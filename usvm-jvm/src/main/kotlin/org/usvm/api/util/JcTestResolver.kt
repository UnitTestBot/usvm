package org.usvm.api.util

import io.ksmt.utils.asExpr
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassType
import org.jacodb.api.JcField
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.float
import org.jacodb.api.ext.ifArrayGetElementType
import org.jacodb.api.ext.int
import org.jacodb.api.ext.long
import org.jacodb.api.ext.short
import org.jacodb.api.ext.toType
import org.jacodb.api.ext.void
import org.usvm.UArrayIndexLValue
import org.usvm.UArrayLengthLValue
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UFieldLValue
import org.usvm.UHeapRef
import org.usvm.ULValue
import org.usvm.URegisterLValue
import org.usvm.USort
import org.usvm.api.JcCoverage
import org.usvm.api.JcParametersState
import org.usvm.api.JcTest
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
import org.usvm.machine.state.WrappedException
import org.usvm.memory.UAddressCounter
import org.usvm.memory.UReadOnlySymbolicMemory
import org.usvm.model.UModelBase
import org.usvm.types.takeFirst

/**
 * A class, responsible for resolving a single [JcTest] for a specific method from a symbolic state.
 *
 * Uses reflection to resolve objects.
 *
 * @param classLoader a class loader to load target classes.
 */
