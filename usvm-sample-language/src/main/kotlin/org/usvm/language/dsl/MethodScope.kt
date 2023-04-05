package org.usvm.language.dsl

import org.usvm.language.ArrayExpr
import org.usvm.language.ArrayIdxSetLValue
import org.usvm.language.Body
import org.usvm.language.BooleanExpr
import org.usvm.language.Call
import org.usvm.language.Expr
import org.usvm.language.Field
import org.usvm.language.FieldSetLValue
import org.usvm.language.Goto
import org.usvm.language.If
import org.usvm.language.IntExpr
import org.usvm.language.SampleType
import org.usvm.language.LValue
import org.usvm.language.Label
import org.usvm.language.Method
import org.usvm.language.Return
import org.usvm.language.SetLabel
import org.usvm.language.SetValue
import org.usvm.language.Stmt
import org.usvm.language.StructExpr
import org.usvm.language.Register
import org.usvm.language.RegisterLValue
import kotlin.reflect.KProperty

class MethodScope<R : SampleType?>(
    private val name: String,
    private val argumentTypes: List<SampleType>,
    private val returnType: R,
) {
    private val argumentsCount = argumentTypes.size

    private val stmts = mutableListOf<Stmt>()

    private var registerCounter = 0
    private var labelCounter = 0
    private val body = Body(registerCounter, stmts)

    fun init(): Method<R> =
        Method(
            name,
            argumentTypes,
            returnType,
            body
        )

    fun build() {
        body.registersCount = registerCounter
    }


    fun loop(condition: BooleanExpr, block: MethodScope<R>.() -> Unit) {
        val loopStart = Label(nextLabelIdx())
        val loopFinish = Label(nextLabelIdx())

        addSetLabel(loopStart)

        addStmt(If(condition.not(), loopFinish))
        block()
        addStmt(Goto(loopStart))

        addSetLabel(loopFinish)
    }

    fun branch(condition: BooleanExpr, block: MethodScope<R>.() -> Unit) {
        val loopFinish = Label(nextLabelIdx())

        addStmt(If(condition.not(), loopFinish))
        block()
        addSetLabel(loopFinish)
    }

    data class Invocation<out R : SampleType?>(
        val method: Method<R>,
        val args: List<Expr<SampleType>>,
    )

    operator fun Method<SampleType?>.invoke(vararg args: Expr<SampleType>) {
        addStmt(Call(null, this, args.toList()))
    }

    operator fun <T : SampleType> Method<T>.invoke(vararg args: Expr<SampleType>): Expr<T> {
        val variable = Register(nextRegister(isArgument = false), returnType)
        addStmt(Call(RegisterLValue(variable), this, args.toList()))

        return variable
    }

    fun <T : SampleType> Invocation<T>.expr(): Expr<T> {
        val variable = Register(nextRegister(isArgument = false), this.method.returnType)
        addStmt(Call(RegisterLValue(variable), method, args))
        return variable
    }

    fun <T> ret(expr: Expr<T>) where T : R & Any {
        addStmt(Return(expr))
    }

    fun ret() {
        addStmt(Return(null))
    }

    // region Stmt

    operator fun <T : SampleType> StructExpr.set(field: Field<T>, value: Expr<T>) {
        val lValue = FieldSetLValue(this, field)
        addSetValue(lValue, value)
    }

    operator fun <T : SampleType> ArrayExpr<T>.set(idx: IntExpr, value: Expr<SampleType>) {
        val lValue = ArrayIdxSetLValue(this, idx)
        addSetValue(lValue, value)
    }

    // endregion

    private fun addStmt(stmt: Stmt) {
        stmts.add(stmt)
    }

    private fun addSetLabel(label: Label) {
        stmts.add(SetLabel(label))
    }

    private fun addSetValue(lvalue: LValue, value: Expr<SampleType>) {
        val stmt = SetValue(lvalue, value)
        stmts.add(stmt)
    }

    private fun nextRegister(isArgument: Boolean) = (registerCounter++).also {
        require((it >= argumentsCount) xor isArgument)
    }

    private fun nextLabelIdx() = labelCounter++

    inner class DeclaredArg<out T : SampleType>(type: T) {
        private val argument = Register(nextRegister(isArgument = true), type)

        operator fun getValue(thisRef: Any?, property: KProperty<*>): Expr<T> {
            return argument
        }
    }

    inline operator fun <reified T : SampleType> Expr<T>.provideDelegate(thisRef: Any?, property: KProperty<*>) =
        VariableDecl(this)


    inner class VariableDecl<T : SampleType>(rvalue: Expr<T>) {
        private val variable = Register(nextRegister(isArgument = false), rvalue.type)
        private val lvalue = RegisterLValue(variable)

        init {
            addSetValue(lvalue, rvalue)
        }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): Expr<T> {
            return variable
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Expr<T>) {
            addSetValue(lvalue, value)
        }
    }
}
