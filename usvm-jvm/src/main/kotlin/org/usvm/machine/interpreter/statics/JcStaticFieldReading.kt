package org.usvm.machine.interpreter.statics

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.jacodb.api.jvm.JcField
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.USymbol
import org.usvm.machine.JcTransformer

class JcStaticFieldReading<Sort : USort> internal constructor(
    ctx: UContext<*>,
    val regionId: JcStaticFieldRegionId<Sort>,
    val field: JcField,
    override val sort: Sort,
) : USymbol<Sort>(ctx) {
    override fun accept(transformer: KTransformerBase): UExpr<Sort> {
        require(transformer is JcTransformer) { "Expected a JcTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(
        other,
        { regionId },
        { field },
        { sort }
    )

    override fun internHashCode(): Int = hash(regionId, field, sort)

    override fun print(printer: ExpressionPrinter) {
        printer.append(regionId.toString())
        printer.append("[")
        printer.append(field.toString())
        printer.append("]")
    }
}
