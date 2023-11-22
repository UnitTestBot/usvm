package org.usvm.api.util

import io.ksmt.utils.asExpr
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.ext.allSuperHierarchySequence
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.float
import org.jacodb.api.ext.ifArrayGetElementType
import org.jacodb.api.ext.int
import org.jacodb.api.ext.isEnum
import org.jacodb.api.ext.long
import org.jacodb.api.ext.short
import org.jacodb.api.ext.toType
import org.jacodb.api.ext.void
import org.usvm.INITIAL_STATIC_ADDRESS
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.JcCoverage
import org.usvm.api.JcParametersState
import org.usvm.api.JcTest
import org.usvm.api.typeStreamOf
import org.usvm.machine.JcContext
import org.usvm.machine.extractBool
import org.usvm.machine.extractByte
import org.usvm.machine.extractChar
import org.usvm.machine.extractDouble
import org.usvm.machine.extractFloat
import org.usvm.machine.extractInt
import org.usvm.machine.extractLong
import org.usvm.machine.extractShort
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.localIdx
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.interpreter.JcFixedInheritorsNumberTypeSelector
import org.usvm.machine.interpreter.JcTypeStreamPrioritization
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.types.first

/**
 * A class, responsible for resolving a single [JcTest] for a specific method from a symbolic state.
 *
 * Uses reflection to resolve objects.
 *
 * @param classLoader a class loader to load target classes.
 */
class JcTestInterpreter(
    private val classLoader: ClassLoader = JcClassLoader,
) : JcTestResolver {
    /**
     * Resolves a [JcTest] from a [method] from a [state].
     */
    override fun resolve(method: JcTypedMethod, state: JcState, stringConstants: Map<String, UConcreteHeapRef>): JcTest {
        val model = state.models.first()
        val memory = state.memory

        val ctx = state.ctx

        val initialScope = MemoryScope(ctx, model, model, stringConstants, method, classLoader)
        val afterScope = MemoryScope(ctx, model, memory, stringConstants, method, classLoader)

        val staticsToResolve = ctx.primitiveTypes
            .flatMap {
                val sort = ctx.typeToSort(it)
                val regionId = JcStaticFieldRegionId(sort)
                val region = memory.getRegion(regionId) as JcStaticFieldsMemoryRegion<*>

                region.mutableStaticFields
            }

        val before = with(initialScope) { resolveState(staticsToResolve) }
        val after = with(afterScope) { resolveState(staticsToResolve) }

        val result = when (val res = state.methodResult) {
            is JcMethodResult.NoCall -> error("No result found")
            is JcMethodResult.Success -> with(afterScope) { Result.success(resolveExpr(res.value, method.returnType)) }
            is JcMethodResult.JcException -> Result.failure(resolveException(res, afterScope))
        }
        val coverage = resolveCoverage(method, state)

        return JcTest(
            method,
            before,
            after,
            result,
            coverage
        )
    }

    private fun resolveException(
        exception: JcMethodResult.JcException,
        afterMemory: MemoryScope
    ): Throwable = with(afterMemory) {
        resolveExpr(exception.address, exception.type) as Throwable
    }

    @Suppress("UNUSED_PARAMETER")
    private fun resolveCoverage(method: JcTypedMethod, state: JcState): JcCoverage {
        // TODO: extract coverage
        return JcCoverage(emptyMap())
    }

    /**
     * An actual class for resolving objects from [UExpr]s.
     *
     * @param model a model to which compose expressions.
     * @param memory a read-only memory to read [ULValue]s from.
     * @param classLoader a class loader to load target classes.
     */
    private class MemoryScope(
        ctx: JcContext,
        model: UModelBase<JcType>,
        memory: UReadOnlyMemory<JcType>,
        stringConstants: Map<String, UConcreteHeapRef>,
        method: JcTypedMethod,
        private val classLoader: ClassLoader = JcClassLoader,
    ) : JcTestStateResolver<Any?>(ctx, model, memory, stringConstants, method) {
        override val decoderApi: DecoderApi<Any?> = JcTestInterpreterDecoderApi(ctx, classLoader)

        fun resolveState(staticsToResolve: List<JcField>): JcParametersState {
            // TODO: now we need to explicitly evaluate indices of registers, because we don't have specific ULValues
            val thisInstance = if (!method.isStatic) {
                val ref = URegisterStackLValue(ctx.addressSort, idx = 0)
                resolveLValue(ref, method.enclosingType)
            } else {
                null
            }

            val parameters = method.parameters.mapIndexed { idx, param ->
                val registerIdx = method.method.localIdx(idx)
                val ref = URegisterStackLValue(ctx.typeToSort(param.type), registerIdx)
                resolveLValue(ref, param.type)
            }

            val statics = staticsToResolve.map { field ->
                    val fieldType = ctx.cp.findTypeOrNull(field.type.typeName)
                        ?: error("No such type ${field.type} found")
                    val sort = ctx.typeToSort(fieldType)

                    StaticFieldValue(field, resolveLValue(JcStaticFieldLValue(field, sort), fieldType))
                }
                .groupBy { it.field.enclosingClass }

            return JcParametersState(thisInstance, parameters, statics)
        }

        override fun allocateClassInstance(type: JcClassType): Any =
            type.allocateInstance(classLoader)

        override fun allocateString(value: Any?): Any = when (value) {
            is CharArray -> String(value)
            is ByteArray -> String(value)
            else -> String()
        }
    }
}
