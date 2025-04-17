package org.usvm.machine.state

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort

/**
 * Represents a result of a method invocation.
 */
sealed interface TsMethodResult {
    /**
     * No call was performed.
     */
    object NoCall : TsMethodResult

    sealed class Success(val value: UExpr<out USort>) : TsMethodResult {
        abstract fun methodSignature(): EtsMethodSignature

        /**
         * A [method] successfully returned a [value].
         */
        class RegularCall(
            val method: EtsMethod,
            value: UExpr<out USort>,
        ) : Success(value) {
            override fun methodSignature(): EtsMethodSignature = method.signature
        }

        class MockedCall(
            val methodSignature: EtsMethodSignature,
            value: UExpr<out USort>,
        ) : Success(value) {
            override fun methodSignature(): EtsMethodSignature = methodSignature
        }
    }

    /**
     * A method threw an exception with [type] type.
     */
    open class TsException(
        val address: UHeapRef,
        val type: EtsType,
    ) : TsMethodResult {
        override fun toString(): String = "${this::class.simpleName}: Address: $address, type: ${type.typeName}"
    }
}
