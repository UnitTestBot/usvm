package org.usvm.machine

import io.ksmt.KAst
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.sort.KSortVisitor
import io.ksmt.utils.asExpr
import org.jacodb.panda.dynamic.api.PandaExpr
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort

typealias PandaNumberSort = KFp64Sort
typealias PandaBoolSort = KBoolSort

val KAst.pctx get() = ctx as PandaContext

data class PandaUExprWrapper(
    val from: PandaExpr,
    val uExpr: UExpr<out USort>
)

class PandaAnySort(ctx: PandaContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        builder.append("any sort")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T = TODO()
}

class PandaVoidSort(ctx: PandaContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        builder.append("void sort")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T = error("Should not be called")
}

class PandaUndefinedSort(ctx: PandaContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        builder.append("undefined sort")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T = TODO("Not yet implemented")
}

fun UBoolExpr.toNumber(): UExpr<PandaNumberSort> = with(ctx) {
    mkIte(this@toNumber, 1.0.toFp(fp64Sort), 0.0.toFp(fp64Sort)).asExpr(fp64Sort)
}