package org.usvm.machine.state

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsType
import org.usvm.UExpr
import org.usvm.UHeapRef

/**
 * Represents a result of a method invocation.
 */
sealed interface TsMethodResult {
    /**
     * No call was performed.
     */
    object NoCall : TsMethodResult

    sealed interface Success : TsMethodResult {
        val value: UExpr<*>
        val methodSignature: EtsMethodSignature

        /**
         * A [method] successfully returned a [value].
         */
        class RegularCall(
            override val value: UExpr<*>,
            val method: EtsMethod,
        ) : Success {
            override val methodSignature: EtsMethodSignature get() = method.signature
        }

        class MockedCall(
            override val value: UExpr<*>,
            override val methodSignature: EtsMethodSignature,
        ) : Success
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
