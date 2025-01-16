package org.usvm.machine.state

import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsMethod
import org.usvm.UHeapRef
import org.usvm.machine.expr.MultiExpr

/**
 * Represents a result of a method invocation.
 */
interface TSMethodResult {
    /**
     * No call was performed.
     */
    object NoCall : TSMethodResult

    /**
     * A [method] successfully returned a [value].
     */
    class Success(
        val method: EtsMethod,
        val value: MultiExpr,
    ) : TSMethodResult

    /**
     * A method threw an exception with [type] type.
     */
    open class TSException(
        val address: UHeapRef,
        val type: EtsType,
    ) : TSMethodResult {
        override fun toString(): String = "${this::class.simpleName}: Address: $address, type: ${type.typeName}"
    }
}
