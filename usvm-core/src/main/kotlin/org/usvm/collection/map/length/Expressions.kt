package org.usvm.collection.map.length

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UCollectionReading
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.UTransformer
import org.usvm.asTypedTransformer

class UInputSymbolicMapLengthReading<MapType> internal constructor(
    ctx: UContext,
    collection: UInputSymbolicMapLengthCollection<MapType>,
    val address: UHeapRef,
) : UCollectionReading<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): USizeExpr {
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<MapType>().transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { collection }, { address })

    override fun internHashCode(): Int = hash(collection, address)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("[")
        printer.append(address)
        printer.append("]")
    }
}
