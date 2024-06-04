package org.usvm.jacodb.state

import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoType
import org.usvm.UExpr
import org.usvm.USort

sealed interface GoMethodResult {
    data object NoCall : GoMethodResult

    class Success(
        val method: GoMethod,
        val value: UExpr<USort>,
    ) : GoMethodResult

    class Panic(
        val value: UExpr<USort>,
        val type: GoType
    ) : GoMethodResult
}