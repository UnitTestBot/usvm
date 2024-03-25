package org.usvm.machine.state

import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort

/**
 * Represents a result of a method invocation.
 */
sealed interface PandaMethodResult {
    /**
     * No call was performed.
     */
    object NoCall : PandaMethodResult

    /**
     * A [method] successfully returned a [value].
     */
    class Success(
        val method: PandaMethod,
        val value: UExpr<out USort>,
    ) : PandaMethodResult

    /**
     * A method threw an exception with [type] type.
     */
    open class PandaException(
        val address: UHeapRef,
        val type: PandaType,
    ) : PandaMethodResult {
        override fun toString(): String = "${this::class.simpleName}: Address: $address, type: ${type.typeName}"
    }
}
