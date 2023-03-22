package org.usvm

import org.ksmt.cache.hash
import org.ksmt.cache.structurallyEqual
import org.ksmt.expr.*
import org.ksmt.expr.printer.ExpressionPrinter
import org.ksmt.expr.transformer.KTransformerBase
import org.ksmt.sort.*

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
typealias UIteExpr<Sort> = KIteExpr<Sort>
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

abstract class UHeapReading<RegionId : URegionId<Key>, Key, Sort : USort>(
    ctx: UContext,
    val region: UMemoryRegion<RegionId, Key, Sort>
) : USymbol<Sort>(ctx) {
    override val sort: Sort get() = region.sort
}

class UInputFieldReading<Field, Sort : USort> internal constructor(
    ctx: UContext,
    region: UInputFieldRegion<Field, Sort>,
    val address: UHeapRef,
) : UHeapReading<UInputFieldRegionId<Field>, UHeapRef, Sort>(ctx, region) {
    @Suppress("UNCHECKED_CAST")
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*, *>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return (transformer as UExprTransformer<Field, *>).transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { region }, { address })

    override fun internHashCode(): Int = hash(region, address)

    override fun print(printer: ExpressionPrinter) {
        printer.append(region.toString())
        printer.append("[")
        printer.append(address)
        printer.append("]")
    }
}

class UAllocatedArrayReading<ArrayType, Sort : USort> internal constructor(
    ctx: UContext,
    region: UAllocatedArrayRegion<ArrayType, Sort>,
    val index: USizeExpr,
) : UHeapReading<UAllocatedArrayId<ArrayType>, USizeExpr, Sort>(ctx, region) {
    @Suppress("UNCHECKED_CAST")
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*, *>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return (transformer as UExprTransformer<*, ArrayType>).transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { region },
            { index },
        )

    override fun internHashCode(): Int = hash(region, index)

    override fun print(printer: ExpressionPrinter) {
        printer.append(region.toString())
        printer.append("[")
        printer.append(index)
        printer.append("]")
    }
}

class UInputArrayReading<ArrayType, Sort : USort> internal constructor(
    ctx: UContext,
    region: UInputArrayRegion<ArrayType, Sort>,
    val address: UHeapRef,
    val index: USizeExpr
) : UHeapReading<UInputArrayId<ArrayType>, USymbolicArrayIndex, Sort>(ctx, region) {
    @Suppress("UNCHECKED_CAST")
    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        require(transformer is UExprTransformer<*, *>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return (transformer as UExprTransformer<*, ArrayType>).transform(this)
    }

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { region },
            { address },
            { index },
        )

    override fun internHashCode(): Int = hash(region, address, index)

    override fun print(printer: ExpressionPrinter) {
        printer.append(region.toString())
        printer.append("[")
        printer.append(address)
        printer.append(", ")
        printer.append(index)
        printer.append("]")
    }
}

class UInputArrayLengthReading<ArrayType> internal constructor(
    ctx: UContext,
    region: UInputArrayLengthRegion<ArrayType>,
    val address: UHeapRef,
) : UHeapReading<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>(ctx, region) {
    @Suppress("UNCHECKED_CAST")
    override fun accept(transformer: KTransformerBase): USizeExpr {
        require(transformer is UExprTransformer<*, *>)
        // An unchecked cast here it to be able to choose the right overload from UExprTransformer
        return (transformer as UExprTransformer<*, ArrayType>).transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { region }, { address })

    override fun internHashCode(): Int = hash(region, address)

    override fun print(printer: ExpressionPrinter) {
        printer.append(region.toString())
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
        TODO("Not yet implemented")
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

//region Utils

val UBoolExpr.isFalse get() = this === ctx.falseExpr
val UBoolExpr.isTrue get() = !isFalse

//endregion