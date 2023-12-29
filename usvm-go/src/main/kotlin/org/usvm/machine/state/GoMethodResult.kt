package org.usvm.machine.state

import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.GoMethod

sealed interface GoMethodResult {
    object NoCall : GoMethodResult

    class Success(
        val method: GoMethod,
        val value: UExpr<USort>,
    ) : GoMethodResult

    object Panic : GoMethodResult
}