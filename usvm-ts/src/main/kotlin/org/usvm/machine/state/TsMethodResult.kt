package org.usvm.machine.state

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.UExpr

/**
 * Represents a result of a method invocation.
 */
sealed interface TsMethodResult {
    /**
     * No call was performed.
     */
    data object NoCall : TsMethodResult

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

            override fun toString(): String = "Call(method=${method.signature}, value=$value)"
        }

        class MockedCall(
            override val value: UExpr<*>,
            override val methodSignature: EtsMethodSignature,
        ) : Success {
            override fun toString(): String = "MockedCall(method=$methodSignature, value=$value)"
        }
    }

    /**
     * An exception was thrown.
     *
     * @param value the thrown value.
     */
    class TsException(
        val value: UExpr<*>,
        // TODO: additional (optional) info?
        val reason: String? = null,
    ) : TsMethodResult {
        override fun toString(): String =
            if (reason != null) {
                "Exception(value=$value, reason=\"$reason\")"
            } else {
                "Exception(value=$value)"
            }
    }

    /**
     * A machine error occurred.
     *
     * @param message describes the error.
     * @param payload contains additional information about the error.
     */
    class MachineError(
        val message: String,
        val payload: Any? = null,
    ) : TsMethodResult {
        override fun toString(): String = "MachineError($message)"
    }
}
