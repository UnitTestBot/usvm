package org.usvm.machine.state

import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort

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
//        val symbolicStackTrace: List<JcMethod> // TODO should it contain locations? Probably, yes. Add message?
    ) : JcMethodResult {
        override fun toString(): String = "${this::class.simpleName}: Address: $address, type: ${type.typeName}"
    }

    /**
     * An unprocessed exception thrown by a method.
     *
     * The difference between it and the [JcMethodResult.JcException] is that
     * this exception must be treated as an intermediate result of the method analysis,
     * and it must be handled by an interpreter later, while the [JcException]
     * is a final result that could be produced as a result of the symbolic execution.
     */
    class UnprocessedJcException(
        address: UHeapRef,
        type: JcType,
    ) : JcException(address, type)
}
