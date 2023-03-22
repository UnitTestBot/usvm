package org.usvm

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

interface UPathCondition : Sequence<UBoolExpr> {
    val isFalse: Boolean
    fun add(constraint: UBoolExpr): UPathCondition
}

class UPathConstraintsSet(
    private val ctx: UContext,
    private val constraints: PersistentSet<UBoolExpr> = persistentSetOf()
) : UPathCondition {
    fun contradiction() = UPathConstraintsSet(ctx, persistentSetOf(ctx.mkFalse()))

    override val isFalse: Boolean
        get() = constraints.singleOrNull() is UFalse

    override fun add(constraint: UBoolExpr): UPathCondition {
        val notConstraint = constraint.ctx.mkNot(constraint)
        if (notConstraint in constraints) {
            return contradiction()
        }

        return UPathConstraintsSet(ctx, constraints.add(constraint))
    }

    override fun iterator(): Iterator<UBoolExpr> = constraints.iterator()
}
