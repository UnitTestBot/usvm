package org.usvm.constraints

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import org.usvm.UAndExpr
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UEqExpr
import org.usvm.UExpr
import org.usvm.UFalse
import org.usvm.UHeapRef
import org.usvm.UIsSubtypeExpr
import org.usvm.UIsSupertypeExpr
import org.usvm.UNotExpr
import org.usvm.UOrExpr
import org.usvm.USizeSort
import org.usvm.isStaticInitializedConcreteHeapRef
import org.usvm.isSymbolicHeapRef
import org.usvm.uctx

/**
 * Mutable representation of path constraints.
 */
open class UPathConstraints<Type, Context : UContext> private constructor(
    val ctx: Context,
    logicalConstraints: PersistentSet<UBoolExpr> = persistentSetOf(),
    /**
     * Specially represented equalities and disequalities between objects, used in various part of constraints management.
     */
    val equalityConstraints: UEqualityConstraints = UEqualityConstraints(ctx),
    /**
     * Constraints solved by type solver.
     */
    val typeConstraints: UTypeConstraints<Type> = UTypeConstraints(
        ctx.typeSystem(),
        equalityConstraints
    ),
    /**
     * Specially represented numeric constraints (e.g. >, <, >=, ...).
     */
    val numericConstraints: UNumericConstraints<USizeSort> = UNumericConstraints(ctx, sort = ctx.sizeSort)
) {
    init {
        equalityConstraints.setTypesCheck(typeConstraints::isStaticRefAssignableToSymbolic)
    }
    /**
     * Constraints solved by SMT solver.
     */
    var logicalConstraints: PersistentSet<UBoolExpr> = logicalConstraints
        private set

    constructor(ctx: Context) : this(ctx, persistentSetOf())

    open val isFalse: Boolean
        get() = equalityConstraints.isContradicting ||
                typeConstraints.isContradicting ||
                numericConstraints.isContradicting ||
                logicalConstraints.singleOrNull() is UFalse

    @Suppress("UNCHECKED_CAST")
    open operator fun plusAssign(constraint: UBoolExpr): Unit =
        with(constraint.uctx) {
            when {
                constraint == falseExpr -> contradiction(this)

                constraint == trueExpr || constraint in logicalConstraints -> {}

                numericConstraints.isNumericConstraint(constraint) ->
                    numericConstraints.addNumericConstraint(constraint)

                constraint is UEqExpr<*> && arePossiblyEqualReferences(constraint.lhs, constraint.rhs) ->
                    equalityConstraints.makeEqual(constraint.lhs as UHeapRef, constraint.rhs as UHeapRef)

                constraint is UIsSubtypeExpr<*> -> {
                    typeConstraints.addSupertype(constraint.ref, constraint.supertype as Type)
                }

                constraint is UIsSupertypeExpr<*> -> {
                    typeConstraints.addSubtype(constraint.ref, constraint.subtype as Type)
                }

                constraint is UAndExpr -> constraint.args.forEach(::plusAssign)

                constraint is UNotExpr -> {
                    val notConstraint = constraint.arg
                    when {
                        notConstraint is UEqExpr<*> && arePossiblyEqualReferences(notConstraint.lhs, notConstraint.rhs) -> {
                            require(notConstraint.rhs.sort == addressSort)
                            equalityConstraints.makeNonEqual(
                                notConstraint.lhs as UHeapRef,
                                notConstraint.rhs as UHeapRef
                            )
                        }

                        notConstraint is UIsSubtypeExpr<*> -> typeConstraints.excludeSupertype(
                            notConstraint.ref,
                            notConstraint.supertype as Type
                        )

                        notConstraint is UIsSupertypeExpr<*> -> typeConstraints.excludeSubtype(
                            notConstraint.ref,
                            notConstraint.subtype as Type
                        )

                        numericConstraints.isNumericConstraint(notConstraint) ->
                            numericConstraints.addNegatedNumericConstraint(notConstraint)

                        notConstraint in logicalConstraints -> contradiction(ctx)

                        notConstraint is UOrExpr -> notConstraint.args.forEach { plusAssign(ctx.mkNot(it)) }

                        else -> logicalConstraints = logicalConstraints.add(constraint)
                    }
                }

                logicalConstraints.contains(constraint.not()) -> contradiction(ctx)

                else -> logicalConstraints = logicalConstraints.add(constraint)
            }
        }

    open fun clone(): UPathConstraints<Type, Context> {
        val clonedEqualityConstraints = equalityConstraints.clone()
        val clonedTypeConstraints = typeConstraints.clone(clonedEqualityConstraints)
        val clonedNumericConstraints = numericConstraints.clone()
        return UPathConstraints(
            ctx = ctx,
            logicalConstraints = logicalConstraints,
            equalityConstraints = clonedEqualityConstraints,
            typeConstraints = clonedTypeConstraints,
            numericConstraints = clonedNumericConstraints
        )
    }

    protected fun contradiction(ctx: UContext) {
        logicalConstraints = persistentSetOf(ctx.falseExpr)
    }

    // Only symbolic references or a pair of symbolic-static could be equal by reference
    private fun arePossiblyEqualReferences(ref1: UExpr<*>, ref2: UExpr<*>): Boolean {
        val isFirstSymbolic = isSymbolicHeapRef(ref1)
        val isSecondSymbolic = isSymbolicHeapRef(ref2)

        val isFirstStaticallyInitialized = isStaticInitializedConcreteHeapRef(ref1)
        val isSecondStaticallyInitialized = isStaticInitializedConcreteHeapRef(ref2)

        return when {
            isFirstSymbolic && isSecondSymbolic -> true
            isFirstSymbolic && isSecondStaticallyInitialized -> true
            isFirstStaticallyInitialized && isSecondSymbolic -> true
            else -> false
        }
    }
}
