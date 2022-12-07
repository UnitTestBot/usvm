package org.usvm

import org.ksmt.solver.KModel

interface UStackEvaluator {
    fun eval(registerIndex: Int, sort: USort): UExpr
}

class UStackFrame(registers: Array<UExpr?>) {
    constructor(registersCount: Int):
        this(Array(registersCount) {null})

    var registers: Array<UExpr?> = registers
        protected set;

    fun realloc(registersCount: Int) {
        registers = Array(registersCount) { if (it < registers.size) registers[it] else null }
    }

    operator fun get(index: Int) = registers[index]
    operator fun set(index: Int, value: UExpr) = registers.set(index, value)

    fun clone() = UStackFrame(registers.clone())
}

class UStack(private val ctx: UContext,
             private val stack: java.util.Stack<UStackFrame> = java.util.Stack<UStackFrame>())
    : Iterable<UStackFrame>, UStackEvaluator
{
    override fun iterator() = stack.iterator()

    fun push(registersCount: Int) = stack.push(UStackFrame(registersCount))

    fun push(registers: Array<UExpr?>) = stack.push(UStackFrame(registers))

    fun peek() = stack.peek()

    fun readRegister(index: Int, sort: USort) =
        peek()[index] ?: URegisterReading(ctx, index, sort)

    fun writeRegister(index: Int, value: UExpr) {
        peek()[index] = value
    }

    fun pop() = stack.pop()

    fun clone(): UStack {
        val newStack = java.util.Stack<UStackFrame>()
        newStack.ensureCapacity(stack.size)
        stack.forEach { newStack.push(it.clone()) }
        return UStack(ctx, newStack)
    }

    fun decode(model: KModel): UStackModel = TODO()

    override fun eval(registerIndex: Int, sort: USort): UExpr = readRegister(registerIndex, sort)
}

class UStackModel(private val registers: Array<UExpr?>): UStackEvaluator {
    override fun eval(registerIndex: Int, sort: USort): UExpr = registers[registerIndex]!!
}

data class UCallStackFrame<Method, Statement>(val method: Method, val returnSite: Statement?)

class UCallStack<Method, Statement>(private val stack: java.util.Stack<UCallStackFrame<Method, Statement>>)
    : Iterable<UCallStackFrame<Method, Statement>>
{
    override fun iterator() = stack.iterator()

    fun pop(): Statement? = stack.pop().returnSite
}
