package org.usvm.machine

import org.usvm.UExpr
import org.usvm.USort
import org.usvm.model.TsInstLocation
import org.usvm.model.TsMethod
import org.usvm.model.TsMethodSignature
import org.usvm.model.TsStmt

sealed interface TsMethodCall : TsStmt {
    val arguments: List<UExpr<out USort>>
    val returnSite: TsStmt

    override fun <R> accept(visitor: TsStmt.Visitor<R>): R {
        error("Auxiliary instruction")
    }
}

class TsVirtualMethodCallStmt(
    override val location: TsInstLocation,
    val callee: TsMethodSignature,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: TsStmt,
) : TsMethodCall

class TsConcreteMethodCallStmt(
    override val location: TsInstLocation,
    val callee: TsMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: TsStmt,
) : TsMethodCall
