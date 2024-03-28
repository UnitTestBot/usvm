package org.usvm.machine

import io.ksmt.KAst
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.sort.KSortVisitor
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
