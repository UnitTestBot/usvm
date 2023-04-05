package org.usvm.language

// region stmts

sealed interface Stmt

class SetLabel(
    val label: Label
) : Stmt

class SetValue(
    val lvalue: LValue,
    val expr: Expr<SampleType>
) : Stmt

class Goto(
    val label: Label
) : Stmt

class If(
    val condition: BooleanExpr,
    val label: Label
) : Stmt

class Call(
    val lvalue: LValue?,
    val method: Method<SampleType?>,
    val args: List<Expr<SampleType>>
) : Stmt

class Return(
    val valueToReturn: Expr<SampleType>?
) : Stmt

// endregion

class Label(
    val idx: Int
)

// region lvalue

sealed interface LValue

class RegisterLValue(
    val value: Register<SampleType>
) : LValue

class FieldSetLValue(
    val instance: StructExpr,
    val field: Field<SampleType>
) : LValue

class ArrayIdxSetLValue(
    val array: ArrayExpr<*>,
    val index: IntExpr,
) : LValue

//endregion
