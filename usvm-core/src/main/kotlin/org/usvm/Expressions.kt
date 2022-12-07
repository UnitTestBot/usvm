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

typealias UExpr = KExpr<USort>
typealias UBoolExpr = KExpr<UBoolSort>
typealias USizeExpr = KExpr<USizeSort>
typealias UTrue = KTrue
typealias UFalse = KFalse
typealias UIteExpr = KIteExpr<UBoolSort>
typealias UNotExpr = KNotExpr

//endregion

//region Object References

typealias UHeapAddress = Int
const val nullAddress = 0

typealias UHeapRef = UExpr // TODO: KExpr<UAddressSort>

class UAddressSort(ctx: UContext) : USort(ctx) {
    override fun <T> accept(visitor: KSortVisitor<T>): T {
        TODO("Not yet implemented")
    }

    override fun print(builder: StringBuilder) {
        TODO("Not yet implemented")
    }
}

class UConcreteHeapRef(val address: UHeapAddress, ctx: UContext) : UHeapRef(ctx) {
    override val sort: UAddressSort = ctx.addressSort

    override fun accept(transformer: KTransformerBase): KExpr<USort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }

    val isNull = (address == nullAddress)
}

open class ULValueExpr(ctx: UContext, val cellSort: USort) : KExpr<UAddressSort>(ctx) {
    override val sort: UAddressSort = ctx.addressSort

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }
}

class URegisterRef(ctx: UContext, sort: USort, val idx: Int): ULValueExpr(ctx, sort) {
}

class UFieldRef<Field>(ctx: UContext, fieldSort: USort, val ref: UHeapRef, val field: Field): ULValueExpr(ctx, fieldSort) {
}

class UArrayIndexRef<ArrayType>(ctx: UContext, cellSort: USort, val ref: UHeapRef, val index: USizeExpr, val arrayType: ArrayType): ULValueExpr(ctx, cellSort) {
}

//endregion

//region Read Expressions

abstract class USymbol(ctx: UContext) : UExpr(ctx) {
}

class URegisterReading(ctx: UContext, idx: Int, override val sort: USort): USymbol(ctx) {
    override fun accept(transformer: KTransformerBase): KExpr<USort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }
}

open class UHeapReading<Key: UMemoryKey<Reg>, Reg: Region<Reg>, Value>(
    ctx: UContext,
    val region: UMemoryRegion<Key, Reg, Value>)
    : USymbol(ctx)
{
    override val sort: USort
        get() = region.sort

    override fun accept(transformer: KTransformerBase): KExpr<USort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }
}

class UFieldReading<Field>(ctx: UContext, region: UVectorMemoryRegion, val address: UHeapRef, val field: Field):
    UHeapReading<UHeapAddressKey, UHeapAddressRegion, UExpr>(ctx, region)
{
    override fun toString(): String = "$address.${field}"
}

class UArrayIndexReading<ArrayType>(ctx: UContext, region: UArrayMemoryRegion, val address: UHeapRef,
                                    val index: USizeExpr, val arrayType: ArrayType):
    UHeapReading<UArrayIndexKey, UArrayIndexRegion, UExpr>(ctx, region)
{
    override fun toString(): String = "$address[$index]"
}

class UArrayLength<ArrayType>(ctx: UContext, region: UArrayLengthMemoryRegion, val address: UHeapRef, val arrayType: ArrayType):
    UHeapReading<UHeapAddressKey, UHeapAddressRegion, USizeExpr>(ctx, region)
{
    override fun toString(): String = "length($address)"
}

//endregion

//region Mocked Expressions

abstract class UMockSymbol(ctx: UContext, override val sort: USort): USymbol(ctx) {
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

class UIsExpr<Type>(ctx: UContext, val ref: UHeapRef, val type: Type): USymbol(ctx) {
    override val sort = ctx.boolSort

    override fun accept(transformer: KTransformerBase): KExpr<USort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }

}

//endregion