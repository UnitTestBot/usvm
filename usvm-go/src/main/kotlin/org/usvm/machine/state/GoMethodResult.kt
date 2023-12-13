package org.usvm.machine.state

import org.usvm.UExpr
import org.usvm.USort
import org.usvm.domain.GoMethod

sealed interface GoMethodResult {
    object NoCall : GoMethodResult

    class Success(
        val method: GoMethod,
        val value: UExpr<out USort>,
    ) : GoMethodResult

    object Panic : GoMethodResult
}