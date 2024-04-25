package org.usvm.jacodb

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.api.core.cfg.CoreExprVisitor
import org.jacodb.api.core.cfg.InstVisitor
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.jacodb.interpreter.GoStepScope
import org.usvm.memory.URegisterStackLValue

interface GoInstVisitor<T> : InstVisitor<T> {
    fun visitGoJumpInst(inst: GoJumpInst): T
    fun visitGoIfInst(inst: GoIfInst): T
    fun visitGoReturnInst(inst: GoReturnInst): T
    fun visitGoRunDefersInst(inst: GoRunDefersInst): T
    fun visitGoPanicInst(inst: GoPanicInst): T
    fun visitGoGoInst(inst: GoGoInst): T
    fun visitGoDeferInst(inst: GoDeferInst): T
    fun visitGoSendInst(inst: GoSendInst): T
    fun visitGoStoreInst(inst: GoStoreInst): T
    fun visitGoMapUpdateInst(inst: GoMapUpdateInst): T
    fun visitGoDebugRefInst(inst: GoDebugRefInst): T
    fun visitExternalGoInst(inst: GoInst): T
    fun visitGoCallInst(inst: GoCallInst): T
}

interface GoExprVisitor<T> : CoreExprVisitor<T> {
    fun visitGoCallExpr(expr: GoCallExpr): T
    fun visitGoAllocExpr(expr: GoAllocExpr): T
    fun visitGoPhiExpr(expr: GoPhiExpr): T

    fun visitGoAddExpr(expr: GoAddExpr): T
    fun visitGoSubExpr(expr: GoSubExpr): T
    fun visitGoMulExpr(expr: GoMulExpr): T
    fun visitGoDivExpr(expr: GoDivExpr): T
    fun visitGoModExpr(expr: GoModExpr): T
    fun visitGoAndExpr(expr: GoAndExpr): T
    fun visitGoOrExpr(expr: GoOrExpr): T
    fun visitGoXorExpr(expr: GoXorExpr): T
    fun visitGoShlExpr(expr: GoShlExpr): T
    fun visitGoShrExpr(expr: GoShrExpr): T
    fun visitGoAndNotExpr(expr: GoAndNotExpr): T
    fun visitGoEqlExpr(expr: GoEqlExpr): T
    fun visitGoNeqExpr(expr: GoNeqExpr): T
    fun visitGoLssExpr(expr: GoLssExpr): T
    fun visitGoLeqExpr(expr: GoLeqExpr): T
    fun visitGoGtrExpr(expr: GoGtrExpr): T
    fun visitGoGeqExpr(expr: GoGeqExpr): T

    fun visitGoUnNotExpr(expr: GoUnNotExpr): T
    fun visitGoUnSubExpr(expr: GoUnSubExpr): T
    fun visitGoUnArrowExpr(expr: GoUnArrowExpr): T
    fun visitGoUnMulExpr(expr: GoUnMulExpr): T
    fun visitGoUnXorExpr(expr: GoUnXorExpr): T

    fun visitGoChangeTypeExpr(expr: GoChangeTypeExpr): T
    fun visitGoConvertExpr(expr: GoConvertExpr): T
    fun visitGoMultiConvertExpr(expr: GoMultiConvertExpr): T
    fun visitGoChangeInterfaceExpr(expr: GoChangeInterfaceExpr): T
    fun visitGoSliceToArrayPointerExpr(expr: GoSliceToArrayPointerExpr): T
    fun visitGoMakeInterfaceExpr(expr: GoMakeInterfaceExpr): T
    fun visitGoMakeClosureExpr(expr: GoMakeClosureExpr): T
    fun visitGoMakeMapExpr(expr: GoMakeMapExpr): T
    fun visitGoMakeChanExpr(expr: GoMakeChanExpr): T
    fun visitGoMakeSliceExpr(expr: GoMakeSliceExpr): T
    fun visitGoSliceExpr(expr: GoSliceExpr): T
    fun visitGoFieldAddrExpr(expr: GoFieldAddrExpr): T
    fun visitGoFieldExpr(expr: GoFieldExpr): T
    fun visitGoIndexAddrExpr(expr: GoIndexAddrExpr): T
    fun visitGoIndexExpr(expr: GoIndexExpr): T
    fun visitGoLookupExpr(expr: GoLookupExpr): T
    fun visitGoSelectExpr(expr: GoSelectExpr): T
    fun visitGoRangeExpr(expr: GoRangeExpr): T
    fun visitGoNextExpr(expr: GoNextExpr): T
    fun visitGoTypeAssertExpr(expr: GoTypeAssertExpr): T
    fun visitGoExtractExpr(expr: GoExtractExpr): T

    fun visitGoFreeVar(expr: GoFreeVar): T
    fun visitGoParameter(expr: GoParameter): T
    fun visitGoConst(expr: GoConst): T
    fun visitGoGlobal(expr: GoGlobal): T
    fun visitGoBuiltin(expr: GoBuiltin): T
    fun visitGoFunction(expr: GoFunction): T

    fun visitGoBool(value: GoBool): T
    fun visitGoByte(value: GoByte): T
    fun visitGoChar(value: GoChar): T
    fun visitGoShort(value: GoShort): T
    fun visitGoInt(value: GoInt): T
    fun visitGoLong(value: GoLong): T
    fun visitGoFloat(value: GoFloat): T
    fun visitGoDouble(value: GoDouble): T
    fun visitGoNullConstant(value: GoNullConstant): T
    fun visitGoStringConstant(value: GoStringConstant): T

