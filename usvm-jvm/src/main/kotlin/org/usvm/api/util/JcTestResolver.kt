package org.usvm.api.util

import io.ksmt.expr.KFp32Value
import io.ksmt.expr.KFp64Value
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
import org.usvm.UConcreteInt32
import org.usvm.UConcreteInt64
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
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.WrappedException
import org.usvm.memory.UAddressCounter
import org.usvm.memory.UReadOnlySymbolicMemory
import org.usvm.model.UModelBase

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
        val initialMemory = MemoryScope(state.ctx, model = null, model, method, classLoader)

        val memory = state.memory
        val afterMemory = MemoryScope(state.ctx, model, memory, method, classLoader)


        val before = with(initialMemory) { resolveState() }
        val after = with(afterMemory) { resolveState() }

        val result = when (val res = state.methodResult) {
            is JcMethodResult.NoCall -> error("no result found")
            is JcMethodResult.Success -> with(afterMemory) { Result.success(resolveExpr(res.value, method.returnType)) }
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
        private val model: UModelBase<JcField, JcType>?,
        private val memory: UReadOnlySymbolicMemory,
        private val method: JcTypedMethod,
        private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
    ) {
        private val resolvedCache = mutableMapOf<Int, Any?>()

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

        fun resolvePrimitive(expr: UExpr<out USort>, type: JcPrimitiveType): Any =
            when (type) {
                ctx.cp.boolean -> (tryConcretize(expr) as UConcreteInt32).intValue != 0
                ctx.cp.short -> (tryConcretize(expr) as UConcreteInt32).intValue.toShort()
                ctx.cp.int -> (tryConcretize(expr) as UConcreteInt32).intValue
                ctx.cp.long -> (tryConcretize(expr) as UConcreteInt64).longValue
                ctx.cp.float -> (tryConcretize(expr) as KFp32Value).value
                ctx.cp.double -> (tryConcretize(expr) as KFp64Value).value
                ctx.cp.byte -> (tryConcretize(expr) as UConcreteInt32).intValue.toByte()
                ctx.cp.char -> (tryConcretize(expr) as UConcreteInt32).intValue.toChar()
                ctx.cp.void -> Unit
                else -> error("Unexpected type: $type")
            }

        fun resolveReference(heapRef: UHeapRef, type: JcRefType): Any? {
            val idx = requireNotNull(tryConcretize(heapRef) as UConcreteHeapRef).address
            if (idx == UAddressCounter.NULL_ADDRESS) {
                return null
            }
            return resolvedCache.getOrElse(idx) {
                when (type) {
                    is JcArrayType -> resolveArray(idx, type, heapRef)
                    is JcClassType -> resolveReference(idx, type, heapRef)
                    else -> error("Unexpected type: $type")
                }
            }
        }

        private fun resolveArray(idx: UConcreteHeapAddress, type: JcArrayType, heapRef: UHeapRef): Any {
            val lengthRef = UArrayLengthLValue(heapRef, type)
            val length = resolveLValue(lengthRef, ctx.cp.int) as Int

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
                    val jClass = resolveType(idx, type.elementType as JcRefType)
                    val instance = Reflection.allocateArray(jClass, length)
                    for (i in 0 until length) {
                        instance[i] = resolveElement(i)
                    }
                    instance
                }
            }
            return instance
        }

        private fun resolveReference(idx: UConcreteHeapAddress, type: JcRefType, heapRef: UHeapRef): Any {
            val jClass = resolveType(idx, type)
            val instance = Reflection.allocateInstance(jClass)
            resolvedCache[idx] = instance

            val fields = type.jcClass.toType().declaredFields // TODO: now it skips inherited fields
            for (field in fields) {
                val ref = UFieldLValue(ctx.typeToSort(field.fieldType), heapRef, field.field)
                val fieldValue = resolveLValue(ref, field.fieldType)

                val javaField = jClass.getDeclaredField(field.name)
                Reflection.setField(instance, javaField, fieldValue)
            }
            return instance
        }

        /**
         * Works incorrectly with interface types.
         */
        @Suppress("UNUSED_PARAMETER")
        private fun resolveType(idx: UConcreteHeapAddress, type: JcRefType): Class<*> {
            // TODO: ask memory for exact type
            type.ifArrayGetElementType?.let {
                return when (it) {
                    ctx.cp.boolean -> BooleanArray::class.java
                    ctx.cp.short -> ShortArray::class.java
                    ctx.cp.int -> IntArray::class.java
                    ctx.cp.long -> LongArray::class.java
                    ctx.cp.float -> FloatArray::class.java
                    ctx.cp.double -> DoubleArray::class.java
                    ctx.cp.byte -> ByteArray::class.java
                    ctx.cp.char -> CharArray::class.java
                    else -> {
                        val elementType = resolveType(idx, it as JcRefType)
                        Reflection.allocateArray(elementType, length = 0).javaClass
                    }
                }
            }

            return classLoader.loadClass(type.typeName)
        }

        /**
         * If we resolve state after, [expr] is read from a state memory, so it requires concretization via [model].
         *
         * @return a concretized expression, or [expr] if [model] is null.
         */
        private fun <T : USort> tryConcretize(expr: UExpr<T>): UExpr<T> {
            return model?.eval(expr) ?: expr
        }
    }

}