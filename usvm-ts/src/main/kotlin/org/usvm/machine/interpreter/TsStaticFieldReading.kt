package org.usvm.machine.interpreter

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.jacodb.ets.model.EtsFieldSignature
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.USymbol
import org.usvm.machine.TsContext
import org.usvm.machine.TsTransformer

class TsStaticFieldReading<Sort : USort> internal constructor(
    ctx: TsContext,
    val regionId: TsStaticFieldRegionId<Sort>,
    val field: EtsFieldSignature,
    override val sort: Sort,
) : USymbol<Sort>(ctx) {

    override fun accept(transformer: KTransformerBase): UExpr<Sort> {
        require(transformer is TsTransformer) { "Expected a TsTransformer, but got: $transformer" }
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
