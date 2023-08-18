package org.usvm

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.*
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.*
import org.usvm.memory.UAllocatedArray
import org.usvm.memory.collections.UAllocatedArrayId
import org.usvm.memory.collections.UAllocatedSymbolicMapId
import org.usvm.memory.UAllocatedSymbolicMap
import org.usvm.memory.UInputArray
import org.usvm.memory.collections.UInputArrayId
import org.usvm.memory.collections.UInputArrayLengthId
import org.usvm.memory.UInputArrayLengths
import org.usvm.memory.collections.UInputFieldId
import org.usvm.memory.UInputFields
import org.usvm.memory.collections.UInputSymbolicMapId
import org.usvm.memory.collections.UInputSymbolicMapLengthId
import org.usvm.memory.UInputSymbolicMapLengthCollection
import org.usvm.memory.UInputSymbolicMap
import org.usvm.memory.collections.USymbolicCollectionId
import org.usvm.memory.collections.USymbolicArrayIndex
import org.usvm.memory.collections.USymbolicMapKey
import org.usvm.memory.collections.USymbolicCollection
import org.usvm.util.Region

//region KSMT aliases

typealias USort = KSort
typealias UBoolSort = KBoolSort
typealias UBvSort = KBvSort
typealias UBv32Sort = KBv32Sort
typealias USizeSort = KBv32Sort

typealias UExpr<Sort> = KExpr<Sort>
typealias UBoolExpr = UExpr<UBoolSort>
typealias USizeExpr = UExpr<USizeSort>
typealias UTrue = KTrue
typealias UFalse = KFalse
typealias UAndExpr = KAndExpr
typealias UOrExpr = KOrExpr
typealias UIteExpr<Sort> = KIteExpr<Sort>
typealias UEqExpr<Sort> = KEqExpr<Sort>
typealias UNotExpr = KNotExpr
typealias UConcreteInt = KIntNumExpr
typealias UConcreteInt32 = KBitVec32Value
typealias UConcreteInt64 = KBitVec64Value
typealias UConcreteSize = KBitVec32Value

typealias UAddressSort = KUninterpretedSort

typealias USizeType = Int

//endregion

abstract class USymbol<Sort : USort>(ctx: UContext) : UExpr<Sort>(ctx)

//region Object References

/**
 * An expr is of a [UHeapRef] type iff it's a [UConcreteHeapRef], [USymbolicHeapRef] or [UIteExpr] with [UAddressSort].
 * [UIteExpr]s have [UConcreteHeapRef]s and [USymbolicHeapRef]s as leafs.
 */
typealias UHeapRef = UExpr<UAddressSort>

/**
 * We maintain the invariant that any symbolic address **cannot** be equal to any UConcreteHeapAddress. Moreover,
 * symbolic addresses may only come from the specific [USymbol] of [UAddressSort] as [USymbol]s defines unknown
 * **symbolic** expressions.
 */
typealias USymbolicHeapRef = USymbol<UAddressSort>
typealias
UConcreteHeapAddress = Int

fun isSymbolicHeapRef(expr: UExpr<*>) =
    expr.sort == expr.uctx.addressSort && expr !is UConcreteHeapRef

class UConcreteHeapRef internal constructor(ctx: UContext, val address: UConcreteHeapAddress) : UHeapRef(ctx) {
    override val sort: UAddressSort = ctx.addressSort

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> {
        require(transformer is UExprTransformer<*>)
        return transformer.transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        printer.append("0x$address")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) { address }

    override fun internHashCode(): Int = hash(address)
}

class UNullRef internal constructor(
    ctx: UContext,
) : USymbolicHeapRef(ctx) {
    override val sort: UAddressSort
        get() = uctx.addressSort

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> {
        require(transformer is UExprTransformer<*>)
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun print(printer: ExpressionPrinter) {
        printer.append("null")
    }
}

//endregion

//region Read Expressions

class URegisterReading<Sort : USort> internal constructor(
    ctx: UContext,
    val idx: Int,
    override val sort: Sort
) : USymbol<Sort>(ctx) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*>)
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { idx }, { sort })

    override fun internHashCode(): Int = hash(idx, sort)

    override fun print(printer: ExpressionPrinter) {
        printer.append("%$idx")
    }
}

