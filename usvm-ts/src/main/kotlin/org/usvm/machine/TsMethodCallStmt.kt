package org.usvm.machine

import org.jacodb.ets.base.EtsInstLocation
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.usvm.UExpr
import org.usvm.USort

interface TsTransparentStatement : EtsStmt {
    val original: EtsStmt
}

sealed interface TsBaseMethodCallStmt : TsTransparentStatement {
    override val method: EtsMethod

    override fun <T> accept(visitor: EtsStmt.Visitor<T>): T {
        error("Auxiliary instruction")
    }
}

sealed interface TsMethodCall {
    val location: EtsInstLocation
    val method: EtsMethod
    val arguments: List<UExpr<out USort>>
    val returnSite: EtsStmt
}

data class TsConcreteMethodCallStmt(
    override val location: EtsInstLocation,
    override val method: EtsMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: EtsStmt,
) : TsBaseMethodCallStmt, TsMethodCall {
    override val original: EtsStmt
        get() = returnSite
}
