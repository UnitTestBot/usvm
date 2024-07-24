package org.usvm

import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsArrayLiteral
import org.jacodb.ets.base.EtsBinaryOperation
import org.jacodb.ets.base.EtsBooleanConstant
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsDeleteExpr
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsInstanceOfExpr
import org.jacodb.ets.base.EtsLengthExpr
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNewArrayExpr
import org.jacodb.ets.base.EtsNewExpr
import org.jacodb.ets.base.EtsNullConstant
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsObjectLiteral
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsPhiExpr
import org.jacodb.ets.base.EtsRelationOperation
import org.jacodb.ets.base.EtsStaticCallExpr
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsTypeOfExpr
import org.jacodb.ets.base.EtsUnaryOperation
import org.jacodb.ets.base.EtsUndefinedConstant

class TSExprResolver : EtsEntity.Visitor<UExpr<out USort>> {

    override fun visit(value: EtsLocal): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsArrayLiteral): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsBooleanConstant): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsNullConstant): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsNumberConstant): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsObjectLiteral): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsStringConstant): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsUndefinedConstant): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsBinaryOperation): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsCastExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsDeleteExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsInstanceCallExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsInstanceOfExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsLengthExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsNewArrayExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsNewExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsPhiExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsRelationOperation): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsStaticCallExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsTypeOfExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsUnaryOperation): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsArrayAccess): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsInstanceFieldRef): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsParameterRef): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsStaticFieldRef): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsThis): UExpr<out USort> {
        TODO("Not yet implemented")
    }
}
