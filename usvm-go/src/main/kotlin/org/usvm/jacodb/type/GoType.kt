package org.usvm.jacodb.type

import io.ksmt.KAst
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KSortVisitor
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.jacodb.GoContext

class GoVoidSort(ctx: GoContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        builder.append("void sort")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T = error("should not be called")
}

class GoVoidValue(ctx: GoContext) : UExpr<GoVoidSort>(ctx) {
    override val sort: GoVoidSort get() = goCtx.voidSort

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun accept(transformer: KTransformerBase): GoVoidValue = this

    override fun print(printer: ExpressionPrinter) {
        printer.append("void")
    }
}

val KAst.goCtx get() = ctx as GoContext