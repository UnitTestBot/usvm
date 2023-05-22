package org.usvm.util

import io.ksmt.expr.KFp32Value
import io.ksmt.expr.KFp64Value
import io.ksmt.utils.asExpr
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.float
import org.jacodb.api.ext.int
import org.jacodb.api.ext.long
import org.jacodb.api.ext.short
import org.jacodb.api.ext.toType
import org.jacodb.api.ext.void
import org.usvm.JcContext
import org.usvm.JcCoverage
import org.usvm.JcParametersState
import org.usvm.JcTest
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UConcreteInt32
import org.usvm.UConcreteInt64
import org.usvm.UExpr
import org.usvm.UFieldRef
import org.usvm.UHeapRef
import org.usvm.ULValue
import org.usvm.URegisterRef
import org.usvm.USort
import org.usvm.memory.UAddressCounter
import org.usvm.memory.UReadOnlySymbolicMemory
import org.usvm.state.JcMethodResult
import org.usvm.state.JcState
import sun.misc.Unsafe

class JcTestResolver {
    fun resolve(method: JcTypedMethod, state: JcState): JcTest {
        val model = state.models.first()
        val initialMemory = MemoryScope(state.ctx, baseMemory = null, model)

        val memory = state.memory
        val finalMemory = MemoryScope(state.ctx, model, memory)


        val before = with(initialMemory) { resolveState(method) }
        val after = with(finalMemory) { resolveState(method) }

        val result = when (val res = state.methodResult) {
            is JcMethodResult.NoCall -> error("no result found")
            is JcMethodResult.Success -> with(finalMemory) { resolveExpr(res.value, method.returnType) }
            is JcMethodResult.Exception -> res.exception
        }
        val coverage = resolveCoverage(method, state)

        return JcTest(
            before,
            after,
            result,
            coverage
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun resolveCoverage(method: JcTypedMethod, state: JcState): JcCoverage {
        return JcCoverage(emptyMap())
    }


    private class MemoryScope(
        private val ctx: JcContext,
        private val baseMemory: UReadOnlySymbolicMemory<JcType>?,
        private val memory: UReadOnlySymbolicMemory<JcType>,
        private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
    ) {
        private val resolvedCache = mutableMapOf<Int, Any?>()

        fun resolveState(method: JcTypedMethod): JcParametersState {
            val thisInstance = if (!method.isStatic) {
                val ref = URegisterRef(ctx.addressSort, idx = 0)
                resolveLValue(ref, method.enclosingType)
            } else {
                null
            }

            val parameters = method.parameters.mapIndexed { idx, param ->
                val registerIdx = if (method.isStatic) idx else idx + 1
                val ref = URegisterRef(ctx.typeToSort(param.type), registerIdx)
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
                ctx.cp.boolean -> (compose(expr) as UConcreteInt32).intValue != 0
                ctx.cp.short -> (compose(expr) as UConcreteInt32).intValue.toShort()
                ctx.cp.int -> (compose(expr) as UConcreteInt32).intValue
                ctx.cp.long -> (compose(expr) as UConcreteInt64).longValue
                ctx.cp.float -> (compose(expr) as KFp32Value).value
                ctx.cp.double -> (compose(expr) as KFp64Value).value
                ctx.cp.byte -> (compose(expr) as UConcreteInt32).intValue.toByte()
                ctx.cp.char -> (compose(expr) as UConcreteInt32).intValue.toChar()
                ctx.cp.void -> Unit
                else -> error("Unexpected type: $type")
            }

        fun resolveReference(heapRef: UHeapRef, type: JcRefType): Any? {
            val idx = requireNotNull(compose(heapRef) as UConcreteHeapRef).address
            if (idx == UAddressCounter.NULL_ADDRESS) {
                return null
            }
            return resolvedCache.getOrElse(idx) {
                resolveUncachedReference(idx, type, heapRef)
            }
        }

        private fun resolveUncachedReference(idx: UConcreteHeapAddress, type: JcRefType, heapRef: UHeapRef): Any? {
            val jClass = resolveType(idx, type)
            val instance = Unsafe.getUnsafe().allocateInstance(jClass)
            resolvedCache[idx] = instance

            val fields = type.jcClass.toType().declaredFields // TODO: skips inherited fields
            for (field in fields) {
                val ref = UFieldRef(ctx.typeToSort(field.fieldType), heapRef, field)
                val fieldValue = resolveLValue(ref, field.fieldType)

                val javaField = jClass.getDeclaredField(field.name)
                javaField.set(instance, fieldValue)
            }
            return instance
        }

        /**
         * Works incorrectly with interface types.
         */
        @Suppress("UNUSED_PARAMETER")
        private fun resolveType(idx: UConcreteHeapAddress, type: JcRefType): Class<*> {
            // TODO: ask memory for exact type
            val clazz = classLoader.loadClass(type.typeName)
            return clazz
        }

        private fun <T : USort> compose(expr: UExpr<T>): UExpr<T> {
            return baseMemory?.compose(expr) ?: expr
        }
    }

}