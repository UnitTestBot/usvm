package org.usvm.state

import org.jacodb.panda.dynamic.ets.base.EtsType
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort

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
        val value: UExpr<out USort>,
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
