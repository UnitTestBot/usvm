package org.usvm.state

import org.usvm.UExpr
import org.usvm.USort

sealed class JcMethodResult {
    object NoCall : JcMethodResult()

    class Success(
        val value: UExpr<out USort>?
    ) : JcMethodResult()

    class Exception(
        val exception: kotlin.Exception
    ) : JcMethodResult()
}