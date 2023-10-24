package org.usvm.constraints

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import org.usvm.UAndExpr
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UEqExpr
import org.usvm.UFalse
import org.usvm.UIsSubtypeExpr
import org.usvm.UIsSupertypeExpr
import org.usvm.UNotExpr
import org.usvm.UOrExpr
import org.usvm.USymbolicHeapRef
import org.usvm.isStaticHeapRef
import org.usvm.isSymbolicHeapRef
import org.usvm.uctx

/**
 * Mutable representation of path constraints.
 */
open class UPathConstraints<Type> private constructor(
    private val ctx: UContext<*>,
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
    val numericConstraints: UNumericConstraints<UBv32Sort> = UNumericConstraints(ctx, sort = ctx.bv32Sort)
) {
    init {
        // Use the information from the type constraints to check whether any static ref is assignable to any symbolic ref
        equalityConstraints.setTypesCheck(typeConstraints::canStaticRefBeEqualToSymbolic)
    }

    /**
     * Constraints solved by SMT solver.
     */
    var logicalConstraints: PersistentSet<UBoolExpr> = logicalConstraints
        private set

    constructor(ctx: UContext<*>) : this(ctx, persistentSetOf())

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

                constraint is UEqExpr<*> && isSymbolicHeapRef(constraint.lhs) && isSymbolicHeapRef(constraint.rhs) ->
                    equalityConstraints.makeEqual(constraint.lhs as USymbolicHeapRef, constraint.rhs as USymbolicHeapRef)

                constraint is UEqExpr<*> && isSymbolicHeapRef(constraint.lhs) && isStaticHeapRef(constraint.rhs) ->
                    equalityConstraints.makeEqual(constraint.lhs as USymbolicHeapRef, constraint.rhs as UConcreteHeapRef)

                constraint is UEqExpr<*> && isStaticHeapRef(constraint.lhs) && isSymbolicHeapRef(constraint.rhs) ->
                    equalityConstraints.makeEqual(constraint.rhs as USymbolicHeapRef, constraint.lhs as UConcreteHeapRef)

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
                        notConstraint is UEqExpr<*> && isSymbolicHeapRef(notConstraint.lhs) && isSymbolicHeapRef(notConstraint.rhs) ->
                            equalityConstraints.makeNonEqual(notConstraint.lhs as USymbolicHeapRef, notConstraint.rhs as USymbolicHeapRef)

                        notConstraint is UEqExpr<*> && isSymbolicHeapRef(notConstraint.lhs) && isStaticHeapRef(notConstraint.rhs) ->
                            equalityConstraints.makeNonEqual(notConstraint.lhs as USymbolicHeapRef, notConstraint.rhs as UConcreteHeapRef)

                        notConstraint is UEqExpr<*> && isStaticHeapRef(notConstraint.lhs) && isSymbolicHeapRef(notConstraint.rhs) ->
                            equalityConstraints.makeNonEqual(notConstraint.rhs as USymbolicHeapRef, notConstraint.lhs as UConcreteHeapRef)

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

    /**
     * Returns lazy sequence of all constraints asserted into this instance.
     */
    internal open fun constraintsSequence(): Sequence<UBoolExpr> =
        equalityConstraints.constraints() +
        typeConstraints.constraints() +
        numericConstraints.constraints() +
        logicalConstraints.asSequence()

    /**
     * Iterates through the constraints of [other], maps every constraint using [mapper], adds the result into this instance.
     */
    internal inline fun mappedUnion(other: UPathConstraints<Type>, mapper: (UBoolExpr) -> UBoolExpr) {
        if (isFalse)
            return
        for (constraint in other.constraintsSequence()) {
            val mappedConstraint = mapper(constraint)
            plusAssign(mappedConstraint)
            if (isFalse)
                return
        }
    }

    open fun clone(): UPathConstraints<Type> {
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

    protected fun contradiction(ctx: UContext<*>) {
        logicalConstraints = persistentSetOf(ctx.falseExpr)
    }
}
