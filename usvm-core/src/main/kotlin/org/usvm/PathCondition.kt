package org.usvm

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

interface UPathCondition: Sequence<UBoolExpr> {
    val isFalse: Boolean
    fun add(constraint: UBoolExpr): UPathCondition
}

class UPathConstraintsSet(
    private val constraints: PersistentSet<UBoolExpr> = persistentSetOf()
) : UPathCondition
{
    fun contradiction(ctx: UContext) =
        UPathConstraintsSet(persistentSetOf(ctx.mkFalse()))

    override val isFalse: Boolean
        get() = constraints.size == 1 && constraints.first() is UFalse

    override fun add(constraint: UBoolExpr): UPathCondition {
        val notConstraint = constraint.uctx.mkNot(constraint)
        if (constraints.contains(notConstraint))
            return contradiction(constraint.uctx)
        return UPathConstraintsSet(constraints.add(constraint))
    }

    override fun iterator(): Iterator<UBoolExpr> = constraints.iterator()
}
