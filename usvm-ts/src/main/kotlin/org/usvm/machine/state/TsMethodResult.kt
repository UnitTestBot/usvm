package org.usvm.machine.state

import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.machine.expr.*

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
        val method: TsMethod,
        val value: UExpr<out USort>,
    ) : TsMethodResult

    /**
     * A method threw an exception with [type] type.
     */
    open class TsException(
        val address: UHeapRef,
        val type: TsType,
    ) : TsMethodResult {
        override fun toString(): String = "${this::class.simpleName}: Address: $address, type: ${type.typeName}"
    }
}
