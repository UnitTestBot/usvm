package org.usvm.language

// region stmts

sealed interface Stmt

class SetLabel(
    val label: Label
) : Stmt {
    override fun toString(): String = label.toString()
}

class SetValue(
    val lvalue: LValue,
    val expr: Expr<SampleType>
) : Stmt {
    override fun toString(): String = "$lvalue = $expr"
}

class Goto(
    val label: Label
) : Stmt {
    override fun toString(): String = "goto $label"
}

class If(
    val condition: BooleanExpr,
    val label: Label
) : Stmt {
    override fun toString(): String = "if $condition goto $label"
}

class Call(
    val lvalue: LValue?,
    val method: Method<SampleType?>,
    val args: List<Expr<SampleType>>
) : Stmt {
    override fun toString(): String = "$lvalue = ${method.name}(${args.joinToString()})"
}

class Return(
    val valueToReturn: Expr<SampleType>?
) : Stmt {
    override fun toString(): String = "return $valueToReturn "
}

// endregion

class Label(
    val idx: Int
) {
    override fun toString(): String = "label@$idx"
}

// region lvalue

sealed interface LValue

class RegisterLValue(
    val value: Register<SampleType>
) : LValue {
    override fun toString(): String = value.toString()
}

class FieldSetLValue(
    val instance: StructExpr,
    val field: Field<SampleType>
) : LValue {
    override fun toString(): String = "$instance.${field.name}"
}

class ArrayIdxSetLValue(
    val array: ArrayExpr<*>,
    val index: IntExpr,
) : LValue {
    override fun toString(): String = "$array[$index]"
}

//endregion
