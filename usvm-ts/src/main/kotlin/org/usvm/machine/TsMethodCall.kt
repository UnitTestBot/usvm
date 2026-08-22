package org.usvm.machine

import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.usvm.UExpr

sealed interface TsMethodCall : EtsStmt {
    val call: EtsCallExpr
    val resolvedReceiver: UExpr<*>?
    val instance: UExpr<*>
    val args: List<UExpr<*>>
    val returnSite: EtsStmt

    override val location: EtsStmtLocation
        get() = returnSite.location

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        error("Auxiliary instruction")
    }
}

class TsVirtualMethodCallStmt(
    override val call: EtsInstanceCallExpr,
    override val instance: UExpr<*>,
    override val args: List<UExpr<*>>,
    override val returnSite: EtsStmt,
) : TsMethodCall {
    override val resolvedReceiver: UExpr<*>
        get() = instance

    override fun toString(): String {
        return "virtual ${call.callee.enclosingClass.name}::${call.callee.name}"
    }

    fun toConcrete(callee: EtsMethod): TsConcreteMethodCallStmt {
        return TsConcreteMethodCallStmt(
            callee = callee,
            call = call,
            resolvedReceiver = resolvedReceiver,
            instance = instance,
            args = args,
            returnSite = returnSite,
        )
    }
}

// Note: `args` are resolved, but not yet truncated (if more than necessary),
//  and not wrapped in array (if calling a vararg method)
class TsConcreteMethodCallStmt(
    val callee: EtsMethod,
    override val call: EtsCallExpr,
    override val resolvedReceiver: UExpr<*>?,
    override val instance: UExpr<*>,
    override val args: List<UExpr<*>>,
    override val returnSite: EtsStmt,
) : TsMethodCall {
    override fun toString(): String {
        return "concrete ${callee.signature.enclosingClass.name}::${callee.name}"
    }
}
