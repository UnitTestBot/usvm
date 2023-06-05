package org.usvm.interpreter.operations

import io.ksmt.expr.KExpr
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KSort
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr


fun <RES_SORT: KSort> createBinaryIntOp(
    op: (UContext, UExpr<KIntSort>, UExpr<KIntSort>) -> UExpr<RES_SORT>
): (UContext, UExpr<*>, UExpr<*>) -> UExpr<RES_SORT>? = { ctx, left, right ->
    with (ctx) {
        if (left.sort != intSort || right.sort != intSort)
            null
        else {
            @Suppress("unchecked_cast")
            op(ctx, left as UExpr<KIntSort>, right as UExpr<KIntSort>)
        }
    }
}

fun handlerGTLongKt(x: UContext, y: UExpr<*>, z: UExpr<*>): UBoolExpr? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left gt right } } (x, y, z)
fun handlerLTLongKt(x: UContext, y: UExpr<*>, z: UExpr<*>): UBoolExpr? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left lt right } } (x, y, z)
fun handlerEQLongKt(x: UContext, y: UExpr<*>, z: UExpr<*>): UBoolExpr? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left eq right } } (x, y, z)
fun handlerNELongKt(x: UContext, y: UExpr<*>, z: UExpr<*>): UBoolExpr? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left neq right } } (x, y, z)
fun handlerGELongKt(x: UContext, y: UExpr<*>, z: UExpr<*>): UBoolExpr? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left ge right } } (x, y, z)
fun handlerLELongKt(x: UContext, y: UExpr<*>, z: UExpr<*>): UBoolExpr? =
    createBinaryIntOp { ctx, left, right -> with(ctx) { left le right } } (x, y, z)
fun handlerADDLongKt(x: UContext, y: UExpr<*>, z: UExpr<*>): UExpr<KIntSort>? =
    createBinaryIntOp { ctx, left, right -> ctx.mkArithAdd(left, right) } (x, y, z)
fun handlerSUBLongKt(x: UContext, y: UExpr<*>, z: UExpr<*>): UExpr<KIntSort>? =
    createBinaryIntOp { ctx, left, right -> ctx.mkArithSub(left, right) } (x, y, z)
fun handlerMULLongKt(x: UContext, y: UExpr<*>, z: UExpr<*>): UExpr<KIntSort>? =
    createBinaryIntOp { ctx, left, right -> ctx.mkArithMul(left, right) } (x, y, z)
fun handlerDIVLongKt(x: UContext, y: UExpr<*>, z: UExpr<*>): UExpr<KIntSort>? =
    createBinaryIntOp { ctx, left, right -> ctx.mkArithDiv(left, right) } (x, y, z)
fun handlerREMLongKt(x: UContext, y: UExpr<*>, z: UExpr<*>): UExpr<KIntSort>? =
    createBinaryIntOp { ctx, left, right -> ctx.mkIntMod(left, right) } (x, y, z)