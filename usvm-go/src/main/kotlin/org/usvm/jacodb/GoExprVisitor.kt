package org.usvm.jacodb

import io.ksmt.utils.asExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.go.api.GoAddExpr
import org.jacodb.go.api.GoAllocExpr
import org.jacodb.go.api.GoAndExpr
import org.jacodb.go.api.GoAndNotExpr
import org.jacodb.go.api.GoBinaryExpr
import org.jacodb.go.api.GoBool
import org.jacodb.go.api.GoBuiltin
import org.jacodb.go.api.GoByte
import org.jacodb.go.api.GoCallExpr
import org.jacodb.go.api.GoChangeInterfaceExpr
import org.jacodb.go.api.GoChangeTypeExpr
import org.jacodb.go.api.GoChar
import org.jacodb.go.api.GoConst
import org.jacodb.go.api.GoConvertExpr
import org.jacodb.go.api.GoDivExpr
import org.jacodb.go.api.GoDouble
import org.jacodb.go.api.GoEqlExpr
import org.jacodb.go.api.GoExpr
import org.jacodb.go.api.GoExprVisitor
import org.jacodb.go.api.GoExtractExpr
import org.jacodb.go.api.GoFieldAddrExpr
import org.jacodb.go.api.GoFieldExpr
import org.jacodb.go.api.GoFloat
import org.jacodb.go.api.GoFreeVar
import org.jacodb.go.api.GoFunction
import org.jacodb.go.api.GoGeqExpr
import org.jacodb.go.api.GoGlobal
import org.jacodb.go.api.GoGtrExpr
import org.jacodb.go.api.GoIndexAddrExpr
import org.jacodb.go.api.GoIndexExpr
import org.jacodb.go.api.GoInt
import org.jacodb.go.api.GoLeqExpr
import org.jacodb.go.api.GoLong
import org.jacodb.go.api.GoLookupExpr
import org.jacodb.go.api.GoLssExpr
import org.jacodb.go.api.GoMakeChanExpr
import org.jacodb.go.api.GoMakeClosureExpr
import org.jacodb.go.api.GoMakeInterfaceExpr
import org.jacodb.go.api.GoMakeMapExpr
import org.jacodb.go.api.GoMakeSliceExpr
import org.jacodb.go.api.GoModExpr
import org.jacodb.go.api.GoMulExpr
import org.jacodb.go.api.GoMultiConvertExpr
import org.jacodb.go.api.GoNeqExpr
import org.jacodb.go.api.GoNextExpr
import org.jacodb.go.api.GoNullConstant
import org.jacodb.go.api.GoOrExpr
import org.jacodb.go.api.GoParameter
import org.jacodb.go.api.GoPhiExpr
import org.jacodb.go.api.GoRangeExpr
import org.jacodb.go.api.GoSelectExpr
import org.jacodb.go.api.GoShlExpr
import org.jacodb.go.api.GoShort
import org.jacodb.go.api.GoShrExpr
import org.jacodb.go.api.GoSliceExpr
import org.jacodb.go.api.GoSliceToArrayPointerExpr
import org.jacodb.go.api.GoStringConstant
import org.jacodb.go.api.GoSubExpr
import org.jacodb.go.api.GoTypeAssertExpr
import org.jacodb.go.api.GoUnArrowExpr
import org.jacodb.go.api.GoUnMulExpr
import org.jacodb.go.api.GoUnNotExpr
import org.jacodb.go.api.GoUnSubExpr
import org.jacodb.go.api.GoUnXorExpr
import org.jacodb.go.api.GoUnaryExpr
import org.jacodb.go.api.GoXorExpr
import org.usvm.UAddressPointer
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.ULValuePointer
import org.usvm.USort
import org.usvm.api.UnknownBinaryOperationException
import org.usvm.api.UnknownUnaryOperationException
import org.usvm.jacodb.interpreter.GoStepScope
import org.usvm.jacodb.operator.GoBinaryOperator
import org.usvm.jacodb.operator.GoUnaryOperator
import org.usvm.jacodb.operator.mkNarrow
import org.usvm.memory.GoPointerLValue
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue

