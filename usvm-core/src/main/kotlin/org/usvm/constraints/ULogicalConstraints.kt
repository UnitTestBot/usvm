package org.usvm.constraints

import io.ksmt.expr.KExpr
import org.usvm.collections.immutable.persistentHashSetOf
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UContext
import org.usvm.algorithms.separate
import org.usvm.collections.immutable.containsAll
import org.usvm.collections.immutable.implementations.immutableSet.UPersistentHashSet
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.isEmpty
import org.usvm.isFalse
import org.usvm.merging.MutableMergeGuard
import org.usvm.merging.UOwnedMergeable

class ULogicalConstraints private constructor(
    private var constraints: UPersistentHashSet<UBoolExpr>,
) : Set<UBoolExpr>, UOwnedMergeable<ULogicalConstraints, MutableMergeGuard> {
    fun add(expr: UBoolExpr, ownership: MutabilityOwnership) {
        constraints = constraints.add(expr, ownership)
    }

    fun clone(): ULogicalConstraints = ULogicalConstraints(constraints)
    override val size: Int
        get() = constraints.calculateSize()

    override fun isEmpty(): Boolean = constraints.isEmpty()

    override fun iterator(): Iterator<UBoolExpr> = constraints.iterator()

    override fun containsAll(elements: Collection<UBoolExpr>): Boolean = constraints.containsAll(elements)

    override fun contains(element: UBoolExpr): Boolean = constraints.contains(element)

    val isContradicting: Boolean
        get() = constraints.any(UBoolExpr::isFalse)

    fun contradiction(ctx: UContext<*>, ownership: MutabilityOwnership) {
        constraints = persistentHashSetOf<UBoolExpr>().add(ctx.falseExpr, ownership)
    }

    /**
     * Check if this [ULogicalConstraints] can be merged with [other] logical constraints.
     *
     * TODO: there are no heuristics on merged constraints complexity compared to the former ones
     *
     * @return the logical constraints.
     */
    override fun mergeWith(
        other: ULogicalConstraints,
        by: MutableMergeGuard,
        thisOwnership: MutabilityOwnership,
        otherOwnership: MutabilityOwnership,
        mergedOwnership: MutabilityOwnership
    ): ULogicalConstraints {
        val (overlap, uniqueThis, uniqueOther) = constraints.separate(other.constraints, mergedOwnership)
        by.appendThis(uniqueThis.asSequence())
        by.appendOther(uniqueOther.asSequence())
        return ULogicalConstraints(overlap)
    }

    companion object {
        fun empty() = ULogicalConstraints(persistentHashSetOf())
    }
}
