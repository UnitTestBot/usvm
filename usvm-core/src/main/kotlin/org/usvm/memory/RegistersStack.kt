package org.usvm.memory

import io.ksmt.expr.KExpr
import io.ksmt.utils.asExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort

interface URegistersStackEvaluator {
    fun <Sort : USort> readRegister(registerIndex: Int, sort: Sort): UExpr<Sort>
}

class URegistersStackFrame(
    private val registers: Array<UExpr<out USort>?>
) {
    constructor(registersCount: Int) :
        this(Array(registersCount) { null })

    constructor(arguments: Array<UExpr<out USort>>, localsCount: Int) :
        this(arguments.copyOf(arguments.size + localsCount))

    operator fun get(index: Int) = registers[index]
    operator fun set(index: Int, value: UExpr<out USort>) = registers.set(index, value)

    fun clone() = URegistersStackFrame(registers.clone())
}

class URegistersStack(
    private val ctx: UContext,
    private val stack: ArrayDeque<URegistersStackFrame> = ArrayDeque(),
) : URegistersStackEvaluator {
    fun push(registersCount: Int) = stack.add(URegistersStackFrame(registersCount))

    fun push(argumentsCount: Int, localsCount: Int) =
        stack.add(URegistersStackFrame(argumentsCount + localsCount))

    fun push(arguments: Array<UExpr<out USort>>, localsCount: Int) =
        stack.add(URegistersStackFrame(arguments, localsCount))

    override fun <Sort : USort> readRegister(registerIndex: Int, sort: Sort): KExpr<Sort> =
        stack.last()[registerIndex]?.asExpr(sort) ?: ctx.mkRegisterReading(registerIndex, sort)

    fun writeRegister(index: Int, value: UExpr<out USort>) {
        stack.last()[index] = value
    }

    fun pop() = stack.removeLast()

    fun clone(): URegistersStack {
        val newStack = ArrayDeque(stack.map { it.clone() })
        return URegistersStack(ctx, newStack)
    }
}
