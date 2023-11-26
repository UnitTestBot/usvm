package org.usvm.state

import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoType
import org.usvm.UExpr
import org.usvm.USort

sealed interface GoMethodResult {
    data object NoCall : GoMethodResult

    class Success(
        val value: UExpr<out USort>,
        val method: GoMethod,
        val type: GoType,
    ) : GoMethodResult

    class Panic(
        val value: UExpr<out USort>,
        val type: GoType
    ) : GoMethodResult
}