    fun visitExternalGoExpr(expr: GoExpr): T
}

class GoInstVisitorImpl(
    private val ctx: GoContext,
    private val scope: GoStepScope,
    private val exprVisitor: GoExprVisitor<UExpr<out USort>>,
) : GoInstVisitor<GoInst> {
    override fun visitGoJumpInst(inst: GoJumpInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoIfInst(inst: GoIfInst): GoInst = with(ctx) {
        val pos = inst.parent.blocks[inst.trueBranch.index].insts[0]
        val neg = inst.parent.blocks[inst.falseBranch.index].insts[0]

        scope.forkWithBlackList(
            inst.condition.accept(exprVisitor).asExpr(boolSort),
            pos,
            neg,
            blockOnTrueState = { newInst(pos) },
            blockOnFalseState = { newInst(neg) }
        )
        GoNullInst(inst.parent)
    }

    override fun visitGoReturnInst(inst: GoReturnInst): GoInst {
        scope.doWithState {
            returnValue(inst.returnValue[0].accept(exprVisitor).cast())
        }
        return GoNullInst(inst.parent)
    }

    override fun visitGoRunDefersInst(inst: GoRunDefersInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoPanicInst(inst: GoPanicInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoGoInst(inst: GoGoInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoDeferInst(inst: GoDeferInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoSendInst(inst: GoSendInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoStoreInst(inst: GoStoreInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoMapUpdateInst(inst: GoMapUpdateInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoDebugRefInst(inst: GoDebugRefInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitExternalGoInst(inst: GoInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoCallInst(inst: GoCallInst): GoInst {
        TODO("Not yet implemented")
    }
}

class GoExprVisitorImpl(
    private val ctx: GoContext,
    private val scope: GoStepScope,
) : GoExprVisitor<UExpr<out USort>> {
    override fun visitGoCallExpr(expr: GoCallExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoAllocExpr(expr: GoAllocExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoPhiExpr(expr: GoPhiExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoAddExpr(expr: GoAddExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoSubExpr(expr: GoSubExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoMulExpr(expr: GoMulExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoDivExpr(expr: GoDivExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoModExpr(expr: GoModExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoAndExpr(expr: GoAndExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoOrExpr(expr: GoOrExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoXorExpr(expr: GoXorExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoShlExpr(expr: GoShlExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoShrExpr(expr: GoShrExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoAndNotExpr(expr: GoAndNotExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoEqlExpr(expr: GoEqlExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoNeqExpr(expr: GoNeqExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoLssExpr(expr: GoLssExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoLeqExpr(expr: GoLeqExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoGtrExpr(expr: GoGtrExpr): UExpr<out USort> {
        val l = expr.lhv.accept(this).asExpr(ctx.bv32Sort)
        val r = expr.rhv.accept(this).asExpr(ctx.bv32Sort)

        return ctx.mkBvSignedGreaterExpr(l, r)
    }

    override fun visitGoGeqExpr(expr: GoGeqExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoUnNotExpr(expr: GoUnNotExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoUnSubExpr(expr: GoUnSubExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoUnArrowExpr(expr: GoUnArrowExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoUnMulExpr(expr: GoUnMulExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoUnXorExpr(expr: GoUnXorExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoChangeTypeExpr(expr: GoChangeTypeExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoConvertExpr(expr: GoConvertExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoMultiConvertExpr(expr: GoMultiConvertExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoChangeInterfaceExpr(expr: GoChangeInterfaceExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoSliceToArrayPointerExpr(expr: GoSliceToArrayPointerExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoMakeInterfaceExpr(expr: GoMakeInterfaceExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoMakeClosureExpr(expr: GoMakeClosureExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoMakeMapExpr(expr: GoMakeMapExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoMakeChanExpr(expr: GoMakeChanExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoMakeSliceExpr(expr: GoMakeSliceExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoSliceExpr(expr: GoSliceExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoFieldAddrExpr(expr: GoFieldAddrExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoFieldExpr(expr: GoFieldExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoIndexAddrExpr(expr: GoIndexAddrExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoIndexExpr(expr: GoIndexExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoLookupExpr(expr: GoLookupExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoSelectExpr(expr: GoSelectExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoRangeExpr(expr: GoRangeExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoNextExpr(expr: GoNextExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoTypeAssertExpr(expr: GoTypeAssertExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoExtractExpr(expr: GoExtractExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoFreeVar(expr: GoFreeVar): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoParameter(expr: GoParameter): UExpr<out USort> {
        return scope.calcOnState {
            memory.read(URegisterStackLValue(ctx.bv32Sort, expr.index))
        }
    }

    override fun visitGoConst(expr: GoConst): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoGlobal(expr: GoGlobal): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoBuiltin(expr: GoBuiltin): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoFunction(expr: GoFunction): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoBool(value: GoBool): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoByte(value: GoByte): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoChar(value: GoChar): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoShort(value: GoShort): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoInt(value: GoInt): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoLong(value: GoLong): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoFloat(value: GoFloat): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoDouble(value: GoDouble): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoNullConstant(value: GoNullConstant): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoStringConstant(value: GoStringConstant): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitExternalGoExpr(expr: GoExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }
}
