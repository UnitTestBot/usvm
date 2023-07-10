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
     * A method threw an [exception].
     */
    open class Exception(
        val exception: kotlin.Exception,
    ) : JcMethodResult

    class UnprocessedException(
        exception: kotlin.Exception,
    ) : Exception(exception)
}

// TODO: stub for symbolic exceptions
class WrappedException(
    val address: UHeapRef,
    val type: JcType,
) : kotlin.Exception()
