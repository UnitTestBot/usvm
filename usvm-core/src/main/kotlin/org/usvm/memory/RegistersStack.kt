package org.usvm.memory

import org.ksmt.expr.KExpr
import org.ksmt.utils.asExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import java.util.Stack

interface URegistersStackEvaluator {
    fun <Sort : USort> eval(registerIndex: Int, sort: Sort): UExpr<Sort>
}

class URegistersStackFrame(registers: Array<UExpr<out USort>?>) {
    constructor(registersCount: Int) :
        this(Array(registersCount) { null })

    var registers: Array<UExpr<out USort>?> = registers
        protected set

    fun realloc(registersCount: Int) {
        registers = Array(registersCount) { if (it < registers.size) registers[it] else null }
    }

    operator fun get(index: Int) = registers[index]
    operator fun set(index: Int, value: UExpr<out USort>) = registers.set(index, value)

    fun clone() = URegistersStackFrame(registers.clone())
}

class URegistersStack(
    private val ctx: UContext,
    private val stack: Stack<URegistersStackFrame> = Stack<URegistersStackFrame>(),
) : Sequence<URegistersStackFrame>, URegistersStackEvaluator {
    override fun iterator() = stack.iterator()

    fun push(registersCount: Int) = stack.push(URegistersStackFrame(registersCount))

    fun push(registers: Array<UExpr<out USort>?>) = stack.push(URegistersStackFrame(registers))

    fun peek() = stack.peek()

    fun <Sort : USort> readRegister(index: Int, sort: Sort): KExpr<Sort> =
        peek()[index]?.asExpr(sort) ?: ctx.mkRegisterReading(index, sort)

    fun writeRegister(index: Int, value: UExpr<out USort>) {
        peek()[index] = value
    }

    fun pop() = stack.pop()

    fun clone(): URegistersStack {
        val newStack = Stack<URegistersStackFrame>()
        newStack.ensureCapacity(stack.size)
        stack.forEach { newStack.push(it.clone()) }
        return URegistersStack(ctx, newStack)
    }

    override fun <Sort : USort> eval(
        registerIndex: Int,
        sort: Sort,
    ): UExpr<Sort> = readRegister(registerIndex, sort).asExpr(sort)
}
