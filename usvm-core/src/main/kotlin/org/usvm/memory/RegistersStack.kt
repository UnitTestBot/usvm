package org.usvm.memory

import io.ksmt.expr.KExpr
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.isTrue
import org.usvm.uctx

object URegisterStackId : UMemoryRegionId<URegisterStackRef<*>, USort> {
    override val sort: USort
        get() = error("Register stack has not sort")

    override fun emptyRegion(): UMemoryRegion<URegisterStackRef<*>, USort> = URegistersStack()
}

class URegisterStackRef<Sort: USort>(
    override val sort: Sort,
    val idx: Int
) : ULValue<URegisterStackRef<*>, USort> {
    override val memoryRegionId: UMemoryRegionId<URegisterStackRef<*>, USort>
        get() = URegisterStackId

    override val key: URegisterStackRef<Sort> = this
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

interface UReadOnlyRegistersStack: UReadOnlyMemoryRegion<URegisterStackRef<*>, USort> {
    fun <Sort : USort> readRegister(index: Int, sort: Sort): KExpr<Sort>

    override fun read(key: URegisterStackRef<*>): UExpr<USort> = readRegister(key.idx, key.sort)
}

class URegistersStack(
    private val stack: MutableList<URegistersStackFrame> = mutableListOf(),
) : UReadOnlyRegistersStack, UMemoryRegion<URegisterStackRef<*>, USort> {
    fun push(registersCount: Int) = stack.add(URegistersStackFrame(registersCount))

    fun push(argumentsCount: Int, localsCount: Int) =
        stack.add(URegistersStackFrame(argumentsCount + localsCount))

    fun push(arguments: Array<UExpr<out USort>>, localsCount: Int) =
        stack.add(URegistersStackFrame(arguments, localsCount))

    override fun <Sort : USort> readRegister(index: Int, sort: Sort): KExpr<Sort> =
        stack.lastOrNull()?.get(index)?.asExpr(sort) ?: sort.uctx.mkRegisterReading(index, sort)

    override fun write(
        key: URegisterStackRef<*>,
        value: UExpr<USort>,
        guard: UBoolExpr
    ): UMemoryRegion<URegisterStackRef<*>, USort> {
        check(guard.isTrue) { "Guarded writes are not supported for register" }
        writeRegister(key.idx, value)
        return this
    }

    fun writeRegister(index: Int, value: UExpr<out USort>) {
        stack.last()[index] = value
    }

    fun pop() = stack.removeLast()

    fun clone(): URegistersStack {
        val newStack = ArrayDeque(stack.map { it.clone() })
        return URegistersStack(newStack)
    }
}
