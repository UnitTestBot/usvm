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
import org.usvm.memory.ULValue
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

//region KSMT aliases

typealias USort = KSort
typealias UBoolSort = KBoolSort
typealias UBvSort = KBvSort
typealias UBv32Sort = KBv32Sort
typealias UFpSort = KFpSort

typealias UExpr<Sort> = KExpr<Sort>
typealias UBoolExpr = UExpr<UBoolSort>
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

abstract class USymbol<Sort : USort>(ctx: UContext<*>) : UExpr<Sort>(ctx)

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

val UConcreteHeapAddress.isAllocated: Boolean get() = this >= INITIAL_CONCRETE_ADDRESS
val UConcreteHeapAddress.isStatic: Boolean get() = this <= INITIAL_STATIC_ADDRESS

@OptIn(ExperimentalContracts::class)
fun isSymbolicHeapRef(expr: UExpr<*>): Boolean {
    contract {
        returns(true) implies (expr is USymbol<*>)
    }

    return expr.sort == expr.uctx.addressSort && expr is USymbol<*>
}

@OptIn(ExperimentalContracts::class)
fun isAllocatedConcreteHeapRef(expr: UExpr<*>): Boolean {
    contract {
        returns(true) implies (expr is UConcreteHeapRef)
    }

    return expr is UConcreteHeapRef && expr.isAllocated
}

@OptIn(ExperimentalContracts::class)
fun isStaticHeapRef(expr: UExpr<*>): Boolean {
    contract {
        returns(true) implies (expr is UConcreteHeapRef)
    }

    return expr is UConcreteHeapRef && expr.isStatic
}

val UConcreteHeapRef.isAllocated: Boolean get() = address.isAllocated
val UConcreteHeapRef.isStatic: Boolean get() = address.isStatic

class UConcreteHeapRefDecl internal constructor(
    ctx: UContext<*>,
    val address: UConcreteHeapAddress,
) : KConstDecl<UAddressSort>(ctx, "0x$address", ctx.addressSort) {
    override fun apply(args: List<KExpr<*>>): KApp<UAddressSort, *> = uctx.mkConcreteHeapRef(address)
}

class UConcreteHeapRef internal constructor(
    ctx: UContext<*>,
    val address: UConcreteHeapAddress,
) : UIntepretedValue<UAddressSort>(ctx) {

    override val decl: UConcreteHeapRefDecl get() = uctx.mkConcreteHeapRefDecl(address)

    override val sort: UAddressSort = ctx.addressSort

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        printer.append("0x$address")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) { address }

    override fun internHashCode(): Int = hash(address)
}

class UNullRef internal constructor(
    ctx: UContext<*>,
) : USymbolicHeapRef(ctx) {
    override val sort: UAddressSort
        get() = uctx.addressSort

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun print(printer: ExpressionPrinter) {
        printer.append("null")
    }
}

// We split all addresses into four parts:
//     * static values (represented as allocated but interpreted as input): [Int.MIN_VALUE..INITIAL_STATIC_ADDRESS]
//     * input values: (INITIAL_STATIC_ADDRESS..0),
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
 * Allocated addresses takes this semi-interval: (0..[Int.MAX_VALUE])
 */
const val INITIAL_CONCRETE_ADDRESS = NULL_ADDRESS + 1

/**
 * A constant corresponding to the first static address in any symbolic memory.
 * Static addresses takes this segment: [[Int.MIN_VALUE]..[INITIAL_STATIC_ADDRESS]]
 */
const val INITIAL_STATIC_ADDRESS = -(1 shl 20) // Use value not less than UNINTERPRETED_SORT_MIN_ALLOWED_VALUE in ksmt


//endregion

//region Read Expressions

class URegisterReading<Sort : USort> internal constructor(
    ctx: UContext<*>,
    val idx: Int,
    override val sort: Sort,
) : USymbol<Sort>(ctx) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { idx }, { sort })

    override fun internHashCode(): Int = hash(idx, sort)

    override fun print(printer: ExpressionPrinter) {
        printer.append("%$idx")
    }
}

abstract class UCollectionReading<CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>, Key, Sort : USort>(
    ctx: UContext<*>,
    val collection: USymbolicCollection<CollectionId, Key, Sort>
) : USymbol<Sort>(ctx) {
    override val sort: Sort get() = collection.sort
}

//endregion

//region Mocked Expressions

abstract class UMockSymbol<Sort : USort>(ctx: UContext<*>, override val sort: Sort) : USymbol<Sort>(ctx)

// TODO: make indices compositional!
class UIndexedMethodReturnValue<Method, Sort : USort> internal constructor(
    ctx: UContext<*>,
    val method: Method,
    val callIndex: Int,
    override val sort: Sort,
) : UMockSymbol<Sort>(ctx, sort) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        printer.append("$method:#$callIndex")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { method }, { callIndex }, { sort })

    override fun internHashCode(): Int = hash(method, callIndex, sort)
}

class UTrackedSymbol<Sort : USort> internal constructor(
    ctx: UContext<*>,
    val name: String,
    override val sort: Sort
): UMockSymbol<Sort>(ctx, sort) {
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { name }, { sort })

    override fun internHashCode(): Int = hash(name, sort)

    override fun print(printer: ExpressionPrinter) {
        printer.append(name)
    }
}

//endregion

//region Subtyping Expressions

abstract class UIsExpr<Type> internal constructor(
    ctx: UContext<*>,
    val ref: UHeapRef,
) : USymbol<UBoolSort>(ctx) {
    final override val sort = ctx.boolSort
}

/**
 * Means **either** [ref] is [UNullRef] **or** [ref] !is [UNullRef] and [ref] <: [supertype]. Thus, the actual type
 * inheritance is checked only on non-null refs.
 */
class UIsSubtypeExpr<Type> internal constructor(
    ctx: UContext<*>,
    ref: UHeapRef,
    val supertype: Type,
) : UIsExpr<Type>(ctx, ref) {
    override fun accept(transformer: KTransformerBase): UBoolExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<Type, USort>().transform(this)
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
    ctx: UContext<*>,
    ref: UHeapRef,
    val subtype: Type,
) : UIsExpr<Type>(ctx, ref) {
    override fun accept(transformer: KTransformerBase): UBoolExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asTypedTransformer<Type, USort>().transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        printer.append("($subtype is subtype of type($ref))")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { ref }, { subtype })

    override fun internHashCode(): Int = hash(ref, subtype)
}

//endregion

//region Pointer Semantics

class UPointer(
    ctx: UContext<*>,
    var address: UConcreteHeapAddress
) : UExpr<UAddressSort>(ctx) {
    override val sort: UAddressSort = ctx.pointerSort

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) { address }

    override fun internHashCode(): Int = hash(address)

    override fun print(printer: ExpressionPrinter) {
        printer.append("&0x$address")
    }
}

//endregion

//region Utils

val UBoolExpr.isFalse get() = this == ctx.falseExpr
val UBoolExpr.isTrue get() = this == ctx.trueExpr

//endregion
