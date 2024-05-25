package org.usvm.constraints

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.PathsTrieNode
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
import org.usvm.isFalse
import org.usvm.isStaticHeapRef
import org.usvm.isSymbolicHeapRef
import org.usvm.isTrue
import org.usvm.solver.UExprTranslator
import org.usvm.uctx

/**
 * Mutable representation of path constraints.
 */
open class UPathConstraints<Type> private constructor(
    private val ctx: UContext<*>,
    val logicalConstraints: LogicalConstraints,
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

    constructor(ctx: UContext<*>) : this(ctx, LogicalConstraints())

    open val isFalse: Boolean
        get() = equalityConstraints.isContradicting ||
                typeConstraints.isContradicting ||
                numericConstraints.isContradicting ||
                logicalConstraints.isContradicting

    fun unsatCore(): PathConstraintsUnsatCore =
        logicalConstraints.generateUnsatCore()
            ?: equalityConstraints.generateUnsatCore()
            ?: PathConstraintsUnsatCore("No core", emptyList())

    operator fun plusAssign(constraint: UBoolExpr): Unit =
        addConstraint(constraint, UnknownConstraintSource)

    fun addConstraint(constraint: UBoolExpr, statement: PathsTrieNode<*, *>): Unit =
        addConstraint(constraint, LocationConstraintSource(statement))

    @Suppress("UNCHECKED_CAST")
    open fun addConstraint(constraint: UBoolExpr, source: ConstraintSource): Unit =
        with(constraint.uctx) {
            when {
                constraint == falseExpr || constraint == trueExpr ->
                    logicalConstraints.addConstraint(constraint, source)

//                numericConstraints.isNumericConstraint(constraint) ->
//                    numericConstraints.addNumericConstraint(constraint)

                constraint is UEqExpr<*> && isSymbolicHeapRef(constraint.lhs) && isSymbolicHeapRef(constraint.rhs) ->
                    equalityConstraints.makeEqual(constraint.lhs as USymbolicHeapRef, constraint.rhs as USymbolicHeapRef, source)

                constraint is UEqExpr<*> && isSymbolicHeapRef(constraint.lhs) && isStaticHeapRef(constraint.rhs) ->
                    equalityConstraints.makeEqual(constraint.lhs as USymbolicHeapRef, constraint.rhs as UConcreteHeapRef, source)

                constraint is UEqExpr<*> && isStaticHeapRef(constraint.lhs) && isSymbolicHeapRef(constraint.rhs) ->
                    equalityConstraints.makeEqual(constraint.rhs as USymbolicHeapRef, constraint.lhs as UConcreteHeapRef, source)

                constraint is UIsSubtypeExpr<*> -> {
                    typeConstraints.addSupertype(constraint.ref, constraint.supertype as Type)
                }

                constraint is UIsSupertypeExpr<*> -> {
                    typeConstraints.addSubtype(constraint.ref, constraint.subtype as Type)
                }

                constraint is UAndExpr -> constraint.args.forEach { addConstraint(it, source) }

                constraint is UNotExpr -> {
                    val notConstraint = constraint.arg
                    when {
                        notConstraint is UEqExpr<*> && isSymbolicHeapRef(notConstraint.lhs) && isSymbolicHeapRef(notConstraint.rhs) ->
                            equalityConstraints.makeNonEqual(notConstraint.lhs as USymbolicHeapRef, notConstraint.rhs as USymbolicHeapRef, source)

                        notConstraint is UEqExpr<*> && isSymbolicHeapRef(notConstraint.lhs) && isStaticHeapRef(notConstraint.rhs) ->
                            equalityConstraints.makeNonEqual(notConstraint.lhs as USymbolicHeapRef, notConstraint.rhs as UConcreteHeapRef, source)

                        notConstraint is UEqExpr<*> && isStaticHeapRef(notConstraint.lhs) && isSymbolicHeapRef(notConstraint.rhs) ->
                            equalityConstraints.makeNonEqual(notConstraint.rhs as USymbolicHeapRef, notConstraint.lhs as UConcreteHeapRef, source)

                        notConstraint is UIsSubtypeExpr<*> -> typeConstraints.excludeSupertype(
                            notConstraint.ref,
                            notConstraint.supertype as Type
                        )

                        notConstraint is UIsSupertypeExpr<*> -> typeConstraints.excludeSubtype(
                            notConstraint.ref,
                            notConstraint.subtype as Type
                        )

//                        numericConstraints.isNumericConstraint(notConstraint) ->
//                            numericConstraints.addNegatedNumericConstraint(notConstraint)

                        notConstraint is UOrExpr -> notConstraint.args.forEach {
                            addConstraint(ctx.mkNot(it), source)
                        }

                        else -> logicalConstraints.addConstraint(constraint, source)
                    }
                }

                else -> logicalConstraints.addConstraint(constraint, source)
            }
        }

    open fun clone(): UPathConstraints<Type> {
        val clonedLogicalConstraints = logicalConstraints.clone()
        val clonedEqualityConstraints = equalityConstraints.clone()
        val clonedTypeConstraints = typeConstraints.clone(clonedEqualityConstraints)
        val clonedNumericConstraints = numericConstraints.clone()
        return UPathConstraints(
            ctx = ctx,
            logicalConstraints = clonedLogicalConstraints,
            equalityConstraints = clonedEqualityConstraints,
            typeConstraints = clonedTypeConstraints,
            numericConstraints = clonedNumericConstraints
        )
    }

    class LogicalConstraints(
        private var constraints: PersistentMap<UBoolExpr, ConstraintSource> = persistentHashMapOf(),
        private var unsatCore: PathConstraintsUnsatCore? = null
    ) {
        val isContradicting: Boolean
            get() = unsatCore != null

        fun generateUnsatCore(): PathConstraintsUnsatCore? = unsatCore

        fun addConstraint(constraint: UBoolExpr, source: ConstraintSource) = with(constraint.ctx) {
            add(constraint, constraint.not(), source)
        }

        private fun add(constraint: UBoolExpr, notConstraint: UBoolExpr, source: ConstraintSource){
            if (constraint.isTrue || constraint in constraints) return
            if (constraint.isFalse || notConstraint in constraints) {
                val notConstraintSource = constraints[notConstraint]
                unsatCore = when {
                    constraint.isFalse -> PathConstraintsUnsatCore("False", emptyList())

                    notConstraintSource != null -> PathConstraintsUnsatCore(
                        "Logical",
                        listOf(constraint to source, notConstraint to notConstraintSource)
                    )

                    else -> PathConstraintsUnsatCore("Logical", emptyList())
                }
                return
            }

            constraints = constraints.put(constraint, source)
        }

        fun asSequence(): Sequence<UBoolExpr> = constraints.keys.asSequence()

        fun translateAndAssert(
            translator: UExprTranslator<*, *>,
            smtAssert: (KExpr<KBoolSort>, ConstraintSource) -> Unit
        ) {
            for ((constraint, constraintSource) in constraints) {
                val translated = translator.translate(constraint)
                smtAssert(translated, constraintSource)
            }
        }

        fun clone(): LogicalConstraints = LogicalConstraints(constraints, unsatCore)
    }
}

sealed interface ConstraintSource

class LocationConstraintSource(
    val location: PathsTrieNode<*, *>
) : ConstraintSource

object UnknownConstraintSource : ConstraintSource

data class PathConstraintsUnsatCore(
    val debugInfo: String,
    val core: List<Pair<UBoolExpr, ConstraintSource>>,
)
