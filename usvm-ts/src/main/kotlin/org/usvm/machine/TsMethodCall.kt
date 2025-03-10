package org.usvm.machine

import org.jacodb.ets.base.EtsInstLocation
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.UExpr
import org.usvm.USort

sealed interface TsMethodCall : EtsStmt {
    val arguments: List<UExpr<out USort>>
    val returnSite: EtsStmt

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        error("Auxiliary instruction")
    }
}

class TsVirtualMethodCallStmt(
    override val location: EtsInstLocation,
    val callee: EtsMethodSignature,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: EtsStmt,
) : TsMethodCall

class TsConcreteMethodCallStmt(
    override val location: EtsInstLocation,
    val callee: EtsMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: EtsStmt,
) : TsMethodCall
