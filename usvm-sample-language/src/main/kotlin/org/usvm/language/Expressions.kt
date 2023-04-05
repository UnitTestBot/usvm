package org.usvm.language

sealed interface Expr<out T : SampleType> {
    val type: T
}

typealias IntExpr = Expr<IntType>
typealias BooleanExpr = Expr<BooleanType>
typealias StructExpr = Expr<StructType>
typealias ArrayExpr<T> = Expr<ArrayType<T>>

class Register<out T : SampleType>(
    val idx: Int,
    override val type: T
) : Expr<T> {

    override fun toString(): String {
        return "%$idx"
    }
}

// int expressions

sealed class BaseIntExpr : IntExpr {
    final override val type get() = IntType
}

class IntConst(
    val const: Int,
) : BaseIntExpr() {
    override fun toString(): String {
        return "$const"
    }
}

class IntPlus(
    val left: IntExpr,
    val right: IntExpr,
) : BaseIntExpr() {
    override fun toString(): String {
        return "($left + $right)"
    }
}

class IntMinus(
    val left: IntExpr,
    val right: IntExpr,
) : BaseIntExpr() {
    override fun toString(): String {
        return "($left - $right)"
    }
}

class UnaryMinus(
    val value: IntExpr,
) : BaseIntExpr() {
    override fun toString(): String {
        return "(-$value)"
    }
}

class IntTimes(
    val left: IntExpr,
    val right: IntExpr,
) : BaseIntExpr() {
    override fun toString(): String {
        return "($left * $right)"
    }
}

class IntDiv(
    val left: IntExpr,
    val right: IntExpr,
) : BaseIntExpr() {
    override fun toString(): String {
        return "($left // $right)"
    }
}

class IntRem(
    val left: IntExpr,
    val right: IntExpr,
) : BaseIntExpr() {
    override fun toString(): String {
        return "($left % $right)"
    }
}


// boolean expressions

sealed class BaseBooleanExpr : BooleanExpr {
    final override val type get() = BooleanType
}

class BooleanConst(
    val const: Boolean,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "$const"
    }
}

class And(
    val left: BooleanExpr,
    val right: BooleanExpr,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "($left and $right)"
    }
}

class Or(
    val left: BooleanExpr,
    val right: BooleanExpr,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "($left or $right)"
    }
}

class Not(
    val value: BooleanExpr,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "!$value"
    }
}

class Gt(
    val left: IntExpr,
    val right: IntExpr,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "($left > $right)"
    }
}

class Ge(
    val left: IntExpr,
    val right: IntExpr,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "($left >= $right)"
    }
}

class Lt(
    val left: IntExpr,
    val right: IntExpr,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "($left < $right)"
    }
}

class Le(
    val left: IntExpr,
    val right: IntExpr,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "($left <= $right)"
    }
}

class IntEq(
    val left: IntExpr,
    val right: IntExpr,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "($left == $right)"
    }
}

class BooleanEq(
    val left: BooleanExpr,
    val right: BooleanExpr,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "($left == $right)"
    }
}

class StructEq(
    val left: StructExpr,
    val right: StructExpr,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "($left === $right)"
    }
}

class ArrayEq<T : SampleType>(
    val left: ArrayExpr<T>,
    val right: ArrayExpr<T>,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "($left === $right)"
    }
}


// object expressions

sealed class BaseStructExpr(
    struct: Struct
) : StructExpr {
    final override val type = StructType(struct)
}

class StructCreation(
    struct: Struct,
    val fields: List<Pair<Field<SampleType>, Expr<SampleType>>>,
) : BaseStructExpr(struct) {
    override fun toString() =
        "$type{${fields.joinToString { it.second.toString() }}}"
}

class StructIsNull(
    val struct: StructExpr,
) : BaseBooleanExpr() {
    override fun toString(): String {
        return "($struct == null)"
    }
}

class FieldSelect<T : SampleType>(
    val instance: StructExpr,
    val field: Field<T>,
) : Expr<T> {
    override val type: T get() = this.field.type
    override fun toString(): String {
        return "($instance).$field"
    }
}


// array expressions

sealed class BaseArrayExpr<T : SampleType>(elementType: T) : ArrayExpr<T> {
    final override val type = ArrayType(elementType)
}

class ArrayCreation<T : SampleType>(
    elementType: T,
    val size: IntExpr,
    val values: List<Expr<T>>,
) : BaseArrayExpr<T>(elementType) {
    override fun toString(): String {
        return "[$size]${type.elementType}{${values.joinToString()}}"
    }
}

class ArraySize(
    val array: ArrayExpr<*>,
) : BaseIntExpr() {
    override fun toString(): String {
        return "length($array)"
    }
}

class ArraySelect<T : SampleType>(
    val array: ArrayExpr<T>,
    val index: IntExpr,
) : Expr<T> {
    override val type: T get() = array.type.elementType

    override fun toString(): String {
        return "$array[$index]"
    }
}