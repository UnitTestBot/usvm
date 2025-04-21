package org.usvm.machine

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.usvm.UExpr

sealed interface TsMethodCall : EtsStmt {
    val instance: UExpr<*>?
    val args: List<UExpr<*>>
    val returnSite: EtsStmt

    override val location: EtsStmtLocation
        get() = returnSite.location

    override fun <R> accept(visitor: EtsStmt.Visitor<R>): R {
        error("Auxiliary instruction")
    }
}

class TsVirtualMethodCallStmt(
    val callee: EtsMethodSignature,
    override val instance: UExpr<*>?,
    override val args: List<UExpr<*>>,
    override val returnSite: EtsStmt,
) : TsMethodCall {
    override fun toString(): String {
        return "virtual ${callee.enclosingClass.name}::${callee.name}"
    }

    fun toConcrete(callee: EtsMethod): TsConcreteMethodCallStmt {
        return TsConcreteMethodCallStmt(callee, instance, args, returnSite)
    }
}

// Note: `args` are resolved, but not yet truncated (if more than necessary),
//  and not wrapped in array (if calling a vararg method)
class TsConcreteMethodCallStmt(
    val callee: EtsMethod,
    override val instance: UExpr<*>?,
    override val args: List<UExpr<*>>,
    override val returnSite: EtsStmt,
) : TsMethodCall {
    override fun toString(): String {
        return "concrete ${callee.signature.enclosingClass.name}::${callee.name}"
    }
}
