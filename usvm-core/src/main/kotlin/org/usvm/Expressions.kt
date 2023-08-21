package org.usvm

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.decl.KConstDecl
import io.ksmt.expr.KAndExpr
import io.ksmt.expr.KApp
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KBitVec64Value
import io.ksmt.expr.KEqExpr
import io.ksmt.expr.KExpr
import io.ksmt.expr.KFalse
import io.ksmt.expr.KIntNumExpr
import io.ksmt.expr.KInterpretedValue
import io.ksmt.expr.KIteExpr
import io.ksmt.expr.KNotExpr
import io.ksmt.expr.KOrExpr
import io.ksmt.expr.KTrue
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBv32Sort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KFpSort
import io.ksmt.sort.KSort
import io.ksmt.sort.KUninterpretedSort
import org.usvm.memory.collection.region.UAllocatedArray
import org.usvm.memory.collection.id.UAllocatedArrayId
import org.usvm.memory.collection.id.UAllocatedSymbolicMapId
import org.usvm.memory.collection.region.UAllocatedSymbolicMap
import org.usvm.memory.collection.region.UInputArray
import org.usvm.memory.collection.id.UInputArrayId
import org.usvm.memory.collection.id.UInputArrayLengthId
import org.usvm.memory.collection.region.UInputArrayLengths
import org.usvm.memory.collection.id.UInputFieldId
import org.usvm.memory.collection.region.UInputFields
import org.usvm.memory.collection.id.UInputSymbolicMapId
import org.usvm.memory.collection.id.UInputSymbolicMapLengthId
import org.usvm.memory.collection.region.UInputSymbolicMapLengthCollection
import org.usvm.memory.collection.region.UInputSymbolicMap
import org.usvm.memory.collection.id.USymbolicCollectionId
import org.usvm.memory.collection.key.USymbolicArrayIndex
import org.usvm.memory.collection.key.USymbolicMapKey
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.util.Region

//region KSMT aliases

typealias USort = KSort
typealias UBoolSort = KBoolSort
typealias UBvSort = KBvSort
typealias UBv32Sort = KBv32Sort
typealias USizeSort = KBv32Sort
typealias UFpSort = KFpSort

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
typealias UIntepretedValue<Sort> = KInterpretedValue<Sort>
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
 * An expr is a [UHeapRef] iff it's a [UConcreteHeapRef], [USymbolicHeapRef] or [UIteExpr] with [UAddressSort].
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
    expr.sort == expr.uctx.addressSort && expr is USymbol<*>

class UConcreteHeapRefDecl internal constructor(
    ctx: UContext,
    val address: UConcreteHeapAddress,
) : KConstDecl<UAddressSort>(ctx, "0x$address", ctx.addressSort) {
    override fun apply(args: List<KExpr<*>>): KApp<UAddressSort, *> = uctx.mkConcreteHeapRef(address)
}

class UConcreteHeapRef internal constructor(
    ctx: UContext,
    val address: UConcreteHeapAddress,
) : UIntepretedValue<UAddressSort>(ctx) {

    override val decl: UConcreteHeapRefDecl get() = uctx.mkConcreteHeapRefDecl(address)

    override val sort: UAddressSort = ctx.addressSort

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> {
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
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
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun print(printer: ExpressionPrinter) {
        printer.append("null")
    }
}

// We split all addresses into three parts:
//     * input values: [Int.MIN_VALUE..0),
//     * null value: [0]
//     * allocated values: (0..[Int.MAX_VALUE]

/**
 * A constant corresponding to `null`.
 */
const val NULL_ADDRESS = 0

/**
 * A constant corresponding to the first input address in any decoded model.
 * Input addresses takes this semi-interval: [[Int.MIN_VALUE]..0)
 */
const val INITIAL_INPUT_ADDRESS = NULL_ADDRESS - 1

/**
 * A constant corresponding to the first allocated address in any symbolic memory.
 * Input addresses takes this semi-interval: (0..[Int.MAX_VALUE])
 */
const val INITIAL_CONCRETE_ADDRESS = NULL_ADDRESS + 1


//endregion

//region Read Expressions

class URegisterReading<Sort : USort> internal constructor(
    ctx: UContext,
    val idx: Int,
    override val sort: Sort,
) : USymbol<Sort>(ctx) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
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
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
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

class UInputArrayLengthReading<ArrayType> internal constructor(
    ctx: UContext,
    collection: UInputArrayLengths<ArrayType>,
    val address: UHeapRef,
) : UCollectionReading<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>(ctx, collection) {
    init {
        require(address !is UNullRef)
    }

    override fun accept(transformer: KTransformerBase): USizeExpr {
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<ArrayType>().transform(this)
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

abstract class UMockSymbol<Sort : USort>(ctx: UContext, override val sort: Sort) : USymbol<Sort>(ctx)

// TODO: make indices compositional!
class UIndexedMethodReturnValue<Method, Sort : USort> internal constructor(
    ctx: UContext,
    val method: Method,
    val callIndex: Int,
    override val sort: Sort,
) : UMockSymbol<Sort>(ctx, sort) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
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

abstract class UIsExpr<Type> internal constructor(
    ctx: UContext,
    val ref: UHeapRef,
) : USymbol<UBoolSort>(ctx) {
    final override val sort = ctx.boolSort
}

/**
 * Means **either** [ref] is [UNullRef] **or** [ref] !is [UNullRef] and [ref] <: [supertype]. Thus, the actual type
 * inheritance is checked only on non-null refs.
 */
class UIsSubtypeExpr<Type> internal constructor(
    ctx: UContext,
    ref: UHeapRef,
    val supertype: Type,
) : UIsExpr<Type>(ctx, ref) {
    override fun accept(transformer: KTransformerBase): UBoolExpr {
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<Type>().transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        printer.append("($ref is $supertype)")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { ref }, { supertype })

    override fun internHashCode(): Int = hash(ref, supertype)
}

/**
 * Means [ref] !is [UNullRef] and [subtype] <: [ref]. Thus, the actual type
 * inheritance is checked only on non-null refs.
 */
class UIsSupertypeExpr<Type> internal constructor(
    ctx: UContext,
    ref: UHeapRef,
    val subtype: Type,
) : UIsExpr<Type>(ctx, ref) {
    override fun accept(transformer: KTransformerBase): UBoolExpr {
        require(transformer is UTransformer<*>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<Type>().transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        printer.append("($subtype is subtype of type($ref))")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { ref }, { subtype })

    override fun internHashCode(): Int = hash(ref, subtype)
}

//endregion

// region symbolic collection expressions

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

//endregion

//region Utils

val UBoolExpr.isFalse get() = this == ctx.falseExpr
val UBoolExpr.isTrue get() = this == ctx.trueExpr

//endregion
