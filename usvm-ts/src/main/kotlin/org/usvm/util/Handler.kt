package org.usvm.util

import org.usvm.model.TsAddExpr
import org.usvm.model.TsAndExpr
import org.usvm.model.TsArrayAccess
import org.usvm.model.TsAssignStmt
import org.usvm.model.TsAwaitExpr
import org.usvm.model.TsBitAndExpr
import org.usvm.model.TsBitNotExpr
import org.usvm.model.TsBitOrExpr
import org.usvm.model.TsBitXorExpr
import org.usvm.model.TsBooleanConstant
import org.usvm.model.TsCallStmt
import org.usvm.model.TsCastExpr
import org.usvm.model.TsDeleteExpr
import org.usvm.model.TsDivExpr
import org.usvm.model.TsEntity
import org.usvm.model.TsEqExpr
import org.usvm.model.TsExpExpr
import org.usvm.model.TsGtEqExpr
import org.usvm.model.TsGtExpr
import org.usvm.model.TsIfStmt
import org.usvm.model.TsInExpr
import org.usvm.model.TsInstanceCallExpr
import org.usvm.model.TsInstanceFieldRef
import org.usvm.model.TsInstanceOfExpr
import org.usvm.model.TsLeftShiftExpr
import org.usvm.model.TsLocal
import org.usvm.model.TsLtEqExpr
import org.usvm.model.TsLtExpr
import org.usvm.model.TsMulExpr
import org.usvm.model.TsNegExpr
import org.usvm.model.TsNewArrayExpr
import org.usvm.model.TsNewExpr
import org.usvm.model.TsNopStmt
import org.usvm.model.TsNotEqExpr
import org.usvm.model.TsNotExpr
import org.usvm.model.TsNullConstant
import org.usvm.model.TsNumberConstant
import org.usvm.model.TsOrExpr
import org.usvm.model.TsParameterRef
import org.usvm.model.TsPostDecExpr
import org.usvm.model.TsPostIncExpr
import org.usvm.model.TsPreDecExpr
import org.usvm.model.TsPreIncExpr
import org.usvm.model.TsPtrCallExpr
import org.usvm.model.TsRemExpr
import org.usvm.model.TsReturnStmt
import org.usvm.model.TsRightShiftExpr
import org.usvm.model.TsStaticCallExpr
import org.usvm.model.TsStaticFieldRef
import org.usvm.model.TsStmt
import org.usvm.model.TsStrictEqExpr
import org.usvm.model.TsStrictNotEqExpr
import org.usvm.model.TsStringConstant
import org.usvm.model.TsSubExpr
import org.usvm.model.TsThis
import org.usvm.model.TsTypeOfExpr
import org.usvm.model.TsUnaryPlusExpr
import org.usvm.model.TsUndefinedConstant
import org.usvm.model.TsUnsignedRightShiftExpr
import org.usvm.model.TsVoidExpr
import org.usvm.model.TsYieldExpr

abstract class AbstractHandler : TsEntity.Visitor.Default<Unit>, TsStmt.Visitor.Default<Unit> {

    abstract fun handle(value: TsEntity)
    abstract fun handle(stmt: TsStmt)

    final override fun defaultVisit(value: TsEntity) {
        handle(value)
    }

    final override fun defaultVisit(stmt: TsStmt) {
        handle(stmt)
    }

    final override fun visit(stmt: TsNopStmt) {
        handle(stmt)
    }

    final override fun visit(stmt: TsAssignStmt) {
        handle(stmt)
        stmt.lhv.accept(this)
        stmt.rhv.accept(this)
    }

    final override fun visit(stmt: TsCallStmt) {
        handle(stmt)
        stmt.expr.accept(this)
    }

    final override fun visit(stmt: TsReturnStmt) {
        handle(stmt)
        stmt.returnValue?.accept(this)
    }

    final override fun visit(stmt: TsIfStmt) {
        handle(stmt)
        stmt.condition.accept(this)
    }

    final override fun visit(value: TsLocal) {
        handle(value)
    }

    final override fun visit(value: TsStringConstant) {
        handle(value)
    }

    final override fun visit(value: TsBooleanConstant) {
        handle(value)
    }

    final override fun visit(value: TsNumberConstant) {
        handle(value)
    }

    final override fun visit(value: TsNullConstant) {
        handle(value)
    }

    final override fun visit(value: TsUndefinedConstant) {
        handle(value)
    }

    final override fun visit(value: TsThis) {
        handle(value)
    }

    final override fun visit(value: TsParameterRef) {
        handle(value)
    }

    final override fun visit(value: TsArrayAccess) {
        handle(value)
        value.array.accept(this)
        value.index.accept(this)
    }

    final override fun visit(value: TsInstanceFieldRef) {
        handle(value)
        value.instance.accept(this)
    }

    final override fun visit(value: TsStaticFieldRef) {
        handle(value)
    }

    final override fun visit(expr: TsNewExpr) {
        handle(expr)
    }

    final override fun visit(expr: TsNewArrayExpr) {
        handle(expr)
        expr.size.accept(this)
    }

    final override fun visit(expr: TsCastExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsInstanceOfExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsDeleteExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsAwaitExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsYieldExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsTypeOfExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsVoidExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsNotExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsBitNotExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsNegExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsUnaryPlusExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsPreIncExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsPreDecExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsPostIncExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsPostDecExpr) {
        handle(expr)
        expr.arg.accept(this)
    }

    final override fun visit(expr: TsEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsNotEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsStrictEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsStrictNotEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsLtExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsLtEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsGtExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsGtEqExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsInExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsAddExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsSubExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsMulExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsDivExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsRemExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsExpExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsBitAndExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsBitOrExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsBitXorExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsLeftShiftExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsRightShiftExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsUnsignedRightShiftExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsAndExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsOrExpr) {
        handle(expr)
        expr.left.accept(this)
        expr.right.accept(this)
    }

    final override fun visit(expr: TsInstanceCallExpr) {
        handle(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    final override fun visit(expr: TsStaticCallExpr) {
        handle(expr)
        expr.args.forEach { it.accept(this) }
    }

    final override fun visit(expr: TsPtrCallExpr) {
        handle(expr)
        expr.ptr.accept(this)
        expr.args.forEach { it.accept(this) }
    }
}
