package org.usvm.collection.set.length

import io.ksmt.KContext
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolSort
import org.usvm.UCollectionReading
import org.usvm.UConcreteHeapAddress
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.asTypedTransformer
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.sizeSort
import org.usvm.uctx

class UInputSetLengthReading<SetType, USizeSort : USort> internal constructor(
    ctx: UContext<USizeSort>,
    collection: UInputSetLengthCollection<SetType, USizeSort>,
    val address: UHeapRef,
) : UCollectionReading<UInputSetLengthId<SetType, USizeSort>, UHeapRef, USizeSort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): UExpr<USizeSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<SetType, USizeSort>().transform(this)
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

sealed class USymbolicSetIntersectionSize<USizeSort : USort>(ctx: KContext) : UExpr<USizeSort>(ctx) {
    override val sort: USizeSort
        get() = uctx.sizeSort.uncheckedCast()

    override fun accept(transformer: KTransformerBase): UExpr<USizeSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<Any, USizeSort>().transform(this)
    }
}

class UAllocatedWithAllocatedSymbolicSetIntersectionSize<USizeSort : USort, ElementSort : USort, AllocatedCollectionId> internal constructor(
    ctx: UContext<USizeSort>,
    val firstAddress: UConcreteHeapAddress,
    val secondAddress: UConcreteHeapAddress,
    val firstCollection: USymbolicCollection<AllocatedCollectionId, UExpr<ElementSort>, UBoolSort>,
    val secondCollection: USymbolicCollection<AllocatedCollectionId, UExpr<ElementSort>, UBoolSort>
) : USymbolicSetIntersectionSize<USizeSort>(ctx)
        where AllocatedCollectionId : USymbolicCollectionId<UExpr<ElementSort>, UBoolSort, AllocatedCollectionId> {

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { firstAddress },
            { secondAddress },
            { firstCollection },
            { secondCollection }
        )

    override fun internHashCode(): Int = hash(firstAddress, secondAddress, firstCollection, secondCollection)

    override fun print(printer: ExpressionPrinter) = with(printer) {
        append("(set-intersection-size ")
        append("$firstAddress")
        append(" ")
        append("$secondAddress")
        append(")")
    }
}

class UAllocatedWithInputSymbolicSetIntersectionSize<USizeSort : USort, ElementSort : USort, AllocatedCollectionId, InputCollectionId> internal constructor(
    ctx: UContext<USizeSort>,
    val firstAddress: UConcreteHeapAddress,
    val secondAddress: UHeapRef,
    val firstCollection: USymbolicCollection<AllocatedCollectionId, UExpr<ElementSort>, UBoolSort>,
    val secondCollection: USymbolicCollection<InputCollectionId, USymbolicSetElement<ElementSort>, UBoolSort>
) : USymbolicSetIntersectionSize<USizeSort>(ctx)
        where AllocatedCollectionId : USymbolicCollectionId<UExpr<ElementSort>, UBoolSort, AllocatedCollectionId>,
              InputCollectionId : USymbolicCollectionId<USymbolicSetElement<ElementSort>, UBoolSort, InputCollectionId> {

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { firstAddress },
            { secondAddress },
            { firstCollection },
            { secondCollection }
        )

    override fun internHashCode(): Int = hash(firstAddress, secondAddress, firstCollection, secondCollection)

    override fun print(printer: ExpressionPrinter) = with(printer) {
        append("(set-intersection-size ")
        append("$firstAddress")
        append(" ")
        append(secondAddress)
        append(")")
    }
}

class UInputWithInputSymbolicSetIntersectionSize<USizeSort : USort, ElementSort : USort, InputCollectionId> internal constructor(
    ctx: UContext<USizeSort>,
    val firstAddress: UHeapRef,
    val secondAddress: UHeapRef,
    val firstCollection: USymbolicCollection<InputCollectionId, USymbolicSetElement<ElementSort>, UBoolSort>,
    val secondCollection: USymbolicCollection<InputCollectionId, USymbolicSetElement<ElementSort>, UBoolSort>
) : USymbolicSetIntersectionSize<USizeSort>(ctx)
        where InputCollectionId : USymbolicCollectionId<USymbolicSetElement<ElementSort>, UBoolSort, InputCollectionId> {

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { firstAddress },
            { secondAddress },
            { firstCollection },
            { secondCollection }
        )

    override fun internHashCode(): Int = hash(firstAddress, secondAddress, firstCollection, secondCollection)

    override fun print(printer: ExpressionPrinter) = with(printer) {
        append("(set-intersection-size ")
        append(firstAddress)
        append(" ")
        append(secondAddress)
        append(")")
    }
}
