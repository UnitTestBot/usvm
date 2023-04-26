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
        get() = constraints.singleOrNull() is UFalse

    override operator fun plus(constraint: UBoolExpr): UPathCondition =
        with(constraint.uctx) {
            when {
                constraint == falseExpr || constraint.not() in constraints -> contradiction(this)

                constraint == trueExpr || constraint in constraints -> this@UPathConstraintsSet

                else -> UPathConstraintsSet(constraints.add(constraint))
            }
        }

    companion object {
        fun contradiction(ctx: UContext) = UPathConstraintsSet(ctx.falseExpr)
    }
}
