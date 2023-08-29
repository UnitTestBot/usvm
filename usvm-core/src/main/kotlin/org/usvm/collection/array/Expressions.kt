package org.usvm.collection.array

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UCollectionReading
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.asTypedTransformer

class UAllocatedArrayReading<ArrayType, Sort : USort> internal constructor(
    ctx: UContext,
    collection: UAllocatedArray<ArrayType, Sort>,
    val index: USizeExpr,
) : UCollectionReading<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>(ctx, collection) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<ArrayType>().transform(this)
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

class UInputArrayReading<ArrayType, Sort : USort> internal constructor(
    ctx: UContext,
    collection: UInputArray<ArrayType, Sort>,
    val address: UHeapRef,
    val index: USizeExpr
) : UCollectionReading<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<ArrayType>().transform(this)
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
