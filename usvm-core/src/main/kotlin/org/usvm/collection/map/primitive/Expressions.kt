package org.usvm.collection.map.primitive

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
import org.usvm.asTypedTransformer
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.util.Region

class UAllocatedSymbolicMapReading<MapType, KeySort : USort, Sort : USort, Reg: Region<Reg>> internal constructor(
    ctx: UContext,
    collection: UAllocatedSymbolicMap<MapType, KeySort, Sort, Reg>,
    val key: UExpr<KeySort>,
) : UCollectionReading<UAllocatedSymbolicMapId<MapType, KeySort, Sort, Reg>, UExpr<KeySort>, Sort>(ctx, collection) {

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<MapType>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { key },
        )

    override fun internHashCode(): Int = hash(collection, key)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("[")
        printer.append(key)
        printer.append("]")
    }
}

class UInputSymbolicMapReading<MapType, KeySort : USort, Sort : USort, Reg: Region<Reg>> internal constructor(
    ctx: UContext,
    collection: UInputSymbolicMap<MapType, KeySort, Sort, Reg>,
    val address: UHeapRef,
    val key: UExpr<KeySort>
) : UCollectionReading<UInputSymbolicMapId<MapType, KeySort, Sort, Reg>, USymbolicMapKey<KeySort>, Sort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<MapType>().transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { collection },
            { address },
            { key },
        )

    override fun internHashCode(): Int = hash(collection, address, key)

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
        printer.append("[")
        printer.append(address)
        printer.append(", ")
        printer.append(key)
        printer.append("]")
    }
}
