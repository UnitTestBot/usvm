package org.usvm.collection.array

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UCollectionReading
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.withSizeSort

class UAllocatedArrayReading<ArrayType, Sort : USort, USizeSort : USort> internal constructor(
    ctx: UContext<USizeSort>,
    collection: UAllocatedArray<ArrayType, Sort, USizeSort>,
    val index: UExpr<USizeSort>,
) : UCollectionReading<UAllocatedArrayId<ArrayType, Sort, USizeSort>, UExpr<USizeSort>, Sort>(ctx, collection) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.withSizeSort<ArrayType, USizeSort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { index },
        )

    override fun internHashCode(): Int = hash(collection, index)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("[")
        printer.append(index)
        printer.append("]")
    }
}

class UInputArrayReading<ArrayType, Sort : USort, USizeSort : USort> internal constructor(
    ctx: UContext<USizeSort>,
    collection: UInputArray<ArrayType, Sort, USizeSort>,
    val address: UHeapRef,
    val index: UExpr<USizeSort>
) : UCollectionReading<UInputArrayId<ArrayType, Sort, USizeSort>, USymbolicArrayIndex<USizeSort>, Sort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.withSizeSort<ArrayType, USizeSort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { address },
            { index },
        )

    override fun internHashCode(): Int = hash(collection, address, index)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("[")
        printer.append(address)
        printer.append(", ")
        printer.append(index)
        printer.append("]")
    }
}
