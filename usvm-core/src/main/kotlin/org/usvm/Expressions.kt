package org.usvm

import org.ksmt.expr.*
import org.ksmt.expr.printer.ExpressionPrinter
import org.ksmt.expr.transformer.KTransformerBase
import org.ksmt.sort.*
import org.usvm.regions.Region

//region KSMT aliases

typealias USort = KSort
typealias UBoolSort = KBoolSort
typealias UBv32Sort = KBv32Sort
typealias USizeSort = KBv32Sort

typealias UExpr<Sort> = KExpr<Sort>
typealias UBoolExpr = UExpr<UBoolSort>
typealias USizeExpr = UExpr<USizeSort>
typealias UTrue = KTrue
typealias UFalse = KFalse
typealias UIteExpr<Sort> = KIteExpr<Sort>
typealias UNotExpr = KNotExpr

//endregion

//region Object References

typealias UHeapAddress = Int
const val nullAddress = 0

typealias UHeapRef = UExpr<UAddressSort> // TODO: KExpr<UAddressSort>

interface USortVisitor<T>: KSortVisitor<T> {
    fun visit(sort: UAddressSort): T
}

class UAddressSort(ctx: UContext) : USort(ctx) {
    override fun <T> accept(visitor: KSortVisitor<T>): T =
        when(visitor) {
            is USortVisitor<T> -> visitor.visit(this)
            else -> error("Can't visit UAddressSort by ${visitor.javaClass}")
        }

    override fun print(builder: StringBuilder) {
        builder.append("Address")
    }
}

class UConcreteHeapRef(val address: UHeapAddress, ctx: UContext) : UHeapRef(ctx) {
    override val sort: UAddressSort = ctx.addressSort

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }

    val isNull = (address == nullAddress)
}

//endregion

//region LValues

open class ULValue(val sort: USort) {
}

class URegisterRef(sort: USort, val idx: Int): ULValue(sort) {
}

class UFieldRef<Field>(fieldSort: USort, val ref: UHeapRef, val field: Field): ULValue(fieldSort) {
}

class UArrayIndexRef<ArrayType>(cellSort: USort, val ref: UHeapRef, val index: USizeExpr, val arrayType: ArrayType): ULValue(cellSort) {
}

//endregion

//region Read Expressions

abstract class USymbol<Sort: USort>(ctx: UContext) : UExpr<Sort>(ctx) {
}

@Suppress("UNUSED_PARAMETER")
class URegisterReading(ctx: UContext, idx: Int, override val sort: USort): USymbol<USort>(ctx) {
    override fun accept(transformer: KTransformerBase): KExpr<USort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }
}

open class UHeapReading<Key: UMemoryKey<Reg>, Reg: Region<Reg>, Sort: USort>(
    ctx: UContext,
    val region: UMemoryRegion<Key, Reg, Sort>)
    : USymbol<Sort>(ctx)
{
    override val sort: Sort
        get() = region.sort

    override fun accept(transformer: KTransformerBase): KExpr<Sort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }
}

class UFieldReading<Field>(ctx: UContext, region: UVectorMemoryRegion, val address: UHeapRef, val field: Field):
    UHeapReading<UHeapAddressKey, UHeapAddressRegion, USort>(ctx, region)
{
    override fun toString(): String = "$address.${field}"
}

class UArrayIndexReading<ArrayType>(ctx: UContext, region: UArrayMemoryRegion, val address: UHeapRef,
                                    val index: USizeExpr, val arrayType: ArrayType):
    UHeapReading<UArrayIndexKey, UArrayIndexRegion, USort>(ctx, region)
{
    override fun toString(): String = "$address[$index]"
}

class UArrayLength<ArrayType>(ctx: UContext, region: UArrayLengthMemoryRegion, val address: UHeapRef, val arrayType: ArrayType):
    UHeapReading<UHeapAddressKey, UHeapAddressRegion, USizeSort>(ctx, region)
{
    override fun toString(): String = "length($address)"
}

//endregion

//region Mocked Expressions

abstract class UMockSymbol(ctx: UContext, override val sort: USort): USymbol<USort>(ctx) {
}

// TODO: make indices compositional!
class UIndexedMethodReturnValue<Method>(ctx: UContext, val method: Method, val callIndex: Int, override val sort: USort): UMockSymbol(ctx, sort) {
    override fun accept(transformer: KTransformerBase): KExpr<USort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }
}

//endregion

//region Subtyping Expressions

class UIsExpr<Type>(ctx: UContext, val ref: UHeapRef, val type: Type): USymbol<UBoolSort>(ctx) {
    override val sort = ctx.boolSort

    override fun accept(transformer: KTransformerBase): KExpr<UBoolSort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }

}

//endregion