class JcTestResolver(
    private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
) {
    /**
     * Resolves a [JcTest] from a [method] from a [state].
     */
    fun resolve(method: JcTypedMethod, state: JcState): JcTest {
        val model = state.models.first()
        val ctx = state.pathConstraints.ctx as JcContext
        val initialScope = MemoryScope(ctx, model, model, method, classLoader)

        val memory = state.memory
        val afterScope = MemoryScope(ctx, model, memory, method, classLoader)

        val before = with(initialScope) { resolveState() }
        val after = with(afterScope) { resolveState() }

        val result = when (val res = state.methodResult) {
            is JcMethodResult.NoCall -> error("No result found")
            is JcMethodResult.Success -> with(afterScope) { Result.success(resolveExpr(res.value, method.returnType)) }
            is JcMethodResult.Exception -> Result.failure(resolveException(res.exception))
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

    private fun resolveException(exception: Exception): Throwable =
        when (exception) {
            is WrappedException -> Reflection.allocateInstance(classLoader.loadClass(exception.name)) as Throwable
            else -> exception
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
        private val ctx: JcContext,
        private val model: UModelBase<JcField, JcType>,
        private val memory: UReadOnlySymbolicMemory,
        private val method: JcTypedMethod,
        private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
    ) {
        private val resolvedCache = mutableMapOf<UConcreteHeapAddress, Any?>()

        fun resolveState(): JcParametersState {
            // TODO: now we need to explicitly evaluate indices of registers, because we don't have specific ULValues
            val thisInstance = if (!method.isStatic) {
                val ref = URegisterLValue(ctx.addressSort, idx = 0)
                resolveLValue(ref, method.enclosingType)
            } else {
                null
            }

            val parameters = method.parameters.mapIndexed { idx, param ->
                val registerIdx = if (method.isStatic) idx else idx + 1
                val ref = URegisterLValue(ctx.typeToSort(param.type), registerIdx)
                resolveLValue(ref, param.type)
            }

            return JcParametersState(thisInstance, parameters)
        }

        fun resolveLValue(lvalue: ULValue, type: JcType): Any? {
            val expr = memory.read(lvalue)

            return resolveExpr(expr, type)
        }

        fun resolveExpr(expr: UExpr<out USort>, type: JcType): Any? =
            when (type) {
                is JcPrimitiveType -> resolvePrimitive(expr, type)
                is JcRefType -> resolveReference(expr.asExpr(ctx.addressSort), type)
                else -> error("Unexpected type: $type")
            }

        fun resolvePrimitive(expr: UExpr<out USort>, type: JcPrimitiveType): Any {
            val exprInModel = evaluateInModel(expr)
            return when (type) {
                ctx.cp.boolean -> extractBool(exprInModel)
                ctx.cp.short -> extractShort(exprInModel)
                ctx.cp.int -> extractInt(exprInModel)
                ctx.cp.long -> extractLong(exprInModel)
                ctx.cp.float -> extractFloat(exprInModel)
                ctx.cp.double -> extractDouble(exprInModel)
                ctx.cp.byte -> extractByte(exprInModel)
                ctx.cp.char -> extractChar(exprInModel)
                ctx.cp.void -> Unit
                else -> error("Unexpected type: ${type.typeName}")
            } ?: error("Can't extract $expr to ${type.typeName}")
        }

        fun resolveReference(heapRef: UHeapRef, type: JcRefType): Any? {
            val ref = evaluateInModel(heapRef) as UConcreteHeapRef
            if (ref.address == UAddressCounter.NULL_ADDRESS) {
                return null
            }
            return resolvedCache.getOrElse(ref.address) {
                when (type) {
                    is JcArrayType -> resolveArray(heapRef, type)
                    is JcClassType -> resolveReference(ref, heapRef, type)
                    else -> error("Unexpected type: $type")
                }
            }
        }

        private fun resolveArray(heapRef: UHeapRef, type: JcArrayType): Any {
            val lengthRef = UArrayLengthLValue(heapRef, type)
            val resolvedLength = resolveLValue(lengthRef, ctx.cp.int) as Int
            val length = if (resolvedLength in 0..10_000) resolvedLength else 0 // TODO hack

            val cellSort = ctx.typeToSort(type.elementType)

            fun <T> resolveElement(idx: Int): T {
                val elemRef = UArrayIndexLValue(cellSort, heapRef, ctx.mkBv(idx), type)
                @Suppress("UNCHECKED_CAST")
                return resolveLValue(elemRef, type.elementType) as T
            }

            val instance = when (type.elementType) {
                ctx.cp.boolean -> BooleanArray(length, ::resolveElement)
                ctx.cp.short -> ShortArray(length, ::resolveElement)
                ctx.cp.int -> IntArray(length, ::resolveElement)
                ctx.cp.long -> LongArray(length, ::resolveElement)
                ctx.cp.float -> FloatArray(length, ::resolveElement)
                ctx.cp.double -> DoubleArray(length, ::resolveElement)
                ctx.cp.byte -> ByteArray(length, ::resolveElement)
                ctx.cp.char -> CharArray(length, ::resolveElement)
                else -> {
                    // TODO: works incorrectly for inner array
                    val clazz = resolveType(type.elementType as JcRefType)
                    val instance = Reflection.allocateArray(clazz, length)
                    for (i in 0 until length) {
                        instance[i] = resolveElement(i)
                    }
                    instance
                }
            }
            return instance
        }

        private fun resolveReference(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcRefType): Any {
            val clazz = resolveType(ref, type)
            val instance = Reflection.allocateInstance(clazz)
            resolvedCache[ref.address] = instance

            val fields = generateSequence(type.jcClass) { it.superClass }
                .map { it.toType() }
                .flatMap { it.declaredFields }
                .filter { !it.isStatic }

            for (field in fields) {
                val lvalue = UFieldLValue(ctx.typeToSort(field.fieldType), heapRef, field.field)
                val fieldValue = resolveLValue(lvalue, field.fieldType)

                val fieldClazz = resolveType(field.enclosingType)
                val javaField = fieldClazz.getDeclaredField(field.name)
                Reflection.setField(instance, javaField, fieldValue)
            }
            return instance
        }

        private fun resolveType(ref: UConcreteHeapRef, type: JcRefType): Class<*> {
            val typeStream = model.typeStreamOf(ref).filterBySupertype(type)
            val typeFromModel = typeStream.takeFirst() as JcRefType
            return resolveType(typeFromModel)
        }

        private fun resolveType(type: JcRefType): Class<*> =
            type.ifArrayGetElementType?.let { elementType ->
                when (elementType) {
                    ctx.cp.boolean -> BooleanArray::class.java
                    ctx.cp.short -> ShortArray::class.java
                    ctx.cp.int -> IntArray::class.java
                    ctx.cp.long -> LongArray::class.java
                    ctx.cp.float -> FloatArray::class.java
                    ctx.cp.double -> DoubleArray::class.java
                    ctx.cp.byte -> ByteArray::class.java
                    ctx.cp.char -> CharArray::class.java
                    is JcRefType -> {
                        val clazz = classLoader.loadClass(elementType.jcClass.name)
                        Reflection.allocateArray(clazz, length = 0).javaClass
                    }

                    else -> error("Unexpected type: $elementType")
                }
            } ?: classLoader.loadClass(type.jcClass.name)

        /**
         * If we resolve state after, [expr] is read from a state memory, so it requires concretization via [model].
         *
         * @return a concretized expression.
         */
        private fun <T : USort> evaluateInModel(expr: UExpr<T>): UExpr<T> {
            return model.eval(expr)
        }
    }

}