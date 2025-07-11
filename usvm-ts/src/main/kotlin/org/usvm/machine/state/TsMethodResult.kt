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

    sealed class Success(val value: UExpr<*>) : TsMethodResult {
        abstract val methodSignature: EtsMethodSignature

        /**
         * A [method] successfully returned a [value].
         */
        class RegularCall(
            val method: EtsMethod,
            value: UExpr<*>,
        ) : Success(value) {
            override val methodSignature: EtsMethodSignature get() = method.signature
        }

        class MockedCall(
            override val methodSignature: EtsMethodSignature,
            value: UExpr<*>,
        ) : Success(value)
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
