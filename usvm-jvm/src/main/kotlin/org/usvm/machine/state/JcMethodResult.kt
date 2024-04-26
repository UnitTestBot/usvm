package org.usvm.machine.state

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UStackTraceFrame

/**
 * Represents a result of a method invocation.
 */
sealed interface JcMethodResult {
    /**
     * No call was performed.
     */
    object NoCall : JcMethodResult

    /**
     * A [method] successfully returned a [value].
     */
    class Success(
        val method: JcMethod,
        val value: UExpr<out USort>,
    ) : JcMethodResult

    /**
     * A method threw an exception with [type] type.
     */
    open class JcException(
        val address: UHeapRef,
        val type: JcType,
        val symbolicStackTrace: List<UStackTraceFrame<JcMethod, JcInst>>
    ) : JcMethodResult {
        override fun toString(): String = "${this::class.simpleName}: Address: $address, type: ${type.typeName}"
    }
}
