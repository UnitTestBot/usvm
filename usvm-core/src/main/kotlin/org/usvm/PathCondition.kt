package org.usvm

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

interface UPathCondition : Collection<UBoolExpr> {
    val isFalse: Boolean
    operator fun plus(constraint: UBoolExpr): UPathCondition
}

class UPathConstraintsSet(
    private val constraints: PersistentSet<UBoolExpr> = persistentSetOf(),
) : Collection<UBoolExpr> by constraints, UPathCondition {
    constructor(constraint: UBoolExpr) : this(persistentSetOf(constraint))

    override val isFalse: Boolean
        get() = constraints.size == 1 && constraints.first() is UFalse

    override operator fun plus(constraint: UBoolExpr): UPathCondition {
        val ctx = constraint.uctx
        val notConstraint = ctx.mkNot(constraint)
        if (constraints.contains(notConstraint)) {
            return contradiction(ctx)
        }
        return UPathConstraintsSet(constraints.add(constraint))
    }

    companion object {
        fun contradiction(ctx: UContext) =
            UPathConstraintsSet(persistentSetOf(ctx.mkFalse()))
    }
}
