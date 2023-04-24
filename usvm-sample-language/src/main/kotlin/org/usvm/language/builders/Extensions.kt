package org.usvm.language.builders

import org.usvm.language.And
import org.usvm.language.ArrayCreation
import org.usvm.language.ArrayEq
import org.usvm.language.ArrayExpr
import org.usvm.language.ArraySelect
import org.usvm.language.ArraySize
import org.usvm.language.ArrayType
import org.usvm.language.BooleanConst
import org.usvm.language.BooleanEq
import org.usvm.language.BooleanExpr
import org.usvm.language.Expr
import org.usvm.language.Field
import org.usvm.language.FieldSelect
import org.usvm.language.Ge
import org.usvm.language.Gt
import org.usvm.language.IntConst
import org.usvm.language.IntDiv
import org.usvm.language.IntEq
import org.usvm.language.IntExpr
import org.usvm.language.IntMinus
import org.usvm.language.IntPlus
import org.usvm.language.IntRem
import org.usvm.language.IntTimes
import org.usvm.language.Le
import org.usvm.language.Lt
import org.usvm.language.Not
import org.usvm.language.Or
import org.usvm.language.SampleType
import org.usvm.language.StructCreation
import org.usvm.language.StructEq
import org.usvm.language.StructExpr
import org.usvm.language.StructIsNull
import org.usvm.language.StructType
import org.usvm.language.UnaryMinus

val Int.expr get() = IntConst(this)
val Boolean.expr get() = BooleanConst(this)
operator fun <T : SampleType> ArrayType<T>.invoke(
    vararg values: Expr<T>,
    size: IntExpr = IntConst(values.size)
) = ArrayCreation(elementType, size, values.toList())

operator fun StructType.invoke(vararg initValues: Pair<Field<SampleType>, Expr<SampleType>>): StructExpr =
    StructCreation(struct, initValues.toList())


operator fun IntExpr.unaryMinus() = UnaryMinus(this)
operator fun IntExpr.plus(other: IntExpr) = IntPlus(this, other)
operator fun IntExpr.minus(other: IntExpr) = IntMinus(this, other)
operator fun IntExpr.times(other: IntExpr) = IntTimes(this, other)
operator fun IntExpr.div(other: IntExpr) = IntDiv(this, other)
operator fun IntExpr.rem(other: IntExpr) = IntRem(this, other)

operator fun BooleanExpr.not() = Not(this)
infix fun BooleanExpr.and(other: BooleanExpr) = And(this, other)
infix fun BooleanExpr.or(other: BooleanExpr) = Or(this, other)
infix fun IntExpr.lt(other: IntExpr) = Lt(this, other)
infix fun IntExpr.le(other: IntExpr) = Le(this, other)
infix fun IntExpr.gt(other: IntExpr) = Gt(this, other)
infix fun IntExpr.ge(other: IntExpr) = Ge(this, other)

infix fun BooleanExpr.eq(other: BooleanExpr) = BooleanEq(this, other)
infix fun IntExpr.eq(other: IntExpr) = IntEq(this, other)
infix fun<T : SampleType> ArrayExpr<T>.eq(other: ArrayExpr<T>) = ArrayEq(this, other)
infix fun StructExpr.eq(other: StructExpr) = StructEq(this, other)

operator fun<T : SampleType> ArrayExpr<T>.get(idx: IntExpr) = ArraySelect(this, idx)
val ArrayExpr<*>.size get() = ArraySize(this)

operator fun<T : SampleType> StructExpr.get(field: Field<T>) = FieldSelect(this, field)
val StructExpr.isNull get() = StructIsNull(this)