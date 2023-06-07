package org.usvm.constraints

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import org.usvm.UAndExpr
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UEqExpr
import org.usvm.UFalse
import org.usvm.UHeapRef
import org.usvm.UIsExpr
import org.usvm.UNotExpr
import org.usvm.UOrExpr
import org.usvm.uctx
import org.usvm.isSymbolicHeapRef

/**
 * Mutable representation of path constraints.
 */
open class UPathConstraints<Type> private constructor(
    val ctx: UContext,
    logicalConstraints: PersistentSet<UBoolExpr> = persistentSetOf(),
    /**
     * Specially represented equalities and disequalities between objects, used in various part of constraints management.
     */
    val equalityConstraints: UEqualityConstraints = UEqualityConstraints(ctx),
    /**
     * Constraints solved by type solver.
     */
    val typeConstraints: UTypeConstraints<Type> = UTypeConstraints(ctx.typeSystem(), equalityConstraints)
) {
    /**
     * Constraints solved by SMT solver.
     */
    var logicalConstraints: PersistentSet<UBoolExpr> = logicalConstraints
        private set

    constructor(ctx: UContext) : this(ctx, persistentSetOf())

    open val isFalse: Boolean
        get() = equalityConstraints.isContradiction ||
                typeConstraints.isContradiction ||
                logicalConstraints.singleOrNull() is UFalse

    @Suppress("UNCHECKED_CAST")
    open operator fun plusAssign(constraint: UBoolExpr): Unit =
        with(constraint.uctx) {
            when {
                constraint == falseExpr -> contradiction(this)

                constraint == trueExpr || constraint in logicalConstraints -> {}

                constraint is UEqExpr<*> && isSymbolicHeapRef(constraint.lhs) && isSymbolicHeapRef(constraint.rhs) -> {
                    equalityConstraints.addReferenceEquality(constraint.lhs as UHeapRef, constraint.rhs as UHeapRef)
                }

                constraint is UIsExpr<*> -> typeConstraints.cast(constraint.ref, constraint.type as Type)

                constraint is UAndExpr -> constraint.args.forEach(::plusAssign)

                constraint is UNotExpr -> {
                    val notConstraint = constraint.arg
                    when {
                        notConstraint is UEqExpr<*> && isSymbolicHeapRef(notConstraint.lhs) && isSymbolicHeapRef(notConstraint.rhs) -> {
                            require(notConstraint.rhs.sort == addressSort)
                            equalityConstraints.addReferenceDisequality(
                                notConstraint.lhs as UHeapRef,
                                notConstraint.rhs as UHeapRef
                            )
                        }

                        notConstraint in logicalConstraints -> contradiction(ctx)

                        notConstraint is UOrExpr -> notConstraint.args.forEach { plusAssign(ctx.mkNot(it)) }

                        else -> logicalConstraints = logicalConstraints.add(constraint)
                    }
                }

                logicalConstraints.contains(constraint.not()) -> contradiction(ctx)

                else -> logicalConstraints = logicalConstraints.add(constraint)
            }
        }

    open fun clone(): UPathConstraints<Type> {
        val clonedEqualityConstraints = equalityConstraints.clone()
        val clonedTypeConstraints = typeConstraints.clone(clonedEqualityConstraints)
        return UPathConstraints(ctx, logicalConstraints, clonedEqualityConstraints, clonedTypeConstraints)
    }

    protected fun contradiction(ctx: UContext) {
        logicalConstraints = persistentSetOf(ctx.falseExpr)
    }
}
