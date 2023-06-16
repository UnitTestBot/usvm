package org.usvm.machine.state

import org.usvm.UExpr
import org.usvm.USort

sealed class JcMethodResult {
    object NoCall : JcMethodResult()

    class Success(
        val value: UExpr<out USort>
    ) : JcMethodResult()

    // TODO: the last place where we distinguish implicitly thrown and explicitly thrown exceptions
    class Exception(
        val exception: kotlin.Exception
    ) : JcMethodResult()

}

// TODO: stub for exceptions
class WrappedException(
    val name: String
) : kotlin.Exception()
