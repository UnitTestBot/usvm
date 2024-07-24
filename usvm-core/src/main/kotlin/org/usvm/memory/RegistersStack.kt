package org.usvm.memory

import io.ksmt.expr.KExpr
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.isTrue
import org.usvm.merging.MergeGuard
import org.usvm.merging.UMergeable
import org.usvm.uctx

object URegisterStackId : UMemoryRegionId<URegisterStackLValue<*>, USort> {
    override val sort: USort
        get() = error("Register stack has no sort")

    override fun emptyRegion(): UMemoryRegion<URegisterStackLValue<*>, USort> = URegistersStack()
}

class URegisterStackLValue<Sort : USort>(
    override val sort: Sort,
    val idx: Int,
) : ULValue<URegisterStackLValue<*>, USort> {
    override val memoryRegionId: UMemoryRegionId<URegisterStackLValue<*>, USort>
        get() = URegisterStackId

    override val key: URegisterStackLValue<Sort> = this
}

interface UReadOnlyRegistersStack : UReadOnlyMemoryRegion<URegisterStackLValue<*>, USort> {
    fun <Sort : USort> readRegister(index: Int, sort: Sort): KExpr<Sort>

    override fun read(key: URegisterStackLValue<*>): UExpr<USort> = readRegister(key.idx, key.sort)
}

class URegistersStack(
    private val frames: MutableList<Array<UExpr<out USort>?>> = mutableListOf(),
) : UReadOnlyRegistersStack, UMemoryRegion<URegisterStackLValue<*>, USort>, UMergeable<URegistersStack, MergeGuard> {
    fun push(registersCount: Int) = frames.add(Array(registersCount) { null })

    fun push(argumentsCount: Int, localsCount: Int) =
        push(argumentsCount + localsCount)

    fun push(arguments: Array<UExpr<out USort>>, localsCount: Int) =
        frames.add(arguments.copyOf(arguments.size + localsCount))

    private fun <Sort : USort> Array<UExpr<out USort>?>?.read(index: Int, sort: Sort): UExpr<Sort> =
        this?.get(index)?.asExpr(sort) ?: sort.uctx.mkRegisterReading(index, sort)

    override fun <Sort : USort> readRegister(index: Int, sort: Sort): UExpr<Sort> =
        frames.lastOrNull().read(index, sort)

    override fun write(
        key: URegisterStackLValue<*>,
        value: UExpr<USort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UMemoryRegion<URegisterStackLValue<*>, USort> {
        check(guard.isTrue) { "Guarded writes are not supported for register" }
        writeRegister(key.idx, value)
        return this
    }

    fun writeRegister(index: Int, value: UExpr<out USort>) {
        frames.last()[index] = value
    }

    fun pop() = frames.removeLast()

    fun clone(): URegistersStack {
        val newStack = ArrayDeque(frames.map { it.clone() })
        return URegistersStack(newStack)
    }

    private fun validate(
        left: List<Array<UExpr<out USort>?>>,
        right: List<Array<UExpr<out USort>?>>,
    ): Boolean {
        if (left.size != right.size) {
            return false
        }
        val zippedStack = left.asSequence().zip(right.asSequence())
        for ((leftFrame, rightFrame) in zippedStack) {
            if (leftFrame.size != rightFrame.size) {
                return false
            }
            val zippedFrame = leftFrame.asSequence().zip(rightFrame.asSequence())
            val exprsOfDifferentSorts = zippedFrame.any { (leftExpr, rightExpr) ->
                leftExpr != null && rightExpr != null && leftExpr.sort != rightExpr.sort
            }
            if (exprsOfDifferentSorts) {
                return false
            }
        }

        return true
    }

    /**
     * Check if this [URegistersStack] can be merged with [other] stack.
     *
     * Verifies, that for each pair of corresponding registers either one of them is `null`,
     * or their sorts match.
     *
     * @return the merged equality constraints.
     */
    override fun mergeWith(other: URegistersStack, by: MergeGuard): URegistersStack? {
        if (!validate(frames, other.frames)) {
            return null
        }
        val clonedStack = clone()
        for ((frameIdx, leftFrame) in clonedStack.frames.withIndex()) {
            val rightFrame = other.frames[frameIdx]
            for ((registerIdx, leftRegister) in leftFrame.withIndex()) {
                val rightRegister = rightFrame[registerIdx]
                val sort = leftRegister?.sort ?: rightRegister?.sort ?: continue
                val result = sort.uctx.mkIte(
                    by.thisConstraint,
                    leftFrame.read(registerIdx, sort),
                    rightFrame.read(registerIdx, sort)
                )
                leftFrame[registerIdx] = result
            }
        }
        return clonedStack
    }
}
