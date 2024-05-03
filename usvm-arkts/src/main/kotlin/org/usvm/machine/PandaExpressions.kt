package org.usvm.machine

import io.ksmt.KAst
import io.ksmt.decl.KConstDecl
import io.ksmt.decl.KDecl
import io.ksmt.expr.KApp
import io.ksmt.expr.KExpr
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.sort.KSortVisitor
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UIntepretedValue
import org.usvm.USort

typealias PandaNumberSort = KFp64Sort
typealias PandaBoolSort = KBoolSort

val KAst.pctx get() = ctx as PandaContext

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

class PandaStringSort(ctx: PandaContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        builder.append("string sort")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T = TODO("Not yet implemented")
}

class PandaConcreteStringDecl(
    ctx: PandaContext,
    val value: String
) : KConstDecl<PandaStringSort>(ctx, value, ctx.stringSort) {
    override fun apply(args: List<KExpr<*>>): KApp<PandaStringSort, *> = pctx.mkConcreteString(value)
}

class PandaConcreteString internal constructor(
    ctx: UContext<*>,
    val value: String,
) : UIntepretedValue<PandaStringSort>(ctx) {
    override val decl: KDecl<PandaStringSort>
        get() = pctx.mkConcreteStringDecl(value)

    override val sort: PandaStringSort
        get() = pctx.stringSort

    override fun accept(transformer: KTransformerBase): KExpr<PandaStringSort> {
        TODO()
    }

    override fun internEquals(other: Any): Boolean {
        return super.internEquals(other)
    }

    override fun internHashCode(): Int {
        return super.internHashCode()
    }
}


fun UBoolExpr.toNumber(): UExpr<PandaNumberSort> = with(ctx) {
    mkIte(this@toNumber, 1.0.toFp(fp64Sort), 0.0.toFp(fp64Sort)).asExpr(fp64Sort)
}

