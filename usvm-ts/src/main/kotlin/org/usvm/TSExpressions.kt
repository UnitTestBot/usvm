package org.usvm

import io.ksmt.KAst
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KSortVisitor

val KAst.tctx get() = ctx as TSContext

class TSVoidSort(ctx: TSContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        builder.append("void sort")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T = error("Should not be called")
}

class TSUndefinedSort(ctx: TSContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        builder.append("undefined sort")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T = error("Should not be called")
}

class TSStringSort(ctx: TSContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        builder.append("string sort")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T = TODO("Not yet implemented")
}

class TSUndefinedValue(ctx: TSContext) : UExpr<TSUndefinedSort>(ctx) {
    override val sort: TSUndefinedSort
        get() = tctx.undefinedSort

    override fun accept(transformer: KTransformerBase): TSUndefinedValue = this

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun print(printer: ExpressionPrinter) {
        printer.append("undefined")
    }

}