class GoExprVisitor(
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

    override fun visitGoAddExpr(expr: GoAddExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoSubExpr(expr: GoSubExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoMulExpr(expr: GoMulExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoDivExpr(expr: GoDivExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoModExpr(expr: GoModExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoAndExpr(expr: GoAndExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoOrExpr(expr: GoOrExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoXorExpr(expr: GoXorExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoShlExpr(expr: GoShlExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoShrExpr(expr: GoShrExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoAndNotExpr(expr: GoAndNotExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoEqlExpr(expr: GoEqlExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoNeqExpr(expr: GoNeqExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoLssExpr(expr: GoLssExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoLeqExpr(expr: GoLeqExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoGtrExpr(expr: GoGtrExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoGeqExpr(expr: GoGeqExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoUnNotExpr(expr: GoUnNotExpr): UExpr<out USort> = visitGoUnaryExpr(expr)

    override fun visitGoUnSubExpr(expr: GoUnSubExpr): UExpr<out USort> = visitGoUnaryExpr(expr)

    override fun visitGoUnArrowExpr(expr: GoUnArrowExpr): UExpr<out USort> = visitGoUnaryExpr(expr)

    override fun visitGoUnMulExpr(expr: GoUnMulExpr): UExpr<out USort> = visitGoUnaryExpr(expr)

    override fun visitGoUnXorExpr(expr: GoUnXorExpr): UExpr<out USort> = visitGoUnaryExpr(expr)

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

    override fun visitGoBool(value: GoBool): UExpr<out USort> = with(ctx) {
        return mkBool(value.value)
    }

    override fun visitGoByte(value: GoByte): UExpr<out USort> = with(ctx) {
        return mkBv(value.value)
    }

    override fun visitGoChar(value: GoChar): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoShort(value: GoShort): UExpr<out USort> = with(ctx) {
        return mkBv(value.value)
    }

    override fun visitGoInt(value: GoInt): UExpr<out USort> = with(ctx) {
        return mkBv(value.value)
    }

    override fun visitGoLong(value: GoLong): UExpr<out USort> = with(ctx) {
        return mkBv(value.value)
    }

    override fun visitGoFloat(value: GoFloat): UExpr<out USort> = with(ctx) {
        return mkFp(value.value, fp32Sort)
    }

    override fun visitGoDouble(value: GoDouble): UExpr<out USort> = with(ctx) {
        return mkFp(value.value, fp64Sort)
    }

    override fun visitGoNullConstant(value: GoNullConstant): UExpr<out USort> = ctx.nullRef

    override fun visitGoStringConstant(value: GoStringConstant): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitCommonCallExpr(expr: CommonExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitCommonInstanceCallExpr(expr: CommonExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitExternalCommonExpr(expr: CommonExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitExternalCommonValue(value: CommonValue): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitExternalGoExpr(expr: GoExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    private fun visitGoBinaryExpr(expr: GoBinaryExpr): UExpr<out USort> {
        val signed = true // TODO(buraindo): make this calculated
        return when (expr) {
            is GoAddExpr -> GoBinaryOperator.Add
            is GoSubExpr -> GoBinaryOperator.Sub
            is GoMulExpr -> GoBinaryOperator.Mul
            is GoDivExpr -> GoBinaryOperator.Div(signed)
            is GoModExpr -> GoBinaryOperator.Mod(signed)
            is GoAndExpr -> GoBinaryOperator.And
            is GoOrExpr -> GoBinaryOperator.Or
            is GoXorExpr -> GoBinaryOperator.Xor
            is GoShlExpr -> GoBinaryOperator.Shl
            is GoShrExpr -> GoBinaryOperator.Shr(signed)
            is GoAndNotExpr -> GoBinaryOperator.AndNot
            is GoEqlExpr -> GoBinaryOperator.Eql
            is GoLssExpr -> GoBinaryOperator.Lss(signed)
            is GoGtrExpr -> GoBinaryOperator.Gtr(signed)
            is GoNeqExpr -> GoBinaryOperator.Neq
            is GoLeqExpr -> GoBinaryOperator.Leq(signed)
            is GoGeqExpr -> GoBinaryOperator.Geq(signed)
            else -> throw UnknownBinaryOperationException()
        }(expr.lhv.accept(this), normalize(expr.rhv.accept(this), expr, signed))
    }

    private fun visitGoUnaryExpr(expr: GoUnaryExpr): UExpr<out USort> {
        val x = expr.value.accept(this)
        return when (expr) {
            is GoUnArrowExpr -> TODO()
            is GoUnXorExpr, is GoUnNotExpr, is GoUnSubExpr -> GoUnaryOperator.Neg(x)
            is GoUnMulExpr -> deref(x, ctx.bv32Sort) // TODO(buraindo) real sort
            else -> throw UnknownUnaryOperationException()
        }
    }

    private fun normalize(expr: UExpr<out USort>, goExpr: GoBinaryExpr, signed: Boolean): UExpr<out USort> = with(ctx) {
        when (goExpr) {
            is GoShrExpr -> bv(expr).mkNarrow(Long.SIZE_BITS, signed).asExpr(bv64Sort)
            else -> expr
        }
    }

    private fun bv(expr: UExpr<out USort>): UExpr<UBvSort> {
        return expr.asExpr(expr.sort as UBvSort)
    }

    private fun <Sort : USort> deref(expr: UExpr<out USort>, sort: Sort): UExpr<Sort> = with(ctx) {
        val pointer = expr.asExpr(pointerSort) as UAddressPointer

        return scope.calcOnState {
            memory.read(pointerLValue(pointer, sort))
        }
    }

    private fun <Sort : USort> pointerLValue(pointer: UAddressPointer, sort: Sort): ULValue<*, Sort> = with(ctx) {
        return scope.calcOnState {
            val lvalue = GoPointerLValue(mkConcreteHeapRef(pointer.address), sort)
            val ref = memory.read(lvalue)
            if (ref is ULValuePointer) {
                ref.lvalue.withSort(sort)
            } else {
                lvalue
            }
        }
    }
}