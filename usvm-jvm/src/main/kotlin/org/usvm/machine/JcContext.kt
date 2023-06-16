package org.usvm.machine

import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KSortVisitor
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.float
import org.jacodb.api.ext.int
import org.jacodb.api.ext.long
import org.jacodb.api.ext.short
import org.jacodb.api.ext.void
import org.usvm.UAddressSort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort

class JcContext(
    val cp: JcClasspath,
    components: JcComponents,
) : UContext(components) {

    val voidValue by lazy { JcVoidValue(this) }
    fun mkVoidValue(): JcVoidValue = voidValue

    fun typeToSort(type: JcType) = when (type) {
        is JcRefType -> addressSort
        cp.void -> voidValue.sort // TODO
        cp.float -> fp32Sort
        cp.double -> fp64Sort
        cp.long -> bv64Sort
        cp.int, cp.char, cp.byte, cp.short, cp.boolean -> bv32Sort
        else -> error("Unknown type: $type")
    }
}


class JcVoidSort(ctx: JcContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        TODO("Not yet implemented")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T {
        TODO("Not yet implemented")
    }
}

class JcVoidValue(ctx: JcContext) : UExpr<JcVoidSort>(ctx) {
    override fun internEquals(other: Any): Boolean {
        TODO("Not yet implemented")
    }

    override fun internHashCode(): Int {
        TODO("Not yet implemented")
    }

    override val sort
        get() = JcVoidSort(ctx as JcContext)

    override fun accept(transformer: KTransformerBase): KExpr<JcVoidSort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }
}