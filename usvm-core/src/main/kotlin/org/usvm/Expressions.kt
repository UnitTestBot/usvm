package org.usvm

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.*
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.*
import org.usvm.memory.UAllocatedArrayId
import org.usvm.memory.UAllocatedArrayCollection
import org.usvm.memory.UAllocatedSymbolicMapId
import org.usvm.memory.UAllocatedSymbolicMap
import org.usvm.memory.UInputArrayId
import org.usvm.memory.UInputArrayLengthId
import org.usvm.memory.UInputArrayLengthCollection
import org.usvm.memory.UInputArrayCollection
import org.usvm.memory.UInputFieldId
import org.usvm.memory.UInputFieldCollection
import org.usvm.memory.UInputSymbolicMapId
import org.usvm.memory.UInputSymbolicMapLengthId
import org.usvm.memory.UInputSymbolicMapLengthCollection
import org.usvm.memory.UInputSymbolicMap
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicArrayIndex
import org.usvm.memory.USymbolicMapKey
import org.usvm.memory.USymbolicCollection
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

typealias UIndexType = Int

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
typealias UConcreteHeapAddress = Int

fun isSymbolicHeapRef(expr: UExpr<*>) =
    expr.sort == expr.uctx.addressSort && expr !is UConcreteHeapRef

class UConcreteHeapRef internal constructor(ctx: UContext, val address: UConcreteHeapAddress) : UHeapRef(ctx) {
    override val sort: UAddressSort = ctx.addressSort

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> {
        require(transformer is UExprTransformer<*, *>)
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
        require(transformer is UExprTransformer<*, *>)
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun print(printer: ExpressionPrinter) {
        printer.append("null")
    }
}

//endregion

//region LValues
open class ULValue(val sort: USort)

class URegisterRef(sort: USort, val idx: Int) : ULValue(sort)

class UFieldRef<Field>(fieldSort: USort, val ref: UHeapRef, val field: Field) : ULValue(fieldSort)

class UArrayIndexRef<ArrayType>(
    cellSort: USort,
    val ref: UHeapRef,
    val index: USizeExpr,
    val arrayType: ArrayType
) : ULValue(cellSort)

//endregion

//region Read Expressions

class URegisterReading<Sort : USort> internal constructor(
    ctx: UContext,
    val idx: Int,
    override val sort: Sort
) : USymbol<Sort>(ctx) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*, *>)
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
    collection: UInputFieldCollection<Field, Sort>,
    val address: UHeapRef,
) : UCollectionReading<UInputFieldId<Field, Sort>, UHeapRef, Sort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    @Suppress("UNCHECKED_CAST")
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*, *>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return (transformer as UExprTransformer<Field, *>).transform(this)
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
    collection: UAllocatedArrayCollection<ArrayType, Sort>,
    val index: USizeExpr,
) : UCollectionReading<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>(ctx, collection) {
    @Suppress("UNCHECKED_CAST")
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*, *>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return (transformer as UExprTransformer<*, ArrayType>).transform(this)
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
    collection: UInputArrayCollection<ArrayType, Sort>,
    val address: UHeapRef,
    val index: USizeExpr
) : UCollectionReading<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    @Suppress("UNCHECKED_CAST")
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*, *>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return (transformer as UExprTransformer<*, ArrayType>).transform(this)
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
    collection: UInputArrayLengthCollection<ArrayType>,
    val address: UHeapRef,
) : UCollectionReading<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    @Suppress("UNCHECKED_CAST")
    override fun accept(transformer: KTransformerBase): USizeExpr {
        require(transformer is UExprTransformer<*, *>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return (transformer as UExprTransformer<*, ArrayType>).transform(this)
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
        require(transformer is UExprTransformer<*, *>)
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

    @Suppress("UNCHECKED_CAST")
    override fun accept(transformer: KTransformerBase): UBoolExpr {
        require(transformer is UExprTransformer<*, *>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return (transformer as UExprTransformer<*, Type>).transform(this)
    }


    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { ref }, { type })

    override fun internHashCode(): Int = hash(ref, type)
}
//endregion

// region symbolic collection expressions

class UAllocatedSymbolicMapReading<KeySort : USort, Reg : Region<Reg>, Sort : USort> internal constructor(
    ctx: UContext,
    collection: UAllocatedSymbolicMap<KeySort, Reg, Sort>,
    val key: UExpr<KeySort>,
) : UCollectionReading<UAllocatedSymbolicMapId<KeySort, Reg, Sort>, UExpr<KeySort>, Sort>(ctx, collection) {

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*, *>)
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

class UInputSymbolicMapReading<KeySort : USort, Reg : Region<Reg>, Sort : USort> internal constructor(
    ctx: UContext,
    collection: UInputSymbolicMap<KeySort, Reg, Sort>,
    val address: UHeapRef,
    val key: UExpr<KeySort>
) : UCollectionReading<UInputSymbolicMapId<KeySort, Reg, Sort>, USymbolicMapKey<KeySort>, Sort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*, *>)
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

class UInputSymbolicMapLengthReading internal constructor(
    ctx: UContext,
    collection: UInputSymbolicMapLengthCollection,
    val address: UHeapRef,
) : UCollectionReading<UInputSymbolicMapLengthId, UHeapRef, USizeSort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): USizeExpr {
        require(transformer is UExprTransformer<*, *>)
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