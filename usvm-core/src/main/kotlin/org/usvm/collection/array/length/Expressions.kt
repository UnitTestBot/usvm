package org.usvm.collection.array.length

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UCollectionReading
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.asTypedTransformer

class UInputArrayLengthReading<ArrayType, USizeSort : USort> internal constructor(
    ctx: UContext<USizeSort>,
    collection: UInputArrayLengths<ArrayType, USizeSort>,
    val address: UHeapRef,
) : UCollectionReading<UInputArrayLengthId<ArrayType, USizeSort>, UHeapRef, USizeSort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): UExpr<USizeSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<ArrayType, USizeSort>().transform(this)
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
