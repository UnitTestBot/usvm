package org.usvm.machine.state

import org.usvm.UExpr
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
     * A method successfully returned a [value].
     */
    class Success(
        val value: UExpr<out USort>
    ) : JcMethodResult

    /**
     * A method threw an [exception].
     */
    class Exception(
        val exception: kotlin.Exception
    ) : JcMethodResult

}

// TODO: stub for symbolic exceptions
class WrappedException(
    val name: String
) : kotlin.Exception()
