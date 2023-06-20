package org.usvm.machine

import io.ksmt.KAst
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
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
import org.usvm.UAddressSort
import org.usvm.UExpr

class JcVoidValue(ctx: JcContext) : UExpr<UAddressSort>(ctx) {
    override val sort: UAddressSort get() = jctx.voidSort

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> = this

    override fun print(printer: ExpressionPrinter) {
        printer.append("void")
    }
}

val KAst.jctx get() = ctx as JcContext
