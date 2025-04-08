package org.usvm.machine.state

import org.jacodb.ets.model.EtsMethod
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

    /**
     * A [method] successfully returned a [value].
     */
    class Success(
        val method: EtsMethod,
        val value: UExpr<*>,
    ) : TsMethodResult

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
