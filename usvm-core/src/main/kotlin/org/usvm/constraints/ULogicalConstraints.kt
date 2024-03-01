package org.usvm.constraints

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentSetOf
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.algorithms.separate
import org.usvm.isFalse
import org.usvm.merging.MutableMergeGuard
import org.usvm.merging.UMergeable

class ULogicalConstraints private constructor(
    private var constraints: PersistentSet<UBoolExpr>,
) : Set<UBoolExpr>, UMergeable<ULogicalConstraints, MutableMergeGuard> {
    operator fun plusAssign(expr: UBoolExpr) {
        constraints = constraints.add(expr)
    }

    fun clone(): ULogicalConstraints = ULogicalConstraints(constraints)
    override val size: Int
        get() = constraints.size

    override fun isEmpty(): Boolean = constraints.isEmpty()

    override fun iterator(): Iterator<UBoolExpr> = constraints.iterator()

    override fun containsAll(elements: Collection<UBoolExpr>): Boolean = constraints.containsAll(elements)

    override fun contains(element: UBoolExpr): Boolean = constraints.contains(element)

    val isContradicting: Boolean
        get() = constraints.any(UBoolExpr::isFalse)

    fun contradiction(ctx: UContext<*>) {
        constraints = persistentHashSetOf(ctx.falseExpr)
    }

    /**
     * Check if this [ULogicalConstraints] can be merged with [other] logical constraints.
     *
     * TODO: there are no heuristics on merged constraints complexity compared to the former ones
     *
     * @return the logical constraints.
     */
    override fun mergeWith(other: ULogicalConstraints, by: MutableMergeGuard): ULogicalConstraints {
        val (overlap, uniqueThis, uniqueOther) = constraints.separate(other.constraints)
        by.appendThis(uniqueThis.asSequence())
        by.appendOther(uniqueOther.asSequence())
        return ULogicalConstraints(overlap)
    }

    companion object {
        fun empty() = ULogicalConstraints(persistentHashSetOf())
    }
}