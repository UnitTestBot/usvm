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

//endregion

//region Object References

typealias UIndexType = Int
typealias UHeapAddress = Int

const val nullAddress = 0

typealias UHeapRef = UExpr<UAddressSort> // TODO: KExpr<UAddressSort>

interface USortVisitor<T> : KSortVisitor<T> {
    fun visit(sort: UAddressSort): T
}

class UAddressSort internal constructor(ctx: UContext) : USort(ctx) {
    override fun <T> accept(visitor: KSortVisitor<T>): T =
        when (visitor) {
            is USortVisitor<T> -> visitor.visit(this)
            else -> error("Can't visit UAddressSort by ${visitor.javaClass}")
        }

    override fun print(builder: StringBuilder) {
        builder.append("Address")
    }
}

class UConcreteHeapRef internal constructor(ctx: UContext, val address: UHeapAddress) : UHeapRef(ctx) {
    override val sort: UAddressSort = ctx.addressSort

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }

    val isNull = (address == nullAddress)

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) { address }

    override fun internHashCode(): Int = hash(address)
}

//endregion

//region LValues
open class ULValue(val sort: USort) {
}

class URegisterRef(sort: USort, val idx: Int) : ULValue(sort) {
}

class UFieldRef<Field>(fieldSort: USort, val ref: UHeapRef, val field: Field) : ULValue(fieldSort) {
}

class UArrayIndexRef<ArrayType>(
    cellSort: USort,
    val ref: UHeapRef,
    val index: USizeExpr,
    val arrayType: ArrayType
) : ULValue(cellSort) {}

//endregion

//region Read Expressions

abstract class USymbol<Sort : USort>(ctx: UContext) : UExpr<Sort>(ctx) {
}

@Suppress("UNUSED_PARAMETER")
class URegisterReading internal constructor(
    ctx: UContext,
    val idx: Int,
    override val sort: USort
) : USymbol<USort>(ctx) {
    override fun accept(transformer: KTransformerBase): KExpr<USort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { idx }, { sort })

    override fun internHashCode(): Int = hash(idx, sort)
}

abstract class UHeapReading<Key, Sort : USort>(
    ctx: UContext,
    val region: UMemoryRegion<Key, Sort>
) : USymbol<Sort>(ctx) {
    override val sort: Sort = region.sort

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }
}

class UFieldReading<Field> internal constructor(
    ctx: UContext,
    region: UVectorMemoryRegion<USort>,
    val address: UHeapRef,
    val field: Field
) : UHeapReading<UHeapRef, USort>(ctx, region) {
    override fun toString(): String = "$address.${field}"

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { region }, { address }, { field })

    override fun internHashCode(): Int = hash(region, address, field)
}

class UAllocatedArrayReading<ArrayType> internal constructor(
    ctx: UContext,
    region: UAllocatedArrayMemoryRegion<USort>,
    val address: UHeapAddress,
    val index: USizeExpr,
    val arrayType: ArrayType,
    val elementSort: USort
) : UHeapReading<USizeExpr, USort>(ctx, region) {
    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { region },
            { address },
            { index },
            { arrayType }
        )

    override fun internHashCode(): Int = hash(region, address, index, arrayType)

    override fun toString(): String = "$0xaddress[$index]"
}

class UInputArrayReading<ArrayType> internal constructor(
    ctx: UContext,
    region: UInputArrayMemoryRegion<USort>,
    val address: UHeapRef,
    val index: USizeExpr,
    val arrayType: ArrayType,
    val elementSort: USort
) : UHeapReading<USymbolicArrayIndex, USort>(ctx, region) {
    override fun toString(): String = "$address[$index]"

    override fun internEquals(other: Any): Boolean =
        structurallyEqual(
            other,
            { region },
            { address },
            { index },
            { arrayType },
            { elementSort }
        )

    override fun internHashCode(): Int = hash(region, address, index, arrayType, elementSort)
}

class UArrayLength<ArrayType> internal constructor(
    ctx: UContext,
    region: UArrayLengthMemoryRegion,
    val address: UHeapRef,
    val arrayType: ArrayType
) : UHeapReading<UHeapRef, USizeSort>(ctx, region) {
    override fun toString(): String = "length($address)"

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { region }, { address }, { arrayType })

    override fun internHashCode(): Int = hash(region, address, arrayType)
}

//endregion

//region Mocked Expressions

abstract class UMockSymbol(ctx: UContext, override val sort: USort) : USymbol<USort>(ctx) {
}

// TODO: make indices compositional!
class UIndexedMethodReturnValue<Method> internal constructor(
    ctx: UContext,
    val method: Method,
    val callIndex: Int,
    override val sort: USort
) : UMockSymbol(ctx, sort) {
    override fun accept(transformer: KTransformerBase): KExpr<USort> {
        TODO("Not yet implemented")
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

    override fun accept(transformer: KTransformerBase): KExpr<UBoolSort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { ref }, { type })

    override fun internHashCode(): Int = hash(ref, type)
}

//endregion
