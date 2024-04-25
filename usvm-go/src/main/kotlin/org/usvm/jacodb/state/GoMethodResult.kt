package org.usvm.jacodb.state

import org.usvm.UExpr
import org.usvm.USort
import org.usvm.jacodb.GoMethod
import org.usvm.jacodb.GoType

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