abstract class UCollectionReading<CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>, Key, Sort : USort>(
    ctx: UContext,
    val collection: USymbolicCollection<CollectionId, Key, Sort>
) : USymbol<Sort>(ctx) {
    override val sort: Sort get() = collection.sort
}

class UInputFieldReading<Field, Sort : USort> internal constructor(
    ctx: UContext,
    collection: UInputFields<Field, Sort>,
    val address: UHeapRef,
) : UCollectionReading<UInputFieldId<Field, Sort>, UHeapRef, Sort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return transformer.transform(this)
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

class UAllocatedArrayReading<ArrayType, Sort : USort> internal constructor(
    ctx: UContext,
    collection: UAllocatedArray<ArrayType, Sort>,
    val index: USizeExpr,
) : UCollectionReading<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>(ctx, collection) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return transformer.transform(this)
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

    @Suppress("UNCHECKED_CAST")
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return (transformer as UExprTransformer<ArrayType>).transform(this)
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

class UInputArrayLengthReading<ArrayType> internal constructor(
    ctx: UContext,
    collection: UInputArrayLengths<ArrayType>,
    val address: UHeapRef,
) : UCollectionReading<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    @Suppress("UNCHECKED_CAST")
    override fun accept(transformer: KTransformerBase): USizeExpr {
        require(transformer is UExprTransformer<*>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return (transformer as UExprTransformer<ArrayType>).transform(this)
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

//endregion

//region Mocked Expressions

abstract class UMockSymbol<Sort : USort>(ctx: UContext, override val sort: Sort) : USymbol<Sort>(ctx) {
}

// TODO: make indices compositional!
class UIndexedMethodReturnValue<Method, Sort : USort> internal constructor(
    ctx: UContext,
    val method: Method,
    val callIndex: Int,
    override val sort: Sort
) : UMockSymbol<Sort>(ctx, sort) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*>)
        return transformer.transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        printer.append("$method:#$callIndex")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { method }, { callIndex }, { sort })

    override fun internHashCode(): Int = hash(method, callIndex, sort)
}

//endregion

//region Subtyping Expressions

class UIsExpr<Type> internal constructor(
    ctx: UContext,
    val ref: UHeapRef,
    val type: Type
) : USymbol<UBoolSort>(ctx) {
    override val sort = ctx.boolSort

    override fun accept(transformer: KTransformerBase): UBoolExpr {
        require(transformer is UExprTransformer<*>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return transformer.transform(this)
    }


    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { ref }, { type })

    override fun internHashCode(): Int = hash(ref, type)
}
//endregion

// region symbolic collection expressions

class UAllocatedSymbolicMapReading<MapType, KeySort : USort, Sort : USort, Reg: Region<Reg>> internal constructor(
    ctx: UContext,
    collection: UAllocatedSymbolicMap<MapType, KeySort, Sort, Reg>,
    val key: UExpr<KeySort>,
) : UCollectionReading<UAllocatedSymbolicMapId<MapType, KeySort, Sort, Reg>, UExpr<KeySort>, Sort>(ctx, collection) {

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*>)
        return transformer.transform(this)
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
        require(transformer is UExprTransformer<*>)
        return transformer.transform(this)
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

class UInputSymbolicMapLengthReading<MapType> internal constructor(
    ctx: UContext,
    collection: UInputSymbolicMapLengthCollection,
    val address: UHeapRef,
) : UCollectionReading<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): USizeExpr {
        require(transformer is UExprTransformer<*>)
        return transformer.transform(this)
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

//endregion

//region Utils

val UBoolExpr.isFalse get() = this === ctx.falseExpr
val UBoolExpr.isTrue get() = !isFalse

//endregion