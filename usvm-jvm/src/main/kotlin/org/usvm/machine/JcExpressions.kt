package org.usvm.machine

import io.ksmt.KAst
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBv16Sort
import io.ksmt.sort.KBv32Sort
import io.ksmt.sort.KBv64Sort
import io.ksmt.sort.KBv8Sort
import io.ksmt.sort.KFp32Sort
import io.ksmt.sort.KFp64Sort
import io.ksmt.sort.KSortVisitor
import org.usvm.UExpr
import org.usvm.USort

typealias JcLongSort = KBv64Sort
typealias JcIntSort = KBv32Sort
typealias JcShortSort = KBv16Sort
typealias JcCharSort = KBv16Sort
typealias JcByteSort = KBv8Sort
typealias JcBooleanSort = KBoolSort

typealias JcFloatSort = KFp32Sort
typealias JcDoubleSort = KFp64Sort

class JcVoidSort(ctx: JcContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        TODO("Not yet implemented")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T {
        TODO("Not yet implemented")
    }
}

class JcVoidValue(ctx: JcContext) : UExpr<JcVoidSort>(ctx) {
    override val sort: JcVoidSort get() = jctx.voidSort

    override fun internEquals(other: Any): Boolean {
        TODO("Not yet implemented")
    }

    override fun internHashCode(): Int {
        TODO("Not yet implemented")
    }

    override fun accept(transformer: KTransformerBase): KExpr<JcVoidSort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }
}

val KAst.jctx get() = ctx as JcContext
