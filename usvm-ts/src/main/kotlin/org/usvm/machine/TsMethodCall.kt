package org.usvm.machine

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
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
    override val location: EtsStmtLocation,
    val callee: EtsMethodSignature,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: EtsStmt,
) : TsMethodCall {
    override fun toString(): String {
        return "virtual ${callee.enclosingClass.name}::${callee.name}"
    }
}

class TsConcreteMethodCallStmt(
    override val location: EtsStmtLocation,
    val callee: EtsMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: EtsStmt,
) : TsMethodCall {
    override fun toString(): String {
        return "concrete ${callee.signature.enclosingClass.name}::${callee.name}"
    }
}
