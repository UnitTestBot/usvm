package org.usvm

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

interface UPathCondition: Iterable<UBoolExpr> {
    val isFalse: Boolean
    fun add(constraint: UBoolExpr): UPathCondition
}

class UPathConstraintsSet(private val ctx: UContext,
                          private val constraints: PersistentSet<UBoolExpr> = persistentSetOf()
)
    : UPathCondition
{
    fun contradiction() =
        UPathConstraintsSet(ctx, persistentSetOf(ctx.mkFalse()))

    override val isFalse: Boolean
        get() = constraints.size == 1 && constraints.first() is UFalse

    override fun add(constraint: UBoolExpr): UPathCondition {
        val notConstraint = constraint.ctx.mkNot(constraint)
        if (constraints.contains(notConstraint))
            return contradiction()
        return UPathConstraintsSet(ctx, constraints.add(constraint))
    }

    override fun iterator(): Iterator<UBoolExpr> = constraints.iterator()
}
