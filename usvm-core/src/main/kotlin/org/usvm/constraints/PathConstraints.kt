package org.usvm.constraints

import org.usvm.UAndExpr
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UEqExpr
import org.usvm.UIsSubtypeExpr
import org.usvm.UIsSupertypeExpr
import org.usvm.UNotExpr
import org.usvm.UOrExpr
import org.usvm.USymbolicHeapRef
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.isStaticHeapRef
import org.usvm.isSymbolicHeapRef
import org.usvm.merging.MutableMergeGuard
import org.usvm.merging.UOwnedMergeable
import org.usvm.solver.UExprTranslator
import org.usvm.uctx

/**
 * Mutable representation of path constraints.
 */
open class UPathConstraints<Type>(
    protected val ctx: UContext<*>,
    protected open var ownership: MutabilityOwnership,
    protected val logicalConstraints: ULogicalConstraints = ULogicalConstraints.empty(),
    /**
     * Specially represented equalities and disequalities between objects, used in various part of constraints management.
     */
    protected val equalityConstraints: UEqualityConstraints = UEqualityConstraints(ctx, ownership),
    /**
     * Constraints solved by type solver.
     */
    val typeConstraints: UTypeConstraints<Type> = UTypeConstraints(
        ownership,
        ctx.typeSystem(),
        equalityConstraints
    ),
    /**
     * Specially represented numeric constraints (e.g. >, <, >=, ...).
     */
    protected val numericConstraints: UNumericConstraints<UBv32Sort> =
        UNumericConstraints(ctx, sort = ctx.bv32Sort, ownership)
) : UOwnedMergeable<UPathConstraints<Type>, MutableMergeGuard> {
    init {
        // Use the information from the type constraints to check whether any static ref is assignable to any symbolic ref
        equalityConstraints.setTypesCheck(typeConstraints::canStaticRefBeEqualToSymbolic)
    }

    /**
     * Recursively changes ownership for all nested data structures that use persistent maps.
     */
    fun changeOwnership(ownership: MutabilityOwnership) {
        this.ownership = ownership
        numericConstraints.changeOwnership(ownership)
        equalityConstraints.changeOwnership(ownership)
        typeConstraints.changeOwnership(ownership)
    }

    /**
     * Constraints solved by SMT solver.
     */
    val softConstraintsSourceSequence: Sequence<UBoolExpr>
        get() = logicalConstraints.asSequence() + numericConstraints.constraints()

    constructor(ctx: UContext<*>, ownership: MutabilityOwnership) : this(ctx, ownership, ULogicalConstraints.empty())

    val isFalse: Boolean
        get() = equalityConstraints.isContradicting ||
            typeConstraints.isContradicting ||
            numericConstraints.isContradicting ||
            logicalConstraints.isContradicting

    // TODO: refactor
    fun constraints(translator: UExprTranslator<Type, *>): Sequence<UBoolExpr> {
        if (isFalse) {
            return sequenceOf(ctx.falseExpr)
        }
        return logicalConstraints.asSequence().map(translator::translate) +
            equalityConstraints.constraints(translator) +
            numericConstraints.constraints(translator) +
            typeConstraints.constraints(translator)
    }

    @Suppress("UNCHECKED_CAST")
    operator fun plusAssign(constraint: UBoolExpr): Unit =
        with(constraint.uctx) {
            when {
                constraint == falseExpr -> contradiction(this)

                constraint == trueExpr || constraint in logicalConstraints -> {}

                numericConstraints.isNumericConstraint(constraint) ->
                    numericConstraints.addNumericConstraint(constraint)

                constraint is UEqExpr<*> && isSymbolicHeapRef(constraint.lhs) && isSymbolicHeapRef(constraint.rhs) ->
                    equalityConstraints.makeEqual(
                        constraint.lhs as USymbolicHeapRef,
                        constraint.rhs as USymbolicHeapRef
                    )

                constraint is UEqExpr<*> && isSymbolicHeapRef(constraint.lhs) && isStaticHeapRef(constraint.rhs) ->
                    equalityConstraints.makeEqual(
                        constraint.lhs as USymbolicHeapRef,
                        constraint.rhs as UConcreteHeapRef
                    )

                constraint is UEqExpr<*> && isStaticHeapRef(constraint.lhs) && isSymbolicHeapRef(constraint.rhs) ->
                    equalityConstraints.makeEqual(
                        constraint.rhs as USymbolicHeapRef,
                        constraint.lhs as UConcreteHeapRef
                    )

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
                        notConstraint is UEqExpr<*> &&
                            isSymbolicHeapRef(notConstraint.lhs) && isSymbolicHeapRef(notConstraint.rhs) ->
                            equalityConstraints.makeNonEqual(
                                notConstraint.lhs as USymbolicHeapRef,
                                notConstraint.rhs as USymbolicHeapRef
                            )

                        notConstraint is UEqExpr<*> &&
                            isSymbolicHeapRef(notConstraint.lhs) && isStaticHeapRef(notConstraint.rhs) ->
                            equalityConstraints.makeNonEqual(
                                notConstraint.lhs as USymbolicHeapRef,
                                notConstraint.rhs as UConcreteHeapRef
                            )

                        notConstraint is UEqExpr<*> &&
                            isStaticHeapRef(notConstraint.lhs) && isSymbolicHeapRef(notConstraint.rhs) ->
                            equalityConstraints.makeNonEqual(
                                notConstraint.rhs as USymbolicHeapRef,
                                notConstraint.lhs as UConcreteHeapRef
                            )

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

                        else -> logicalConstraints.add(constraint, ownership)
                    }
                }

                logicalConstraints.contains(constraint.not()) -> contradiction(ctx)

                else -> logicalConstraints.add(constraint, ownership)
            }
        }

    open fun clone(
        thisOwnership: MutabilityOwnership = MutabilityOwnership(),
        cloneOwnership: MutabilityOwnership = MutabilityOwnership(), // ownerships must be fresh new because of plus assign operations
    ): UPathConstraints<Type> {
        val clonedLogicalConstraints = logicalConstraints.clone()
        val clonedEqualityConstraints = equalityConstraints.clone(thisOwnership, cloneOwnership)
        val clonedTypeConstraints = typeConstraints.clone(clonedEqualityConstraints, thisOwnership, cloneOwnership)
        val clonedNumericConstraints = numericConstraints.clone(thisOwnership, cloneOwnership)
        this.ownership = thisOwnership
        return UPathConstraints(
            ctx = ctx,
            ownership = cloneOwnership,
            logicalConstraints = clonedLogicalConstraints,
            equalityConstraints = clonedEqualityConstraints,
            typeConstraints = clonedTypeConstraints,
            numericConstraints = clonedNumericConstraints,
        )
    }

    private fun contradiction(ctx: UContext<*>) {
        logicalConstraints.contradiction(ctx, ownership)
    }

    /**
     * Check if this [UPathConstraints] can be merged with [other] path constraints. Puts merge constraints into merge
     * guard [by].
     *
     * TODO: now the only supported case is:
     *  - logical constraints are always merged
     *  - equality constraints are merged only if their contents are equal
     *  - type constraints are merged only if their contents are equal, except types for concrete refs
     *  - numeric constraints are always merged
     *
     * TODO: there are no heuristics on merged constraints complexity compared to the former ones
     *
     * @return the merged path constraints.
     */
    override fun mergeWith(
        other: UPathConstraints<Type>,
        by: MutableMergeGuard,
        thisOwnership: MutabilityOwnership,
        otherOwnership: MutabilityOwnership,
        mergedOwnership: MutabilityOwnership,
    ): UPathConstraints<Type>? {
        // TODO: elaborate on some merge parameters here
        val mergedLogicalConstraints =
            logicalConstraints.mergeWith(other.logicalConstraints, by, thisOwnership, otherOwnership, mergedOwnership)
        val mergedEqualityConstraints =
            equalityConstraints.mergeWith(other.equalityConstraints, by, thisOwnership, otherOwnership, mergedOwnership)
                ?: return null
        val mergedTypeConstraints = typeConstraints
            .clone(mergedEqualityConstraints, thisOwnership, otherOwnership)
            .mergeWith(other.typeConstraints, by, thisOwnership, otherOwnership, mergedOwnership) ?: return null
        val mergedNumericConstraints =
            numericConstraints.mergeWith(other.numericConstraints, by, thisOwnership, otherOwnership, mergedOwnership)

        this.changeOwnership(thisOwnership)
        other.changeOwnership(otherOwnership)
        return UPathConstraints(
            ctx,
            mergedOwnership,
            mergedLogicalConstraints,
            mergedEqualityConstraints,
            mergedTypeConstraints,
            mergedNumericConstraints,
        )
    }
}
