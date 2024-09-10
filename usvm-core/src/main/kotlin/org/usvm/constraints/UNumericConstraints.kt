package org.usvm.constraints

import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KBitVecValue
import io.ksmt.expr.KBvAddExpr
import io.ksmt.expr.KBvNegationExpr
import io.ksmt.expr.KBvSignedGreaterExpr
import io.ksmt.expr.KBvSignedGreaterOrEqualExpr
import io.ksmt.expr.KBvSignedLessExpr
import io.ksmt.expr.KBvSignedLessOrEqualExpr
import io.ksmt.expr.KBvSubExpr
import io.ksmt.expr.KEqExpr
import io.ksmt.expr.rewrite.simplify.ExpressionOrdering
import io.ksmt.utils.BvUtils.bvMaxValueSigned
import io.ksmt.utils.BvUtils.bvMinValueSigned
import io.ksmt.utils.BvUtils.bvOne
import io.ksmt.utils.BvUtils.bvZero
import io.ksmt.utils.BvUtils.isBvMaxValueSigned
import io.ksmt.utils.BvUtils.isBvMinValueSigned
import io.ksmt.utils.BvUtils.minus
import io.ksmt.utils.BvUtils.plus
import io.ksmt.utils.BvUtils.signedGreater
import io.ksmt.utils.BvUtils.signedGreaterOrEqual
import io.ksmt.utils.BvUtils.signedLess
import io.ksmt.utils.BvUtils.signedLessOrEqual
import io.ksmt.utils.asExpr
import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.algorithms.UPersistentMultiMap
import org.usvm.algorithms.addToSet
import org.usvm.algorithms.removeValue
import org.usvm.algorithms.separate
import org.usvm.collections.immutable.*
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.merging.MutableMergeGuard
import org.usvm.merging.UOwnedMergeable
import org.usvm.regions.IntIntervalsRegion
import org.usvm.solver.UExprTranslator

private typealias ConstraintTerms<Sort> = UExpr<Sort>

/**
 * Manage and simplify numeric constraints over bit-vectors (e.g. geq, lt, eq).
 *
 * [isNumericConstraint] --- check if expression is numeric constraint over bit-vectors and can
 * be handled with [UNumericConstraints].
 *
 * [addNumericConstraint] --- add numeric constraint.
 * Throws exception if constraint is not [isNumericConstraint]
 *
 * [addNegatedNumericConstraint] --- add negation of constraint.
 * Throws exception if constraint is not [isNumericConstraint]
 *
 * [constraints] --- retrieve currently added constraints (possibly simplified).
 *
 * [evalInterval] --- retrieve possible values interval for the expression.
 * */
class UNumericConstraints<Sort : UBvSort> private constructor(
    private val ctx: UContext<*>,
    val sort: Sort,
    private var ownership: MutabilityOwnership,
    private var numericConstraints: UPersistentHashMap<ConstraintTerms<Sort>, Constraint<Sort>>,
    private var constraintWatchList: UPersistentMultiMap<ConstraintTerms<Sort>, ConstraintTerms<Sort>>,
) : UOwnedMergeable<UNumericConstraints<Sort>, MutableMergeGuard> {
    constructor(ctx: UContext<*>, sort: Sort, ownership: MutabilityOwnership) : this(
        ctx,
        sort,
        ownership,
        persistentHashMapOf(),
        persistentHashMapOf()
    )

    fun changeOwnership(ownership: MutabilityOwnership) {
        this.ownership = ownership
    }

    private val constraintPropagationQueue = arrayListOf<ConstraintUpdateEvent<Sort>>()

    /**
     * Return true if current numeric constraints are unsatisfiable.
     * */
    var isContradicting = false
        private set

    private fun contradiction() {
        isContradicting = true
    }

    private val zero: KBitVecValue<Sort> by lazy {
        ctx.bvZero(sort.sizeBits)
    }

    private val one: KBitVecValue<Sort> by lazy {
        ctx.bvOne(sort.sizeBits)
    }

    private val minValue: KBitVecValue<Sort> by lazy {
        ctx.bvMinValueSigned(sort.sizeBits)
    }

    private val maxValue: KBitVecValue<Sort> by lazy {
        ctx.bvMaxValueSigned(sort.sizeBits)
    }

    /**
     * Retrieve actual constraints.
     * */
    fun constraints(): Sequence<UBoolExpr> {
        if (isContradicting) {
            return sequenceOf(ctx.falseExpr)
        }

        return numericConstraints.asSequence()
            .flatMap { it.value.mkExpressions() }
    }

    fun constraints(translator: UExprTranslator<*, *>): Sequence<UBoolExpr> = constraints().map(translator::translate)

    /**
     * Check if [expr] is numeric constraint over bit-vectors and can
     * be handled with [UNumericConstraints].
     * */
    fun isNumericConstraint(expr: UBoolExpr): Boolean =
        recognizeNumericConstraint(
            expr = expr,
            eqConstraint = { _, _ -> true },
            lessConstraint = { _, _ -> true },
            lessOrEqualConstraint = { _, _ -> true },
            unknownConstraint = { false }
        )

    /**
     * Add numeric constraint [expr].
     * Throws exception if constraint is not [isNumericConstraint]
     * */
    fun addNumericConstraint(expr: UBoolExpr) {
        recognizeNumericConstraint(
            expr = expr,
            eqConstraint = { lhs, rhs ->
                addConstraint(
                    lhs = lhs,
                    rhs = rhs,
                    ConstraintKind.EQ
                )
            },
            lessConstraint = { lhs, rhs ->
                addConstraint(
                    lhs = lhs,
                    rhs = rhs,
                    ConstraintKind.LT
                )
            },
            lessOrEqualConstraint = { lhs, rhs ->
                addConstraint(
                    lhs = lhs,
                    rhs = rhs,
                    ConstraintKind.LEQ
                )
            },
            unknownConstraint = { error("Unknown numeric constraint: $expr") }
        )
    }

    /**
     * Add negation of constraint [expr].
     * Throws exception if constraint is not [isNumericConstraint]
     * */
    fun addNegatedNumericConstraint(expr: UBoolExpr) {
        recognizeNumericConstraint(
            expr = expr,
            eqConstraint = { lhs, rhs ->
                addConstraint(
                    lhs = lhs,
                    rhs = rhs,
                    ConstraintKind.NEQ
                )
            },
            lessConstraint = { lhs, rhs ->
                // (not (< a b)) <=> (<= b a)
                addConstraint(
                    lhs = rhs,
                    rhs = lhs,
                    ConstraintKind.LEQ
                )
            },
            lessOrEqualConstraint = { lhs, rhs ->
                // (not (<= a b)) <=> (< b a)
                addConstraint(
                    lhs = rhs,
                    rhs = lhs,
                    ConstraintKind.LT
                )
            },
            unknownConstraint = { error("Unknown numeric constraint: $expr") }
        )
    }

    /**
     * Add constraint on [lhs] and [rhs].
     *
     * 1. Rewrite both expressions in the form: a + b + c0
     * where a and b are terms (e.g., variables or complex expressions),
     * and c0 is a constant value.
     * See [collectLinearTerms].
     *
     * 2. Add constraint on rewritten expressions.
     *
     * 3. Propagate constraints.
     * Constraint addition may update lower or upper bounds on the expressions,
     * which can result in better bounds on other expressions.
     * */
    private fun addConstraint(lhs: UExpr<Sort>, rhs: UExpr<Sort>, kind: ConstraintKind) {
        if (isContradicting) return

        val (lhsTerms, lhsConst) = collectLinearTerms(lhs)
        val (rhsTerms, rhsConst) = collectLinearTerms(rhs)

        when (kind) {
            ConstraintKind.EQ -> addEqualityConstraint(lhsTerms, lhsConst, rhsTerms, rhsConst)

            ConstraintKind.NEQ -> addDisequalityConstraint(lhsTerms, lhsConst, rhsTerms, rhsConst)

            ConstraintKind.LT -> addUpperBoundConstraint(
                lhsTerms, lhsConst, rhsTerms, rhsConst, isStrict = true, isInternalConstraint = false
            )

            ConstraintKind.LEQ -> addUpperBoundConstraint(
                lhsTerms, lhsConst, rhsTerms, rhsConst, isStrict = false, isInternalConstraint = false
            )
        }

        propagateConstraints()
    }

    /**
     * Retrieve lower and upper bounds for the [expr].
     * */
    fun evalInterval(expr: UExpr<Sort>): IntIntervalsRegion {
        require(sort == ctx.bv32Sort) { "Unsupported sort: $sort" }

        val (terms, const) = collectLinearTerms(expr)

        if (terms == null) {
            return IntIntervalsRegion.point(const?.intValue ?: 0)
        }

        return withConstraint(
            terms = terms,
            bounds = { bounds, boundsBias ->
                val bias = add(boundsBias, const)

                val actualConstraints = bounds.actualizeConstraint(bias)

                val lowerBound = actualConstraints.lowerBound(bias)?.value?.intValue ?: Int.MIN_VALUE
                val upperBound = actualConstraints.upperBound(bias)?.value?.intValue ?: Int.MAX_VALUE

                var interval = IntIntervalsRegion.ofClosed(lowerBound, upperBound)

                actualConstraints.excludedPoints(bias).forEach { value ->
                    val point = IntIntervalsRegion.point(value.intValue)
                    interval = interval.subtract(point)
                }

                interval
            },
            value = { value ->
                val biasedValue = add(value, const)
                IntIntervalsRegion.point(biasedValue.intValue)
            },
            noConstraint = {
                IntIntervalsRegion.universe()
            }
        )
    }

    private val KBitVecValue<Sort>.intValue: Int get() = (this as KBitVec32Value).intValue

    fun clone(thisOwnership: MutabilityOwnership, cloneOwnership: MutabilityOwnership): UNumericConstraints<Sort> {
        if (this.isContradicting) {
            return this
        }

        this.ownership = thisOwnership
        return UNumericConstraints(ctx, sort, cloneOwnership, numericConstraints, constraintWatchList)
    }

    private fun constraintUpdated(update: ConstraintUpdateEvent<Sort>) {
        constraintPropagationQueue.add(update)
    }


    /**
     * Find an appropriate constraint for the [terms].
     *
     * [bounds] --- [terms] are constrained with [BoundsConstraint].
     * Additional constant bias is passed to the [bounds] when
     * [terms] are biased wrt equality constraint.
     *
     * [value] --- [terms] are constrained with concrete value.
     * */
    private inline fun <T> withConstraint(
        terms: ConstraintTerms<Sort>,
        bounds: (BoundsConstraint<Sort>, KBitVecValue<Sort>) -> T,
        value: (KBitVecValue<Sort>) -> T,
        noConstraint: () -> T,
    ): T {
        var constraint = numericConstraints[terms] ?: return noConstraint()
        while (true) {
            when (constraint) {
                is BoundsConstraint<Sort> -> return bounds(constraint, zero)
                is ConcreteEqualityConstraint<Sort> -> return value(constraint.value)
                is TermsEqualityConstraint<Sort> -> {
                    val equalConstraints = numericConstraints[constraint.equalTerms]
                        ?: error("Unexpected")

                    when (equalConstraints) {
                        is BoundsConstraint<Sort> -> {
                            // c + bias = et <=> c = et - bias
                            return bounds(equalConstraints, sub(zero, constraint.bias))
                        }

                        is ConcreteEqualityConstraint<Sort> -> {
                            // c + bias = value <=> c = value - bias
                            return value(sub(equalConstraints.value, constraint.bias))
                        }

                        is TermsEqualityConstraint<Sort> -> {
                            // c + bias = et && et + eb = x <=> c + (bias + eb) = x
                            val mergedConstraint = TermsEqualityConstraint(
                                constrainedTerms = terms,
                                bias = add(constraint.bias, equalConstraints.bias),
                                equalTerms = equalConstraints.equalTerms
                            )
                            updateConstraint(mergedConstraint)
                            constraint = mergedConstraint
                            continue
                        }
                    }
                }
            }
        }
    }

    private inline fun <T> withConstraint(
        terms: ConstraintTerms<Sort>,
        bounds: (BoundsConstraint<Sort>, KBitVecValue<Sort>) -> T,
        value: (KBitVecValue<Sort>) -> T,
    ): T = withConstraint(
        terms = terms,
        bounds = { constraint, bias -> bounds(constraint, bias) },
        value = { v -> value(v) },
        noConstraint = {
            val constraint = BoundsConstraint(terms)
            updateConstraint(constraint)
            bounds(constraint, zero)
        }
    )

    private fun updateConstraint(constraint: Constraint<Sort>) {
        numericConstraints = numericConstraints.put(constraint.constrainedTerms, constraint, ownership)
    }

    private fun constraintAddDependency(terms: ConstraintTerms<Sort>, dependency: ConstraintTerms<Sort>) {
        if (dependency !in numericConstraints) {
            updateConstraint(BoundsConstraint(dependency))
        }

        val watchList = constraintWatchList[dependency]

        // new constraint
        if (watchList == null && dependency.hasMultipleTerms()) {
            for (term in dependency.unitTerms()) {
                constraintAddDependency(dependency, term)
            }
        }

        val currentWatchList = watchList ?: persistentHashSetOf()
        val updatedWatchList = currentWatchList.add(terms, ownership)
        if (updatedWatchList === watchList) {
            return
        }

        constraintWatchList = constraintWatchList.put(dependency, updatedWatchList, ownership)
    }

    private fun propagateConstraints() {
        while (constraintPropagationQueue.isNotEmpty()) {
            val update = constraintPropagationQueue.removeLast()
            val dependentConstraints = constraintWatchList[update.terms] ?: continue
            withConstraint(
                terms = update.terms,
                bounds = { updatedConstraint, updateInitialBias ->
                    for (dc in dependentConstraints) {
                        withConstraint(
                            terms = dc,
                            bounds = { dcConstraint, _ ->
                                val updatedDcConstraint = propagate(
                                    constraint = dcConstraint,
                                    updatedConstraint = updatedConstraint,
                                    updatedBias = add(updateInitialBias, update.bias),
                                    kind = update.kind
                                )
                                if (updatedDcConstraint !== dcConstraint) {
                                    updateConstraint(updatedDcConstraint)
                                }
                            },
                            value = {
                                // nothing to propagate
                            },
                            noConstraint = {}
                        )
                    }
                },
                value = {
                    // values already substituted
                },
                noConstraint = {}
            )
        }
    }

    private fun addEqualityConstraint(
        lhsTerms: ConstraintTerms<Sort>?,
        lhsConst: KBitVecValue<Sort>?,
        rhsTerms: ConstraintTerms<Sort>?,
        rhsConst: KBitVecValue<Sort>?,
    ) {
        if (lhsTerms == null && rhsTerms == null) {
            if (lhsConst != rhsConst) {
                contradiction()
            }
            return
        }

        if (lhsTerms == null) {
            // swap lhs and rhs
            addLhsEqualityConstraint(rhsTerms!!, rhsConst, rhsTerms = null, lhsConst)
        } else {
            addLhsEqualityConstraint(lhsTerms, lhsConst, rhsTerms, rhsConst)
        }
    }

    private fun addDisequalityConstraint(
        lhsTerms: ConstraintTerms<Sort>?,
        lhsConst: KBitVecValue<Sort>?,
        rhsTerms: ConstraintTerms<Sort>?,
        rhsConst: KBitVecValue<Sort>?,
    ) {
        if (lhsTerms == null && rhsTerms == null) {
            if (lhsConst == rhsConst) {
                contradiction()
            }
            return
        }

        if (lhsTerms == null) {
            // swap lhs and rhs
            addLhsDisequalityConstraint(rhsTerms!!, rhsConst, rhsTerms = null, lhsConst)
        } else {
            addLhsDisequalityConstraint(lhsTerms, lhsConst, rhsTerms, rhsConst)
        }
    }

    private fun addUpperBoundConstraint(
        lhsTerms: ConstraintTerms<Sort>?,
        lhsConst: KBitVecValue<Sort>?,
        rhsTerms: ConstraintTerms<Sort>?,
        rhsConst: KBitVecValue<Sort>?,
        isStrict: Boolean,
        isInternalConstraint: Boolean,
    ) {
        if (lhsTerms == null && rhsTerms == null) {
            check(lhsConst != null && rhsConst != null) {
                "Binary constraint with an empty operand"
            }

            if (lhsConst.signedGreater(rhsConst)) {
                contradiction()
            }

            if (isStrict && lhsConst == rhsConst) {
                contradiction()
            }

            return
        }

        if (lhsTerms == null) {
            // swap lhs and rhs: rhs is UB for lhs <=> lhs is LB for rhs
            addLhsLowerBoundConcreteConstraint(rhsTerms!!, rhsConst, lhsConst, isStrict, isInternalConstraint)
        } else {
            addLhsUpperBoundConstraint(lhsTerms, lhsConst, rhsTerms, rhsConst, isStrict, isInternalConstraint)
        }
    }

    private fun addLowerBoundConcreteConstraint(
        lhsTerms: ConstraintTerms<Sort>?,
        lhsConst: KBitVecValue<Sort>?,
        rhsConst: KBitVecValue<Sort>?,
        isStrict: Boolean,
        isInternalConstraint: Boolean,
    ) {
        if (lhsTerms == null) {
            check(lhsConst != null && rhsConst != null) {
                "Binary constraint with an empty operand"
            }

            if (lhsConst.signedLess(rhsConst)) {
                contradiction()
            }

            if (isStrict && lhsConst == rhsConst) {
                contradiction()
            }

            return
        }

        addLhsLowerBoundConcreteConstraint(lhsTerms, lhsConst, rhsConst, isStrict, isInternalConstraint)
    }

    private fun addLhsEqualityConstraint(
        lhsTerms: ConstraintTerms<Sort>,
        lhsConst: KBitVecValue<Sort>?,
        rhsTerms: ConstraintTerms<Sort>?,
        rhsConst: KBitVecValue<Sort>?,
    ) = withConstraint(
        terms = lhsTerms,
        bounds = { lhsConstraints, lhsBias ->
            if (rhsTerms == null) {
                addBoundConcreteEqualityConstraint(lhsConstraints, add(lhsConst, lhsBias), rhsConst ?: zero)
            } else {
                addBoundTermsEqualityConstraint(lhsConstraints, add(lhsConst, lhsBias), rhsTerms, rhsConst)
            }
        },
        value = { value ->
            // swap lhs and rhs: value + lc == rt + rc <=> rt + rc == value + lc
            addEqualityConstraint(rhsTerms, rhsConst, rhsTerms = null, rhsConst = add(lhsConst, value))
        }
    )

    private fun addLhsDisequalityConstraint(
        lhsTerms: ConstraintTerms<Sort>,
        lhsConst: KBitVecValue<Sort>?,
        rhsTerms: ConstraintTerms<Sort>?,
        rhsConst: KBitVecValue<Sort>?,
    ) = withConstraint(
        terms = lhsTerms,
        bounds = { lhsConstraints, lhsBias ->
            updateIfModified(lhsConstraints) {
                if (rhsTerms == null) {
                    lhsConstraints.addConcreteDisequality(
                        add(lhsConst, lhsBias), rhsConst ?: zero,
                        isPrimary = true
                    )
                } else {
                    addBoundTermsDisequalityConstraint(lhsConstraints, add(lhsConst, lhsBias), rhsTerms, rhsConst)
                }
            }
        },
        value = { value ->
            // swap lhs and rhs: value + lc != rt + rc <=> rt + rc != value + lc
            addDisequalityConstraint(rhsTerms, rhsConst, rhsTerms = null, rhsConst = add(lhsConst, value))
        }
    )

    private fun addLhsLowerBoundConcreteConstraint(
        lhsTerms: ConstraintTerms<Sort>,
        lhsConst: KBitVecValue<Sort>?,
        rhsConst: KBitVecValue<Sort>?,
        isStrict: Boolean,
        isInternalConstraint: Boolean,
    ) = withConstraint(
        terms = lhsTerms,
        bounds = { lhsConstraint, lhsBias ->
            updateIfModified(lhsConstraint) {
                lhsConstraint.addConcreteLowerBound(
                    add(lhsConst, lhsBias), rhsConst ?: zero,
                    isStrict, isPrimary = true
                )
            }
        },
        value = { value ->
            // swap lhs and rhs: value + lc >= rt + rc <=> rt + rc <= value + lc
            addUpperBoundConstraint(
                lhsTerms = null, lhsConst = rhsConst,
                rhsTerms = null, rhsConst = add(lhsConst, value),
                isStrict, isInternalConstraint
            )
        }
    )

    private fun addLhsUpperBoundConstraint(
        lhsTerms: ConstraintTerms<Sort>,
        lhsConst: KBitVecValue<Sort>?,
        rhsTerms: ConstraintTerms<Sort>?,
        rhsConst: KBitVecValue<Sort>?,
        isStrict: Boolean,
        isInternalConstraint: Boolean,
    ) = withConstraint(
        terms = lhsTerms,
        bounds = { lhsConstraint, lhsBias ->
            updateIfModified(lhsConstraint) {
                if (rhsTerms == null) {
                    lhsConstraint.addConcreteUpperBound(
                        add(lhsConst, lhsBias), rhsConst ?: zero, isStrict,
                        isPrimary = true
                    )
                } else {
                    addBoundTermsUpperBoundConstraint(
                        lhsConstraint, add(lhsConst, lhsBias), rhsTerms, rhsConst,
                        isStrict, isInternalConstraint
                    )
                }
            }
        },
        value = { value ->
            // swap lhs and rhs: value + lc <= rt + rc <=> rt + rc >= value + lc
            addLowerBoundConcreteConstraint(
                rhsTerms, rhsConst,
                rhsConst = add(lhsConst, value), isStrict,
                isInternalConstraint
            )
        }
    )

    private inline fun updateIfModified(original: Constraint<Sort>, body: () -> Constraint<Sort>) {
        val updated = body()
        if (updated !== original) {
            updateConstraint(updated)
        }
    }

    private fun addBoundTermsEqualityConstraint(
        lhsConstraint: BoundsConstraint<Sort>,
        lhsBias: KBitVecValue<Sort>,
        rhsTerms: ConstraintTerms<Sort>,
        rhsConst: KBitVecValue<Sort>?,
    ) = withConstraint(
        terms = rhsTerms,
        bounds = { rhsConstraint, rhsBias ->
            val normalizedRhsConst = add(rhsConst, rhsBias)

            // x + lc == x + rc
            if (rhsConstraint === lhsConstraint) {
                if (lhsBias != normalizedRhsConst) {
                    contradiction()
                }

                lhsConstraint
            } else {
                // Select representative and merge constraints
                if (lhsConstraint.size() > rhsConstraint.size()) {
                    mergeEqualBoundTermsConstraint(lhsConstraint, lhsBias, rhsConstraint, normalizedRhsConst)
                } else {
                    mergeEqualBoundTermsConstraint(rhsConstraint, normalizedRhsConst, lhsConstraint, lhsBias)
                }
            }
        },
        value = { value ->
            addBoundConcreteEqualityConstraint(
                lhsConstraint, lhsBias, add(rhsConst, value)
            )
        }
    )

    private fun addBoundTermsDisequalityConstraint(
        lhsConstraint: BoundsConstraint<Sort>,
        lhsBias: KBitVecValue<Sort>,
        rhsTerms: ConstraintTerms<Sort>,
        rhsConst: KBitVecValue<Sort>?,
    ): BoundsConstraint<Sort> = withConstraint(
        terms = rhsTerms,
        bounds = { rhsConstraint, rhsBias ->
            val normalizedRhsConst = add(rhsConst, rhsBias)

            // x + lc != x + rc
            if (rhsConstraint === lhsConstraint) {
                if (lhsBias == normalizedRhsConst) {
                    contradiction()
                }

                lhsConstraint
            } else {
                lhsConstraint.addTermsDisequality(
                    bias = lhsBias,
                    rhs = rhsConstraint,
                    rhsBias = normalizedRhsConst
                )
            }
        },
        value = { value ->
            lhsConstraint.addConcreteDisequality(
                lhsBias, add(rhsConst, value), isPrimary = true
            )
        }
    )

    private fun addBoundTermsUpperBoundConstraint(
        lhsConstraint: BoundsConstraint<Sort>,
        lhsBias: KBitVecValue<Sort>,
        rhsTerms: ConstraintTerms<Sort>,
        rhsConst: KBitVecValue<Sort>?,
        isStrict: Boolean,
        isInternalConstraint: Boolean,
    ): BoundsConstraint<Sort> = withConstraint(
        terms = rhsTerms,
        bounds = { rhsConstraint, rhsBias ->
            val normalizedRhsConst = add(rhsConst, rhsBias)

            if (rhsConstraint === lhsConstraint) {
                val simplifiedRhs = if (isStrict) {
                    // (a + c0) < (a + c1) <=> (a + c0) < (MIN_VALUE + c0 - c1)
                    minValue + (lhsBias - normalizedRhsConst)
                } else {
                    // (a + c0) <= (a + c1) <=> (a + c0) <= (MAX_VALUE + c0 - c1)
                    maxValue + (lhsBias - normalizedRhsConst)
                }

                lhsConstraint.addConcreteUpperBound(
                    lhsBias, simplifiedRhs.uncheckedCast(), isStrict, isPrimary = true
                )
            } else {
                val updatedLhs = lhsConstraint.addTermsUpperBound(
                    lhsBias, rhsConstraint, normalizedRhsConst, isStrict
                )
                if (!isInternalConstraint && updatedLhs !== lhsConstraint) {
                    updateConstraint(updatedLhs)

                    val updatedRHs = rhsConstraint.addTermsInferredLowerBound(
                        normalizedRhsConst, updatedLhs, lhsBias, isStrict
                    )
                    if (updatedRHs !== rhsConstraint) {
                        updateConstraint(updatedRHs)
                    }
                }
                updatedLhs
            }
        },
        value = { value ->
            lhsConstraint.addConcreteUpperBound(
                lhsBias, add(rhsConst, value), isStrict, isPrimary = true
            )
        }
    )

    /**
     *  Merge [source] constraints into [destination].
     *
     *  Substitute all occurrences of [source] with [destination]:
     *  dst + dst.bias = src + src.bias <=> src + (src.bias - dst.bias) = dst
     *  */
    private fun mergeEqualBoundTermsConstraint(
        destination: BoundsConstraint<Sort>,
        destinationBias: KBitVecValue<Sort>,
        source: BoundsConstraint<Sort>,
        sourceBias: KBitVecValue<Sort>,
    ) {
        val bias = sub(sourceBias, destinationBias)

        val newSourceConstraint = TermsEqualityConstraint(
            constrainedTerms = source.constrainedTerms,
            bias = bias,
            equalTerms = destination.constrainedTerms
        )
        updateConstraint(newSourceConstraint)

        source.propagateTermEquality(bias, destination)
    }

    /**
     * Substitute all occurrences of [lhsConstraint] with concrete value:
     * lhs + lhsBias = rhs <=> lhs = rhs - lhsBias
     * */
    private fun addBoundConcreteEqualityConstraint(
        lhsConstraint: BoundsConstraint<Sort>,
        lhsBias: KBitVecValue<Sort>,
        rhsConst: KBitVecValue<Sort>,
    ) {
        val value = sub(rhsConst, lhsBias)

        val newConstraint = ConcreteEqualityConstraint(lhsConstraint.constrainedTerms, value)
        updateConstraint(newConstraint)

        lhsConstraint.propagateValueEquality(value)
    }

    private fun BoundsConstraint<Sort>.addConcreteUpperBound(
        bias: KBitVecValue<Sort>,
        value: KBitVecValue<Sort>,
        isStrict: Boolean,
        isPrimary: Boolean,
    ): BoundsConstraint<Sort> {
        val normalizedValue: KBitVecValue<Sort> = if (isStrict) {
            if (value.isBvMinValueSigned()) {
                contradiction()
                return this
            }

            sub(value, one)
        } else {
            value
        }

        return addConcreteUpperBound(bias, normalizedValue, isPrimary)
    }

    private fun BoundsConstraint<Sort>.addConcreteLowerBound(
        bias: KBitVecValue<Sort>,
        value: KBitVecValue<Sort>,
        isStrict: Boolean,
        isPrimary: Boolean,
    ): BoundsConstraint<Sort> {
        val normalizedValue: KBitVecValue<Sort> = if (isStrict) {
            if (value.isBvMaxValueSigned()) {
                contradiction()
                return this
            }

            add(value, one)
        } else {
            value
        }

        return addConcreteLowerBound(bias, normalizedValue, isPrimary)
    }

    private fun BoundsConstraint<Sort>.addConcreteUpperBound(
        bias: KBitVecValue<Sort>,
        value: KBitVecValue<Sort>,
        isPrimary: Boolean,
    ): BoundsConstraint<Sort> =
        refineFromGround(bias).updateConcreteUpperBound(bias, value, isPrimary)

    private fun BoundsConstraint<Sort>.addConcreteLowerBound(
        bias: KBitVecValue<Sort>,
        value: KBitVecValue<Sort>,
        isPrimary: Boolean,
    ): BoundsConstraint<Sort> =
        refineFromGround(bias).updateConcreteLowerBound(bias, value, isPrimary)

    private fun BoundsConstraint<Sort>.addConcreteDisequality(
        bias: KBitVecValue<Sort>,
        value: KBitVecValue<Sort>,
        isPrimary: Boolean,
    ): BoundsConstraint<Sort> =
        refineFromGround(bias).updateConcreteDisequality(bias, value, isPrimary)

    private fun BoundsConstraint<Sort>.addTermsUpperBound(
        bias: KBitVecValue<Sort>,
        rhs: BoundsConstraint<Sort>,
        rhsBias: KBitVecValue<Sort>,
        isStrict: Boolean,
    ): BoundsConstraint<Sort> = refineFromGround(bias)
        .updateTermsUpperBound(bias, rhs.actualizeConstraint(rhsBias), rhsBias, isStrict)

    private fun BoundsConstraint<Sort>.addTermsInferredLowerBound(
        bias: KBitVecValue<Sort>,
        rhs: BoundsConstraint<Sort>,
        rhsBias: KBitVecValue<Sort>,
        isStrict: Boolean,
    ): BoundsConstraint<Sort> = refineFromGround(bias)
        .updateTermsInferredLowerBound(bias, rhs.actualizeConstraint(rhsBias), rhsBias, isStrict)

    private fun BoundsConstraint<Sort>.addTermsDisequality(
        bias: KBitVecValue<Sort>,
        rhs: BoundsConstraint<Sort>,
        rhsBias: KBitVecValue<Sort>,
    ): BoundsConstraint<Sort> = refineFromGround(bias)
        .updateTermsDisequality(bias, rhs.actualizeConstraint(rhsBias), rhsBias)

    private fun BoundsConstraint<Sort>.actualizeConstraint(
        bias: KBitVecValue<Sort>,
    ): BoundsConstraint<Sort> {
        val actualized = refineFromGround(bias)
        if (this !== actualized) {
            updateConstraint(actualized)
        }
        return actualized
    }

    private fun BoundsConstraint<Sort>.refineGroundConstraint(
        bias: KBitVecValue<Sort>,
    ): BoundsConstraint<Sort> =
        refineBounds(bias, zero) { subNoOverflow(it, bias) }

    private fun BoundsConstraint<Sort>.refineFromGround(
        bias: KBitVecValue<Sort>,
    ): BoundsConstraint<Sort> =
        refineBounds(zero, bias) { addNoOverflow(it, bias) }

    private inline fun BoundsConstraint<Sort>.refineBounds(
        sourceBias: KBitVecValue<Sort>,
        targetBias: KBitVecValue<Sort>,
        shiftBound: (KBitVecValue<Sort>) -> KBitVecValue<Sort>?,
    ): BoundsConstraint<Sort> {
        if (sourceBias == targetBias) {
            return this
        }

        val sourceLowerBound = lowerBound(sourceBias)
        val sourceUpperBound = upperBound(sourceBias)

        val targetLowerBound = sourceLowerBound?.let { shiftBound(it.value) }
        val targetUpperBound = sourceUpperBound?.let { shiftBound(it.value) }

        if (targetLowerBound == null || targetUpperBound == null) {
            return this
        }

        var updateKind: BoundsUpdateKind? = null
        val refinedLB = refineLowerBound(targetBias, targetLowerBound)
        if (refinedLB !== this) {
            updateKind = BoundsUpdateKind.LOWER
        }

        val refined = refinedLB.refineUpperBound(targetBias, targetUpperBound)
        if (refined !== refinedLB) {
            updateKind = if (updateKind == BoundsUpdateKind.LOWER) {
                BoundsUpdateKind.BOTH
            } else {
                BoundsUpdateKind.UPPER
            }
        }

        if (updateKind == null) {
            return this
        }

        constraintUpdated(ConstraintUpdateEvent(constrainedTerms, targetBias, updateKind))

        return refined
    }

    private fun BoundsConstraint<Sort>.refineLowerBound(
        bias: KBitVecValue<Sort>,
        bound: KBitVecValue<Sort>,
    ): BoundsConstraint<Sort> {
        val current = concreteLowerBounds[bias]
        if (current != null && current.value.signedGreaterOrEqual(bound)) return this
        val isPrimary = current?.isPrimary ?: false
        return modifyConcreteLowerBounds(bias, ValueConstraint(bound, isPrimary), ownership)
    }

    private fun BoundsConstraint<Sort>.refineUpperBound(
        bias: KBitVecValue<Sort>,
        bound: KBitVecValue<Sort>,
    ): BoundsConstraint<Sort> {
        val current = concreteUpperBounds[bias]
        if (current != null && current.value.signedLessOrEqual(bound)) return this
        val isPrimary = current?.isPrimary ?: false
        return modifyConcreteUpperBounds(bias, ValueConstraint(bound, isPrimary), ownership)
    }

    private fun BoundsConstraint<Sort>.updateConcreteLowerBound(
        bias: KBitVecValue<Sort>,
        value: KBitVecValue<Sort>,
        isPrimary: Boolean,
    ): BoundsConstraint<Sort> {
        upperBound(bias)?.let {
            if (it.value.signedLess(value)) {
                contradiction()
                return this
            }
        }

        lowerBound(bias)?.let {
            if (it.value.signedGreater(value)) {
                return this
            }

            // Replace with primary constraint. Constraint value remains unchanged
            if (it.value == value) {
                return if (isPrimary && !it.isPrimary) {
                    modifyConcreteLowerBounds(bias, ValueConstraint(value, isPrimary = true), ownership)
                } else {
                    this
                }
            }
        }

        return addRefinedConcreteLowerBound(bias, ValueConstraint(value, isPrimary))
    }

    private fun BoundsConstraint<Sort>.updateConcreteUpperBound(
        bias: KBitVecValue<Sort>,
        value: KBitVecValue<Sort>,
        isPrimary: Boolean,
    ): BoundsConstraint<Sort> {
        lowerBound(bias)?.let {
            if (it.value.signedGreater(value)) {
                contradiction()
                return this
            }
        }

        upperBound(bias)?.let {
            if (it.value.signedLess(value)) {
                return this
            }

            // Replace with primary constraint. Constraint value remains unchanged
            if (it.value == value) {
                return if (isPrimary && !it.isPrimary) {
                    modifyConcreteUpperBounds(bias, ValueConstraint(value, isPrimary = true), ownership)
                } else {
                    this
                }
            }
        }

        return addRefinedConcreteUpperBound(bias, ValueConstraint(value, isPrimary))
    }

    private fun BoundsConstraint<Sort>.updateConcreteDisequality(
        bias: KBitVecValue<Sort>,
        value: KBitVecValue<Sort>,
        isPrimary: Boolean,
    ): BoundsConstraint<Sort> {
        upperBound(bias)?.let {
            if (it.value.signedLess(value)) {
                return this
            }
        }

        lowerBound(bias)?.let {
            if (it.value.signedGreater(value)) {
                return this
            }
        }

        // We must have a single constraint for each bias, but we can have multiple biases
        // this + bias != value <=> this + (bias - value) != 0
        if (value != zero) {
            return addConcreteDisequality(sub(bias, value), zero, isPrimary)
        }

        val currentDisequality = concreteDisequalitites[bias]
        if (currentDisequality != null && (currentDisequality.isPrimary || !isPrimary)) {
            return this
        }

        return modifyConcreteDisequalitites(bias, ValueConstraint(value, isPrimary), ownership)
    }

    private fun BoundsConstraint<Sort>.excludedPoints(
        bias: KBitVecValue<Sort>,
    ): Sequence<KBitVecValue<Sort>> =
        concreteDisequalitites.asSequence().map { (constraintBias, _) ->
            // x + constraintBias != 0 <=> x + bias != bias - constraintBias
            sub(bias, constraintBias)
        }

    private fun BoundsConstraint<Sort>.updateTermsInferredLowerBound(
        lhsBias: KBitVecValue<Sort>,
        rhs: BoundsConstraint<Sort>,
        rhsBias: KBitVecValue<Sort>,
        isStrict: Boolean,
    ): BoundsConstraint<Sort> {
        val rhsLB = rhs.lowerBound(rhsBias)
        val rhsUB = rhs.upperBound(rhsBias)

        val lhsLB = lowerBound(lhsBias)
        val lhsUB = upperBound(lhsBias)

        if (lhsUB != null && rhsLB != null) {
            if (lhsUB.value.signedLess(rhsLB.value)) {
                contradiction()
                return this
            }
        }

        if (rhsUB != null && lhsLB != null) {
            // todo: strict?, equal constraints?
            if (lhsLB.value.signedGreater(rhsUB.value)) {
                return this
            }
        }

        val constraint = TermsConstraint(rhs.constrainedTerms, rhsBias, isStrict)

        if (rhsLB != null && (lhsLB == null || rhsLB.value.signedGreater(lhsLB.value))) {
            return addInferredLowerBound(lhsBias, rhs, constraint, rhsLB.value) { bounds ->
                // we have stricter constraint than current concrete
                bounds.addConcreteLowerBound(lhsBias, rhsLB.value, isPrimary = false)
            }
        }

        return addInferredLowerBound(lhsBias, rhs, constraint, rhsLB?.value)
    }

    private fun BoundsConstraint<Sort>.updateTermsUpperBound(
        lhsBias: KBitVecValue<Sort>,
        rhs: BoundsConstraint<Sort>,
        rhsBias: KBitVecValue<Sort>,
        isStrict: Boolean,
    ): BoundsConstraint<Sort> {
        val rhsLB = rhs.lowerBound(rhsBias)
        val rhsUB = rhs.upperBound(rhsBias)

        val lhsLB = lowerBound(lhsBias)
        val lhsUB = upperBound(lhsBias)

        if (rhsUB != null && lhsLB != null) {
            if (lhsLB.value.signedGreater(rhsUB.value)) {
                contradiction()
                return this
            }
        }

        if (rhsLB != null && lhsUB != null) {
            // todo: strict?, equal constraints?
            if (lhsUB.value.signedLess(rhsLB.value)) {
                return this
            }
        }

        val constraint = TermsConstraint(rhs.constrainedTerms, rhsBias, isStrict)

        if (rhsUB != null && (lhsUB == null || rhsUB.value.signedLess(lhsUB.value))) {
            return addUpperBound(lhsBias, rhs, constraint, rhsUB.value) { bounds ->
                // we have stricter constraint than current concrete
                bounds.addConcreteUpperBound(lhsBias, rhsUB.value, isPrimary = false)
            }
        }

        return addUpperBound(lhsBias, rhs, constraint, rhsUB?.value)
    }

    private fun BoundsConstraint<Sort>.updateTermsDisequality(
        lhsBias: KBitVecValue<Sort>,
        rhs: BoundsConstraint<Sort>,
        rhsBias: KBitVecValue<Sort>,
    ): BoundsConstraint<Sort> {
        val rhsLB = rhs.lowerBound(rhsBias)
        val lhsUB = upperBound(lhsBias)

        if (rhsLB != null && lhsUB != null && lhsUB.value.signedLess(rhsLB.value)) {
            return this
        }

        val rhsUB = rhs.upperBound(rhsBias)
        val lhsLB = lowerBound(lhsBias)

        if (rhsUB != null && lhsLB != null && rhsUB.value.signedLess(lhsLB.value)) {
            return this
        }

        // Since we store biases for each constraint, it is better to have fewer constraints but more biases
        // this + bias != rhs + rhsBias <=> this + (bias - rhsBias) != rhs + 0
        if (rhsBias != zero) {
            return addTermsDisequality(sub(lhsBias, rhsBias), rhs, zero)
        }

        val constraint = TermsConstraint(rhs.constrainedTerms, rhsBias, isStrict = true)
        val modifiedDiseq = termDisequalities.addTermConstraint(lhsBias, constraint, ownership)
        return modifyTermDisequalities(modifiedDiseq)
    }

    // this + replacementBias = replacement
    private fun BoundsConstraint<Sort>.propagateTermEquality(
        replacementBias: KBitVecValue<Sort>,
        replacement: BoundsConstraint<Sort>,
    ) {
        var updatedReplacement = replacement

        // this + bias >= bound <=> replacement + (bias - replacementBias) >= bound
        updatedReplacement = concreteLowerBounds.fold(updatedReplacement) { result, (bias, bound) ->
            result.addConcreteLowerBound(sub(bias, replacementBias), bound.value, bound.isPrimary)
        }

        // this + bias <= bound <=> replacement + (bias - replacementBias) <= bound
        updatedReplacement = concreteUpperBounds.fold(updatedReplacement) { result, (bias, bound) ->
            result.addConcreteUpperBound(sub(bias, replacementBias), bound.value, bound.isPrimary)
        }

        // this + bias != bound <=> replacement + (bias - replacementBias) != bound
        updatedReplacement = concreteDisequalitites.fold(updatedReplacement) { result, (bias, bound) ->
            result.addConcreteDisequality(sub(bias, replacementBias), bound.value, bound.isPrimary)
        }

        // don't copy lower bounds since all of them are derived constraints

        // this + bias <= constraint <=> replacement + (bias - replacementBias) <= constraint
        updatedReplacement = copyTermBounds(
            updatedReplacement, replacementBias, termUpperBounds
        ) { constraints, bias, rhsTerms, rhsBias, isStrict ->
            addBoundTermsUpperBoundConstraint(
                constraints, bias, rhsTerms, rhsBias, isStrict, isInternalConstraint = true
            )
        }

        // this + bias != constraint <=> replacement + (bias - replacementBias) != constraint
        updatedReplacement = copyTermBounds(
            updatedReplacement, replacementBias, termDisequalities
        ) { constraints, bias, rhsTerms, rhsBias, _ ->
            addBoundTermsDisequalityConstraint(constraints, bias, rhsTerms, rhsBias)
        }

        updateConstraint(updatedReplacement)

        val dependencies = constraintWatchList[constrainedTerms]
        // toList fixes [dependencies] because it can be mutated in foreach body
        dependencies?.toList()?.forEach { dependentTerms ->
            withConstraint(
                terms = dependentTerms,
                bounds = { dependencyConstraint, _ ->
                    // this = replacement + (- replacementBias)
                    val updated = substituteTerms(
                        dependencyConstraint,
                        constrainedTerms,
                        updatedReplacement,
                        sub(zero, replacementBias)
                    )
                    updateConstraint(updated)
                },
                value = {},
                noConstraint = {}
            )
        }
    }

    private fun BoundsConstraint<Sort>.propagateValueEquality(value: KBitVecValue<Sort>) {
        // value + bias >= bound
        concreteLowerBounds.forEach { (bias, bound) ->
            if (add(value, bias).signedLess(bound.value)) {
                contradiction()
                return
            }
        }

        // value + bias <= bound
        concreteUpperBounds.forEach { (bias, bound) ->
            if (add(value, bias).signedGreater(bound.value)) {
                contradiction()
                return
            }
        }

        // value + bias != bound
        concreteDisequalitites.forEach { (bias, bound) ->
            if (add(value, bias) == bound.value) {
                contradiction()
                return
            }
        }

        propagateValueEqualityConstraints(
            termsValue = value,
            bounds = inferredTermLowerBounds,
            // value + bias >= constraintValue + constraint.bias
            hasContradiction = { biasedValue, bound ->
                biasedValue.signedLess(bound)
            },
            update = { constraints, bias, biasedValue, isStrict ->
                constraints.addConcreteUpperBound(bias, biasedValue, isStrict, isPrimary = false)
            }
        )

        propagateValueEqualityConstraints(
            termsValue = value,
            bounds = termUpperBounds,
            // value + bias <= constraintValue + constraint.bias
            hasContradiction = { biasedValue, bound ->
                biasedValue.signedGreater(bound)
            },
            update = { constraints, bias, biasedValue, isStrict ->
                constraints.addConcreteLowerBound(bias, biasedValue, isStrict, isPrimary = true)
            }
        )

        propagateValueEqualityConstraints(
            termsValue = value,
            bounds = termDisequalities,
            // value + bias != constraintValue + constraint.bias
            hasContradiction = { biasedValue, bound ->
                biasedValue == bound
            },
            update = { constraints, bias, biasedValue, _ ->
                constraints.addConcreteDisequality(bias, biasedValue, isPrimary = true)
            }
        )

        val dependencies = constraintWatchList[constrainedTerms]
        // toList fixes [dependencies] because it can be mutated in foreach body
        dependencies?.toList()?.forEach { dependentTerms ->
            withConstraint(
                terms = dependentTerms,
                bounds = { dependencyConstraint, _ ->
                    val updated = substituteValue(dependencyConstraint, constrainedTerms, value)
                    updateConstraint(updated)
                },
                value = {},
                noConstraint = {}
            )
        }
    }

    private fun substituteValue(
        initialConstraint: BoundsConstraint<Sort>,
        terms: ConstraintTerms<Sort>,
        value: KBitVecValue<Sort>,
    ): BoundsConstraint<Sort> {
        var result = initialConstraint

        result = substituteValue(
            initialConstraint = result,
            terms = terms,
            termsValue = value,
            bounds = initialConstraint.inferredTermLowerBounds,
            updateBounds = { constraint, bounds -> constraint.modifyTermLowerBounds(bounds) }
        ) { res, bias, constraintValue, isStrict ->
            res.addConcreteLowerBound(bias, constraintValue, isStrict, isPrimary = false)
        }

        result = substituteValue(
            initialConstraint = result,
            terms = terms,
            termsValue = value,
            bounds = initialConstraint.termUpperBounds,
            updateBounds = { constraint, bounds -> constraint.modifyTermUpperBounds(bounds) }
        ) { res, bias, constraintValue, isStrict ->
            res.addConcreteUpperBound(bias, constraintValue, isStrict, isPrimary = true)
        }

        result = substituteValue(
            initialConstraint = result,
            terms = terms,
            termsValue = value,
            bounds = initialConstraint.termDisequalities,
            updateBounds = { constraints, bounds -> constraints.modifyTermDisequalities(bounds) }
        ) { res, bias, constraintValue, _ ->
            res.addConcreteDisequality(bias, constraintValue, isPrimary = true)
        }

        return result
    }

    // terms = replacement + replacementBias
    private fun substituteTerms(
        constraintSet: BoundsConstraint<Sort>,
        terms: ConstraintTerms<Sort>,
        replacement: BoundsConstraint<Sort>,
        replacementBias: KBitVecValue<Sort>,
    ): BoundsConstraint<Sort> {
        var result = constraintSet

        result = substituteTerms(
            initialConstraint = result,
            terms = terms,
            replacement = replacement,
            replacementBias = replacementBias,
            bounds = constraintSet.inferredTermLowerBounds,
            updateBounds = { constraints, bounds -> constraints.modifyTermLowerBounds(bounds) }
        ) { res, bias, rhs, rhsBias, isStrict ->
            res.addTermsInferredLowerBound(bias, rhs, rhsBias, isStrict)
        }

        result = substituteTerms(
            initialConstraint = result,
            terms = terms,
            replacement = replacement,
            replacementBias = replacementBias,
            bounds = constraintSet.termUpperBounds,
            updateBounds = { constraints, bounds -> constraints.modifyTermUpperBounds(bounds) }
        ) { res, bias, rhs, rhsBias, isStrict ->
            res.addTermsUpperBound(bias, rhs, rhsBias, isStrict)
        }

        result = substituteTerms(
            initialConstraint = result,
            terms = terms,
            replacement = replacement,
            replacementBias = replacementBias,
            bounds = constraintSet.termDisequalities,
            updateBounds = { constraints, bounds -> constraints.modifyTermDisequalities(bounds) }
        ) { res, bias, rhs, rhsBias, _ ->
            res.addTermsDisequality(bias, rhs, rhsBias)
        }

        return result
    }

    private inline fun substituteValue(
        initialConstraint: BoundsConstraint<Sort>,
        terms: ConstraintTerms<Sort>,
        termsValue: KBitVecValue<Sort>,
        bounds: TermConstraintSet<Sort>,
        updateBounds: (BoundsConstraint<Sort>, TermConstraintSet<Sort>) -> BoundsConstraint<Sort>,
        update: (
            BoundsConstraint<Sort>,
            KBitVecValue<Sort>,
            KBitVecValue<Sort>,
            Boolean,
        ) -> BoundsConstraint<Sort>,
    ): BoundsConstraint<Sort> {
        val constraints = bounds.termDependency.getOrDefault(terms, persistentHashSetOf())
        var result = initialConstraint
        for (constraint in constraints) {
            val biasedConstraint = add(termsValue, constraint.bias)
            val biases = bounds.termConstraints[constraint] ?: continue
            for (bias in biases) {
                // this + bias (op) terms + constraint.bias && terms = termsValue
                // this + bias (op) termsValue + constraint.bias
                result = update(result, bias, biasedConstraint, constraint.isStrict)
            }
        }

        result = updateBounds(result, bounds.dropTermsConstraints(terms, ownership))
        return result
    }

    private inline fun substituteTerms(
        initialConstraint: BoundsConstraint<Sort>,
        terms: ConstraintTerms<Sort>,
        replacement: BoundsConstraint<Sort>,
        replacementBias: KBitVecValue<Sort>,
        bounds: TermConstraintSet<Sort>,
        updateBounds: (BoundsConstraint<Sort>, TermConstraintSet<Sort>) -> BoundsConstraint<Sort>,
        update: (
            BoundsConstraint<Sort>,
            KBitVecValue<Sort>,
            BoundsConstraint<Sort>,
            KBitVecValue<Sort>,
            Boolean,
        ) -> BoundsConstraint<Sort>,
    ): BoundsConstraint<Sort> {
        val constraints = bounds.termDependency.getOrDefault(terms, persistentHashSetOf())
        var result = initialConstraint
        for (constraint in constraints.toList()) {
            val biases = bounds.termConstraints[constraint] ?: continue
            for (bias in biases) {
                // this + bias (op) terms + constraint.bias && terms = replacement + replacementBias
                // this + bias (op) replacement + (replacementBias + constraint.bias)
                result = update(
                    result, bias, replacement,
                    add(replacementBias, constraint.bias),
                    constraint.isStrict
                )
            }
        }

        result = updateBounds(result, bounds.dropTermsConstraints(terms, ownership))
        return result
    }

    private inline fun copyTermBounds(
        target: BoundsConstraint<Sort>,
        targetBias: KBitVecValue<Sort>,
        bounds: TermConstraintSet<Sort>,
        addConstraint: (
            BoundsConstraint<Sort>,
            KBitVecValue<Sort>,
            ConstraintTerms<Sort>,
            KBitVecValue<Sort>,
            Boolean,
        ) -> BoundsConstraint<Sort>,
    ): BoundsConstraint<Sort> {
        var result = target

        for ((constraint, biases) in bounds.termConstraints.toList()) {
            // this + bias (op) constraint <=> target + (bias - targetBias) (op) constraint
            for (bias in biases) {
                result = addConstraint(
                    result, sub(bias, targetBias), constraint.terms, constraint.bias, constraint.isStrict
                )
            }
        }

        return result
    }

    private inline fun propagateValueEqualityConstraints(
        termsValue: KBitVecValue<Sort>,
        bounds: TermConstraintSet<Sort>,
        hasContradiction: (KBitVecValue<Sort>, KBitVecValue<Sort>) -> Boolean,
        update: (
            BoundsConstraint<Sort>, KBitVecValue<Sort>, KBitVecValue<Sort>, Boolean,
        ) -> BoundsConstraint<Sort>,
    ) {
        for ((constraint, biases) in bounds.termConstraints.toList()) {
            withConstraint(
                terms = constraint.terms,
                bounds = { boundsConstraint, initialConstraintBias ->
                    var resultBounds = boundsConstraint
                    val constraintBias = add(constraint.bias, initialConstraintBias)

                    for (bias in biases) {
                        resultBounds = update(
                            resultBounds,
                            constraintBias,
                            add(termsValue, bias),
                            constraint.isStrict
                        )
                    }

                    updateConstraint(resultBounds)
                },

                // value + bias (op) constraintValue + constraint.bias
                value = { constraintValue ->
                    val biasedConstraint = add(constraintValue, constraint.bias)
                    for (bias in biases) {
                        val biasedValue = add(termsValue, bias)
                        if (hasContradiction(biasedValue, biasedConstraint)) {
                            contradiction()
                            return
                        }

                        if (constraint.isStrict && biasedValue == biasedConstraint) {
                            contradiction()
                            return
                        }
                    }
                }
            )
        }
    }

    /**
     * rhs >= bound
     * x2 > x1 > x0
     *
     * l + x0 >= rhs
     * l + x1 >= rhs
     * l + x2 >= rhs
     *
     * 1. bound >= 0
     *    x0 >= 0
     *    (x2 - x0) < (MAX_VALUE - bound)
     *    -------
     *    remove l + x1
     *
     * 2. bound >= 0
     *    x2 < 0
     *    (x2 - x0) < (MAX_VALUE - bound)
     *    -------
     *    remove l + x1
     *
     * 3. bound < 0
     *    x0 >= 0
     *    (x2 - x0) < (MAX_VALUE + bound)
     *    -------
     *    remove l + x1
     *
     * 4. bound < 0
     *    x2 < 0
     *    (x2 - x0) < (MAX_VALUE + bound)
     *    -------
     *    remove l + x1
     * */
    private inline fun eliminateTermLowerBound(
        boundsConstraint: BoundsConstraint<Sort>,
        bias: KBitVecValue<Sort>,
        rhs: BoundsConstraint<Sort>,
        constraint: TermsConstraint<Sort>,
        rhsLB: KBitVecValue<Sort>?,
        cont: (BoundsConstraint<Sort>) -> BoundsConstraint<Sort>,
    ): BoundsConstraint<Sort> = eliminateBoundConstraints(
        boundsConstraint, bias, rhsLB, rhsConstraint = rhs,
        findRelevantBiases = {
            boundsConstraint.inferredTermLowerBounds.findBiasesWithConstraint(constraint)
        },
        removeConstraintForBias = { bc, biasToRemove ->
            val modifiedBounds = bc.inferredTermLowerBounds.removeTermConstraint(biasToRemove, constraint, ownership)
            bc.modifyTermLowerBounds(modifiedBounds)
        },
        removeOppositeConstraintForBias = { bc, biasToRemove ->
            val oppositeConstraint = TermsConstraint(
                boundsConstraint.constrainedTerms,
                biasToRemove,
                constraint.isStrict
            )
            bc.removeTermUpperBound(constraint.bias, oppositeConstraint, ownership)
        },
        cont = { cont(it) }
    )

    /**
     * rhs <= bound
     * x2 > x1 > x0
     *
     * l + x0 <= rhs
     * l + x1 <= rhs
     * l + x2 <= rhs
     *
     * 1. bound >= 0
     *   x0 >= 0
     *   (x2 - x0) < (MAX_VALUE - bound)
     *   l + x1 can be removed
     *
     * 2. bound >= 0
     *    x2 < 0
     *    (x2 - x0) < (MAX_VALUE - bound)
     *    l + x1 can be removed
     *
     * 3. bound < 0
     *    x0 >= 0
     *    (x2 - x0) < (MAX_VALUE + bound)
     *    l + x1 can be removed
     *
     * 4. bound < 0
     *    x2 < 0
     *    (x2 - x0) < (MAX_VALUE + bound)
     *    l + x1 can be removed
     * */
    private inline fun eliminateTermUpperBound(
        boundsConstraint: BoundsConstraint<Sort>,
        bias: KBitVecValue<Sort>,
        rhs: BoundsConstraint<Sort>,
        constraint: TermsConstraint<Sort>,
        rhsUB: KBitVecValue<Sort>?,
        cont: (BoundsConstraint<Sort>) -> BoundsConstraint<Sort>,
    ): BoundsConstraint<Sort> = eliminateBoundConstraints(
        boundsConstraint, bias, rhsUB, rhsConstraint = rhs,
        findRelevantBiases = {
            boundsConstraint.termUpperBounds.findBiasesWithConstraint(constraint)
        },
        removeConstraintForBias = { bc, biasToRemove ->
            val modifiedBounds = bc.termUpperBounds.removeTermConstraint(biasToRemove, constraint, ownership)
            bc.modifyTermUpperBounds(modifiedBounds)
        },
        removeOppositeConstraintForBias = { bc, biasToRemove ->
            val oppositeConstraint = TermsConstraint(
                boundsConstraint.constrainedTerms,
                biasToRemove,
                constraint.isStrict
            )
            bc.removeTermLowerBound(constraint.bias, oppositeConstraint, ownership)
        },
        cont = { cont(it) }
    )

    /**
     * See [eliminateTermUpperBound] for the details.
     * */
    private inline fun eliminateConcreteUpperBound(
        boundsConstraint: BoundsConstraint<Sort>,
        bias: KBitVecValue<Sort>,
        constraint: ValueConstraint<Sort>,
        rhsUB: KBitVecValue<Sort>?,
        cont: (BoundsConstraint<Sort>) -> BoundsConstraint<Sort>,
    ): BoundsConstraint<Sort> = eliminateBoundConstraints(
        boundsConstraint, bias, rhsUB, rhsConstraint = null,
        findRelevantBiases = {
            boundsConstraint.concreteUpperBounds.asSequence()
                .mapNotNull { (bias, c) -> bias.takeIf { c == constraint } }
        },
        removeConstraintForBias = { bc, biasToRemove ->
            bc.removeConcreteUpperBound(biasToRemove, ownership)
        },
        removeOppositeConstraintForBias = { bc, _ -> bc },
        cont = { cont(it) }
    )

    /**
     * See [eliminateTermLowerBound] for the details.
     * */
    private inline fun eliminateConcreteLowerBound(
        boundsConstraint: BoundsConstraint<Sort>,
        bias: KBitVecValue<Sort>,
        constraint: ValueConstraint<Sort>,
        rhsLB: KBitVecValue<Sort>?,
        cont: (BoundsConstraint<Sort>) -> BoundsConstraint<Sort>,
    ): BoundsConstraint<Sort> = eliminateBoundConstraints(
        boundsConstraint, bias, rhsLB, rhsConstraint = null,
        findRelevantBiases = {
            boundsConstraint.concreteLowerBounds.asSequence()
                .mapNotNull { (bias, c) -> bias.takeIf { c == constraint } }
        },
        removeConstraintForBias = { bc, biasToRemove ->
            bc.removeConcreteLowerBound(biasToRemove, ownership)
        },
        removeOppositeConstraintForBias = { bc, _ -> bc },
        cont = { cont(it) }
    )

    private inline fun eliminateBoundConstraints(
        boundsConstraint: BoundsConstraint<Sort>,
        bias: KBitVecValue<Sort>,
        rhsBound: KBitVecValue<Sort>?,
        rhsConstraint: BoundsConstraint<Sort>?,
        findRelevantBiases: () -> Sequence<KBitVecValue<Sort>>,
        removeConstraintForBias: (BoundsConstraint<Sort>, KBitVecValue<Sort>) -> BoundsConstraint<Sort>,
        removeOppositeConstraintForBias: (BoundsConstraint<Sort>, KBitVecValue<Sort>) -> BoundsConstraint<Sort>,
        cont: (BoundsConstraint<Sort>) -> BoundsConstraint<Sort>,
    ): BoundsConstraint<Sort> {
        if (rhsBound == null) {
            return cont(boundsConstraint)
        }

        val searchPositive = bias.signedGreaterOrEqual(zero)
        val relevantConstraints = findRelevantBiases()
            .filter { if (searchPositive) it.signedGreaterOrEqual(zero) else it.signedLess(zero) }
            .toMutableList()

        if (relevantConstraints.size < 2) {
            return cont(boundsConstraint)
        }

        relevantConstraints.add(bias)
        relevantConstraints.sortWith(BvValueComparator)

        val delta = if (rhsBound.signedGreaterOrEqual(zero)) {
            sub(maxValue, rhsBound)
        } else {
            add(maxValue, rhsBound)
        }

        val biasesToRemove = arrayListOf<KBitVecValue<Sort>>()

        var leftIdx = 0
        var rightIdx = 2
        while (rightIdx < relevantConstraints.size) {
            val left = relevantConstraints[leftIdx]
            val right = relevantConstraints[rightIdx]
            if ((right - left).signedLess(delta)) {
                val idxToRemove = rightIdx - 1
                biasesToRemove.add(relevantConstraints[idxToRemove])
                rightIdx++
                continue
            }

            leftIdx = rightIdx - 1
            rightIdx++
        }

        if (biasesToRemove.isEmpty()) {
            return cont(boundsConstraint)
        }

        var modifiedConstraint = boundsConstraint
        var removeCurrent = false

        var modifiedRhsConstraint = rhsConstraint

        for (biasToRemove in biasesToRemove) {
            if (biasToRemove == bias) {
                removeCurrent = true
                continue
            }

            modifiedConstraint = removeConstraintForBias(modifiedConstraint, biasToRemove)
            modifiedRhsConstraint = modifiedRhsConstraint?.let { removeOppositeConstraintForBias(it, biasToRemove) }
        }

        if (modifiedRhsConstraint !== null && modifiedRhsConstraint !== rhsConstraint) {
            updateConstraint(modifiedRhsConstraint)
        }

        if (removeCurrent) {
            return modifiedConstraint
        }

        return cont(modifiedConstraint)
    }

    private inline fun BoundsConstraint<Sort>.addInferredLowerBound(
        bias: KBitVecValue<Sort>,
        rhs: BoundsConstraint<Sort>,
        constraint: TermsConstraint<Sort>,
        rhsLB: KBitVecValue<Sort>?,
        postProcessConstraint: (BoundsConstraint<Sort>) -> BoundsConstraint<Sort> = { it },
    ): BoundsConstraint<Sort> =
        eliminateTermLowerBound(this, bias, rhs, constraint, rhsLB) { boundsConstraint ->
            constraintAddDependency(boundsConstraint.constrainedTerms, constraint.terms)
            val updatedBounds = boundsConstraint.inferredTermLowerBounds.addTermConstraint(bias, constraint, ownership)
            val result = boundsConstraint.modifyTermLowerBounds(updatedBounds)
            postProcessConstraint(result)
        }

    private inline fun BoundsConstraint<Sort>.addUpperBound(
        bias: KBitVecValue<Sort>,
        rhs: BoundsConstraint<Sort>,
        constraint: TermsConstraint<Sort>,
        rhsUB: KBitVecValue<Sort>?,
        postProcessConstraint: (BoundsConstraint<Sort>) -> BoundsConstraint<Sort> = { it },
    ): BoundsConstraint<Sort> =
        eliminateTermUpperBound(this, bias, rhs, constraint, rhsUB) { boundsConstraint ->
            constraintAddDependency(boundsConstraint.constrainedTerms, constraint.terms)
            val updatedBounds = boundsConstraint.termUpperBounds.addTermConstraint(bias, constraint, ownership)
            val result = boundsConstraint.modifyTermUpperBounds(updatedBounds)
            postProcessConstraint(result)
        }

    private fun BoundsConstraint<Sort>.addRefinedConcreteUpperBound(
        bias: KBitVecValue<Sort>,
        constraint: ValueConstraint<Sort>,
    ): BoundsConstraint<Sort> = eliminateConcreteUpperBound(
        this, bias, constraint, rhsUB = constraint.value
    ) { boundsConstraint ->
        constraintUpdated(ConstraintUpdateEvent(constrainedTerms, bias, BoundsUpdateKind.UPPER))
        return boundsConstraint
            .modifyConcreteUpperBounds(bias, constraint, ownership)
            .refineGroundConstraint(bias)
    }

    private fun BoundsConstraint<Sort>.addRefinedConcreteLowerBound(
        bias: KBitVecValue<Sort>,
        constraint: ValueConstraint<Sort>,
    ): BoundsConstraint<Sort> = eliminateConcreteLowerBound(
        this, bias, constraint, rhsLB = constraint.value
    ) { boundsConstraint ->
        constraintUpdated(ConstraintUpdateEvent(constrainedTerms, bias, BoundsUpdateKind.LOWER))
        return boundsConstraint
            .modifyConcreteLowerBounds(bias, constraint, ownership)
            .refineGroundConstraint(bias)
    }

    private fun propagate(
        constraint: BoundsConstraint<Sort>,
        updatedConstraint: BoundsConstraint<Sort>,
        updatedBias: KBitVecValue<Sort>,
        kind: BoundsUpdateKind,
    ): BoundsConstraint<Sort> {
        var propagationResult = constraint.propagateUnitTermConstraint(updatedConstraint.constrainedTerms)

        if (kind == BoundsUpdateKind.LOWER || kind == BoundsUpdateKind.BOTH) {
            propagationResult = propagateTermConstraint(
                propagationResult, updatedConstraint, updatedBias, propagationResult.inferredTermLowerBounds
            ) { res, bias, isStrict ->
                res.addTermsInferredLowerBound(bias, updatedConstraint, updatedBias, isStrict)
            }
        }

        if (kind == BoundsUpdateKind.UPPER || kind == BoundsUpdateKind.BOTH) {
            propagationResult = propagateTermConstraint(
                propagationResult, updatedConstraint, updatedBias, propagationResult.termUpperBounds
            ) { res, bias, isStrict ->
                res.addTermsUpperBound(bias, updatedConstraint, updatedBias, isStrict)
            }
        }

        return propagationResult
    }

    private fun BoundsConstraint<Sort>.propagateUnitTermConstraint(
        updatedTerm: ConstraintTerms<Sort>,
    ): BoundsConstraint<Sort> {
        if (updatedTerm.hasMultipleTerms() || !constrainedTerms.hasMultipleTerms()) return this
        val constraintUnitTerms = constrainedTerms.unitTerms()
        if (updatedTerm !in constraintUnitTerms) return this

        /**
         * a <= aUb && a >= aLb
         * b <= bUb && b >= bLb
         * aUb + bUb no overflow
         * aLb + bLb no overflow
         * ---------------------
         * a + b <= (aUb + bUb)
         * a + b >= (aLb + bLb)
         * */
        val termsSumLowerBound = boundsSumNoOverflow(constraintUnitTerms) { constraint, bias ->
            constraint.lowerBound(bias)?.value
        } ?: return this

        val termsSumUpperBound = boundsSumNoOverflow(constraintUnitTerms) { constraint, bias ->
            constraint.upperBound(bias)?.value
        } ?: return this

        return this
            .addConcreteLowerBound(bias = zero, value = termsSumLowerBound, isPrimary = false)
            .addConcreteUpperBound(bias = zero, value = termsSumUpperBound, isPrimary = false)
    }

    private inline fun propagateTermConstraint(
        constraint: BoundsConstraint<Sort>,
        rhs: BoundsConstraint<Sort>,
        rhsBias: KBitVecValue<Sort>,
        bounds: TermConstraintSet<Sort>,
        propagateConstraint: (
            BoundsConstraint<Sort>, KBitVecValue<Sort>, Boolean,
        ) -> BoundsConstraint<Sort>,
    ): BoundsConstraint<Sort> {
        val propagatedStrict = propagateTermConstraint(
            constraint, rhs, rhsBias, bounds, propagateConstraint, isStrict = true
        )
        return propagateTermConstraint(
            propagatedStrict, rhs, rhsBias, bounds, propagateConstraint, isStrict = false
        )
    }

    private inline fun propagateTermConstraint(
        constraint: BoundsConstraint<Sort>,
        rhs: BoundsConstraint<Sort>,
        rhsBias: KBitVecValue<Sort>,
        bounds: TermConstraintSet<Sort>,
        propagateConstraint: (BoundsConstraint<Sort>, KBitVecValue<Sort>, Boolean) -> BoundsConstraint<Sort>,
        isStrict: Boolean,
    ): BoundsConstraint<Sort> {
        val rhsConstraint = TermsConstraint(rhs.constrainedTerms, rhsBias, isStrict)
        val biases = bounds.termConstraints[rhsConstraint] ?: return constraint
        return biases.fold(constraint) { res, bias ->
            propagateConstraint(res, bias, isStrict)
        }
    }

    private inline fun boundsSumNoOverflow(
        terms: List<ConstraintTerms<Sort>>,
        getBound: (BoundsConstraint<Sort>, KBitVecValue<Sort>) -> KBitVecValue<Sort>?,
    ): KBitVecValue<Sort>? = terms.fold(zero) { acc, term ->
        withConstraint(
            terms = term,
            bounds = { bounds, bias ->
                getBound(bounds, bias)?.let { addNoOverflow(acc, it) } ?: return null
            },
            value = { addNoOverflow(acc, it) ?: return null },
            noConstraint = { return null }
        )
    }

    private enum class ConstraintKind {
        EQ, NEQ, LT, LEQ
    }

    sealed class Constraint<Sort : UBvSort>(val constrainedTerms: ConstraintTerms<Sort>) {
        abstract fun mkExpressions(): Sequence<UBoolExpr>
    }

    /**
     * Term constraints.
     *
     * Concrete constraints --- constraints with concrete values.
     * 1. lower bounds --- [constrainedTerms] + bias >= value
     * 2. upper bounds --- [constrainedTerms] + bias <= value
     * 3. disequality --- [constrainedTerms] + bias != 0
     *
     * Concrete constraints can be primary or not.
     * Primary constraints are added using [addConstraint] and must be
     * a part of the [constraints].
     * Non-primary constraints are inferred from other primary constraints
     * and can be skipped in [constraints].
     *
     * See [ValueConstraint].
     *
     * Term constraints --- constraints with other terms.
     * 1. lower bounds --- [constrainedTerms] + bias >= rhsTerms + rhsBias
     * 2. upper bounds --- [constrainedTerms] + bias <= rhsTerms + rhsBias
     * 3. disequality --- [constrainedTerms] + bias != rhsTerms + 0
     *
     * Lower bounds are always inferred from upper bounds of rhs terms
     * and can be skipped in [constraints].
     * Upper bounds and disequalities are always primary and must be
     * a part of the [constraints].
     *
     * See [TermConstraintSet].
     * */
    class BoundsConstraint<Sort : UBvSort>(
        constrainedTerms: ConstraintTerms<Sort>,
        val concreteLowerBounds: UPersistentHashMap<KBitVecValue<Sort>, ValueConstraint<Sort>>,
        val concreteUpperBounds: UPersistentHashMap<KBitVecValue<Sort>, ValueConstraint<Sort>>,
        val concreteDisequalitites: UPersistentHashMap<KBitVecValue<Sort>, ValueConstraint<Sort>>,
        val inferredTermLowerBounds: TermConstraintSet<Sort>,
        val termUpperBounds: TermConstraintSet<Sort>,
        val termDisequalities: TermConstraintSet<Sort>,
    ) : Constraint<Sort>(constrainedTerms) {
        constructor(constrainedTerms: ConstraintTerms<Sort>) : this(
            constrainedTerms = constrainedTerms,
            concreteLowerBounds = persistentHashMapOf(),
            concreteUpperBounds = persistentHashMapOf(),
            concreteDisequalitites = persistentHashMapOf(),
            inferredTermLowerBounds = TermConstraintSet<Sort>(),
            termUpperBounds = TermConstraintSet<Sort>(),
            termDisequalities = TermConstraintSet<Sort>()
        )

        fun size(): Int =
            inferredTermLowerBounds.size +
                termUpperBounds.size +
                termDisequalities.size

        fun lowerBound(bias: KBitVecValue<Sort>): ValueConstraint<Sort>? =
            concreteLowerBounds[bias]

        fun upperBound(bias: KBitVecValue<Sort>): ValueConstraint<Sort>? =
            concreteUpperBounds[bias]

        fun modifyConcreteLowerBounds(
            bias: KBitVecValue<Sort>,
            bound: ValueConstraint<Sort>,
            ownership: MutabilityOwnership,
        ): BoundsConstraint<Sort> {
            val modified = concreteLowerBounds.put(bias, bound, ownership)
            if (modified === this.concreteLowerBounds) {
                return this
            }
            return BoundsConstraint(
                constrainedTerms,
                modified, concreteUpperBounds, concreteDisequalitites,
                inferredTermLowerBounds, termUpperBounds, termDisequalities
            )
        }

        fun modifyConcreteUpperBounds(
            bias: KBitVecValue<Sort>,
            bound: ValueConstraint<Sort>,
            ownership: MutabilityOwnership,
        ): BoundsConstraint<Sort> {
            val modified = concreteUpperBounds.put(bias, bound, ownership)
            if (modified === this.concreteUpperBounds) {
                return this
            }
            return BoundsConstraint(
                constrainedTerms,
                concreteLowerBounds, modified, concreteDisequalitites,
                inferredTermLowerBounds, termUpperBounds, termDisequalities
            )
        }

        fun removeConcreteUpperBound(bias: KBitVecValue<Sort>, ownership: MutabilityOwnership): BoundsConstraint<Sort> {
            val modified = concreteUpperBounds.remove(bias, ownership)
            if (modified === this.concreteUpperBounds) {
                return this
            }
            return BoundsConstraint(
                constrainedTerms,
                concreteLowerBounds, modified, concreteDisequalitites,
                inferredTermLowerBounds, termUpperBounds, termDisequalities
            )
        }

        fun removeConcreteLowerBound(bias: KBitVecValue<Sort>, ownership: MutabilityOwnership): BoundsConstraint<Sort> {
            val modified = concreteLowerBounds.remove(bias, ownership)
            if (modified === this.concreteLowerBounds) {
                return this
            }
            return BoundsConstraint(
                constrainedTerms,
                modified, concreteUpperBounds, concreteDisequalitites,
                inferredTermLowerBounds, termUpperBounds, termDisequalities
            )
        }

        fun modifyConcreteDisequalitites(
            bias: KBitVecValue<Sort>,
            bound: ValueConstraint<Sort>,
            ownership: MutabilityOwnership,
        ): BoundsConstraint<Sort> {
            val modified = concreteDisequalitites.put(bias, bound, ownership)
            if (modified === this.concreteDisequalitites) {
                return this
            }
            return BoundsConstraint(
                constrainedTerms,
                concreteLowerBounds, concreteUpperBounds, modified,
                inferredTermLowerBounds, termUpperBounds, termDisequalities
            )
        }

        fun modifyTermLowerBounds(termLowerBounds: TermConstraintSet<Sort>): BoundsConstraint<Sort> {
            if (termLowerBounds === this.inferredTermLowerBounds) {
                return this
            }
            return BoundsConstraint(
                constrainedTerms,
                concreteLowerBounds, concreteUpperBounds, concreteDisequalitites,
                termLowerBounds, termUpperBounds, termDisequalities
            )
        }

        fun modifyTermUpperBounds(termUpperBounds: TermConstraintSet<Sort>): BoundsConstraint<Sort> {
            if (termUpperBounds === this.termUpperBounds) {
                return this
            }
            return BoundsConstraint(
                constrainedTerms,
                concreteLowerBounds, concreteUpperBounds, concreteDisequalitites,
                inferredTermLowerBounds, termUpperBounds, termDisequalities
            )
        }

        fun modifyTermDisequalities(termDisequalities: TermConstraintSet<Sort>): BoundsConstraint<Sort> {
            if (termDisequalities === this.termDisequalities) {
                return this
            }
            return BoundsConstraint(
                constrainedTerms,
                concreteLowerBounds, concreteUpperBounds, concreteDisequalitites,
                inferredTermLowerBounds, termUpperBounds, termDisequalities
            )
        }

        fun removeTermLowerBound(
            bias: KBitVecValue<Sort>,
            constraint: TermsConstraint<Sort>,
            ownership: MutabilityOwnership,
        ): BoundsConstraint<Sort> {
            val updatedBounds = inferredTermLowerBounds.removeTermConstraint(bias, constraint, ownership)
            return modifyTermLowerBounds(updatedBounds)
        }

        fun removeTermUpperBound(
            bias: KBitVecValue<Sort>,
            constraint: TermsConstraint<Sort>,
            ownership: MutabilityOwnership,
        ): BoundsConstraint<Sort> {
            val updatedBounds = termUpperBounds.removeTermConstraint(bias, constraint, ownership)
            return modifyTermUpperBounds(updatedBounds)
        }

        override fun mkExpressions(): Sequence<UBoolExpr> {
            // Concrete bounds are always not strict
            val lbConcreteSeq = mapPrimaryConcrete(concreteLowerBounds) { bias, rhs ->
                val lhs = bias.ctx.mkBvAddExpr(constrainedTerms, bias)
                bias.ctx.mkBvSignedGreaterOrEqualExpr(lhs, rhs)
            }

            val ubConcreteSeq = mapPrimaryConcrete(concreteUpperBounds) { bias, rhs ->
                val lhs = bias.ctx.mkBvAddExpr(constrainedTerms, bias)
                bias.ctx.mkBvSignedLessOrEqualExpr(lhs, rhs)
            }

            val disEqConcreteSeq = mapPrimaryConcrete(concreteDisequalitites) { bias, rhs ->
                val lhs = bias.ctx.mkBvAddExpr(constrainedTerms, bias)
                bias.ctx.mkNot(bias.ctx.mkEq(lhs, rhs))
            }

            // term lower bounds are always derived and can be skipped in expressions

            val ubTermSeq = mapTerms(termUpperBounds) { bias, rhs, isStrict ->
                val lhs = bias.ctx.mkBvAddExpr(constrainedTerms, bias)
                if (isStrict) {
                    bias.ctx.mkBvSignedLessExpr(lhs, rhs)
                } else {
                    bias.ctx.mkBvSignedLessOrEqualExpr(lhs, rhs)
                }
            }

            val disEqTermSeq = mapTerms(termDisequalities) { bias, rhs, _ ->
                val lhs = bias.ctx.mkBvAddExpr(constrainedTerms, bias)
                bias.ctx.mkNot(bias.ctx.mkEq(lhs, rhs))
            }

            return lbConcreteSeq + ubConcreteSeq + disEqConcreteSeq + ubTermSeq + disEqTermSeq
        }

        private inline fun <T> mapPrimaryConcrete(
            concrete: UPersistentHashMap<KBitVecValue<Sort>, ValueConstraint<Sort>>,
            crossinline body: (KBitVecValue<Sort>, UExpr<Sort>) -> T,
        ): Sequence<T> =
            concrete.asSequence().mapNotNull { (bias, constraint) ->
                constraint.value.takeIf { constraint.isPrimary }?.let {
                    body(bias, it)
                }
            }

        private inline fun <T> mapTerms(
            constraintSet: TermConstraintSet<Sort>,
            crossinline body: (KBitVecValue<Sort>, UExpr<Sort>, Boolean) -> T,
        ): Sequence<T> =
            constraintSet.termConstraints.asSequence()
                .flatMap { (constraint, biases) ->
                    val expr = constraint.expr
                    biases.asSequence().map { bias ->
                        body(bias, expr, constraint.isStrict)
                    }
                }
    }

    class ConcreteEqualityConstraint<Sort : UBvSort>(
        constrainedTerms: ConstraintTerms<Sort>,
        val value: KBitVecValue<Sort>,
    ) : Constraint<Sort>(constrainedTerms) {
        private val expr by lazy {
            value.ctx.mkEq(constrainedTerms, value)
        }

        override fun mkExpressions(): Sequence<UBoolExpr> = sequenceOf(expr)
    }

    class TermsEqualityConstraint<Sort : UBvSort>(
        constrainedTerms: ConstraintTerms<Sort>,
        val bias: KBitVecValue<Sort>,
        val equalTerms: ConstraintTerms<Sort>,
    ) : Constraint<Sort>(constrainedTerms) {
        private val expr by lazy {
            bias.ctx.mkEq(bias.ctx.mkBvAddExpr(constrainedTerms, bias), equalTerms)
        }

        override fun mkExpressions(): Sequence<UBoolExpr> = sequenceOf(expr)
    }

    class TermConstraintSet<Sort : UBvSort>(
        val termConstraints: UPersistentMultiMap<TermsConstraint<Sort>, KBitVecValue<Sort>>,
        val termDependency: UPersistentMultiMap<ConstraintTerms<Sort>, TermsConstraint<Sort>>,
        val size: Int
    ) {
        constructor() : this(persistentHashMapOf(), persistentHashMapOf(), 0)

        fun addTermConstraint(
            bias: KBitVecValue<Sort>,
            constraint: TermsConstraint<Sort>,
            ownership: MutabilityOwnership,
        ): TermConstraintSet<Sort> {
            var newSize = size
            val constraints = termConstraints[constraint].also { if (it == null) newSize++ } ?: persistentHashSetOf()
            val updatedConstraints = constraints.add(bias, ownership)
            if (updatedConstraints === constraints) {
                return this
            }

            val updatedTermDependency = if (constraints.isNotEmpty()) {
                termDependency
            } else {
                termDependency.addToSet(constraint.terms, constraint, ownership)
            }

            return TermConstraintSet(
                termConstraints.put(constraint, updatedConstraints, ownership),
                updatedTermDependency,
                newSize
            )
        }

        fun removeTermConstraint(
            bias: KBitVecValue<Sort>,
            constraint: TermsConstraint<Sort>,
            ownership: MutabilityOwnership,
        ): TermConstraintSet<Sort> {
            val constraints = termConstraints[constraint] ?: return this
            val updatedConstraints = constraints.remove(bias, ownership)
            if (updatedConstraints === constraints) {
                return this
            }

            if (updatedConstraints.isEmpty()) {
                return TermConstraintSet(
                    termConstraints.remove(constraint, ownership),
                    termDependency.removeValue(constraint.terms, constraint, ownership),
                    size - 1
                )
            }

            return TermConstraintSet(
                termConstraints.put(constraint, updatedConstraints, ownership),
                termDependency,
                size
            )
        }

        fun dropTermsConstraints(terms: ConstraintTerms<Sort>, ownership: MutabilityOwnership): TermConstraintSet<Sort> {
            val constraints = termDependency[terms] ?: return this

            var updatedConstraints = termConstraints
            var updatedSize = size
            for (constraint in constraints) {
                val (newUpdatedConstraints, hasChanged) = updatedConstraints.removeWithChangeInfo(constraint, ownership)
                updatedConstraints = newUpdatedConstraints
                if (hasChanged) updatedSize--
            }

            return TermConstraintSet(
                updatedConstraints,
                termDependency.remove(terms, ownership),
                updatedSize
            )
        }

        fun findBiasesWithConstraint(constraint: TermsConstraint<Sort>): Sequence<KBitVecValue<Sort>> =
            termConstraints[constraint]?.asSequence() ?: emptySequence()
    }

    enum class BoundsUpdateKind {
        UPPER, LOWER, BOTH
    }

    data class ConstraintUpdateEvent<Sort : UBvSort>(
        val terms: ConstraintTerms<Sort>,
        val bias: KBitVecValue<Sort>,
        val kind: BoundsUpdateKind,
    )

    data class ValueConstraint<Sort : UBvSort>(
        val value: KBitVecValue<Sort>,
        val isPrimary: Boolean,
    )

    data class TermsConstraint<Sort : UBvSort>(
        val terms: ConstraintTerms<Sort>,
        val bias: KBitVecValue<Sort>,
        val isStrict: Boolean,
    ) {
        val expr: UExpr<Sort> by lazy {
            bias.ctx.mkBvAddExpr(terms, bias)
        }
    }

    object BvValueComparator : Comparator<KBitVecValue<*>> {
        override fun compare(a: KBitVecValue<*>, b: KBitVecValue<*>): Int = when {
            a == b -> 0
            a.signedLess(b) -> -1
            else -> 1
        }
    }

    private fun add(a: KBitVecValue<Sort>?, b: KBitVecValue<Sort>?): KBitVecValue<Sort> = when {
        a == null -> b ?: zero
        b == null -> a
        else -> (a + b).uncheckedCast()
    }

    private fun sub(a: KBitVecValue<Sort>?, b: KBitVecValue<Sort>?): KBitVecValue<Sort> = when {
        a == null -> b ?: zero
        b == null -> a
        else -> (a - b).uncheckedCast()
    }

    private fun addNoOverflow(a: KBitVecValue<Sort>, b: KBitVecValue<Sort>): KBitVecValue<Sort>? {
        val sum: KBitVecValue<Sort> = (a + b).uncheckedCast()
        val noOverflow = if (b.signedGreaterOrEqual(zero)) {
            sum.signedGreater(a)
        } else {
            sum.signedLess(a)
        }
        return sum.takeIf { noOverflow }
    }

    private fun subNoOverflow(a: KBitVecValue<Sort>, b: KBitVecValue<Sort>): KBitVecValue<Sort>? {
        val diff: KBitVecValue<Sort> = (a - b).uncheckedCast()
        val noOverflow = if (b.signedGreaterOrEqual(zero)) {
            diff.signedLess(a)
        } else {
            diff.signedGreater(a)
        }
        return diff.takeIf { noOverflow }
    }

    private fun ConstraintTerms<Sort>.hasMultipleTerms(): Boolean =
        this is KBvAddExpr<Sort>

    private fun ConstraintTerms<Sort>.unitTerms(): List<ConstraintTerms<Sort>> {
        val stack = arrayListOf(this)
        val result = arrayListOf<UExpr<Sort>>()

        while (stack.isNotEmpty()) {
            val term = stack.removeLast()
            if (term is KBvAddExpr<Sort>) {
                stack.add(term.arg0)
                stack.add(term.arg1)
            } else {
                result.add(term)
            }
        }

        return result
    }

    private fun collectLinearTerms(expr: UExpr<Sort>): Pair<ConstraintTerms<Sort>?, KBitVecValue<Sort>?> {
        val terms = mutableListOf<UExpr<Sort>>()
        val constants = mutableListOf<KBitVecValue<Sort>>()
        collectLinearTerms(expr, terms, constants)

        terms.sortedWith(ExpressionOrdering)

        val constant = constants.reduceOrNull { acc, c -> add(acc, c) }
        val constraintTerms = terms.reduceOrNull { acc, term -> ctx.mkBvAddExprNoSimplify(acc, term) }

        return constraintTerms to constant
    }

    private fun collectLinearTerms(
        expr: UExpr<Sort>,
        linearTerms: MutableList<UExpr<Sort>>,
        constantTerms: MutableList<KBitVecValue<Sort>>,
    ) {
        when (expr) {
            is KBitVecValue<Sort> -> {
                constantTerms.add(expr)
            }

            is KBvAddExpr<Sort> -> {
                collectLinearTerms(expr.arg0, linearTerms, constantTerms)
                collectLinearTerms(expr.arg1, linearTerms, constantTerms)
            }

            is KBvSubExpr<Sort> -> {
                collectLinearTerms(expr.arg0, linearTerms, constantTerms)
                collectLinearTerms(ctx.mkBvNegationExpr(expr.arg1), linearTerms, constantTerms)
            }

            is KBvNegationExpr<Sort> -> {
                when (val negatedValue = expr.value) {
                    is KBvAddExpr<Sort> -> {
                        collectLinearTerms(ctx.mkBvNegationExpr(negatedValue.arg0), linearTerms, constantTerms)
                        collectLinearTerms(ctx.mkBvNegationExpr(negatedValue.arg1), linearTerms, constantTerms)
                    }

                    is KBvSubExpr<Sort> -> {
                        collectLinearTerms(ctx.mkBvNegationExpr(negatedValue.arg0), linearTerms, constantTerms)
                        collectLinearTerms(negatedValue.arg1, linearTerms, constantTerms)
                    }

                    is KBvNegationExpr<Sort> -> collectLinearTerms(negatedValue.value, linearTerms, constantTerms)

                    else -> linearTerms.add(expr)
                }
            }

            else -> {
                linearTerms.add(expr)
            }
        }
    }

    private inline fun <T> recognizeNumericConstraint(
        expr: UBoolExpr,
        eqConstraint: (UExpr<Sort>, UExpr<Sort>) -> T,
        lessConstraint: (UExpr<Sort>, UExpr<Sort>) -> T,
        lessOrEqualConstraint: (UExpr<Sort>, UExpr<Sort>) -> T,
        unknownConstraint: (UBoolExpr) -> T,
    ): T = when {
        expr is KEqExpr<*> && expr.lhs.sort == sort -> {
            eqConstraint(expr.lhs.asExpr(sort), expr.rhs.asExpr(sort))
        }

        expr is KBvSignedLessExpr<*> && expr.arg0.sort == sort -> {
            lessConstraint(expr.arg0.asExpr(sort), expr.arg1.asExpr(sort))
        }

        expr is KBvSignedLessOrEqualExpr<*> && expr.arg0.sort == sort -> {
            lessOrEqualConstraint(expr.arg0.asExpr(sort), expr.arg1.asExpr(sort))
        }

        expr is KBvSignedGreaterExpr<*> && expr.arg0.sort == sort -> {
            lessConstraint(expr.arg1.asExpr(sort), expr.arg0.asExpr(sort))
        }

        expr is KBvSignedGreaterOrEqualExpr<*> && expr.arg0.sort == sort -> {
            lessOrEqualConstraint(expr.arg1.asExpr(sort), expr.arg0.asExpr(sort))
        }

        else -> unknownConstraint(expr)
    }

    /**
     * Check if this [UNumericConstraints] can be merged with [other] numeric constraints.
     *
     * Computes the intersection and puts it into result. Other constraints are put into merge guard [by].
     *
     * @return the numeric constraints.
     */
    override fun mergeWith(
        other: UNumericConstraints<Sort>,
        by: MutableMergeGuard,
        thisOwnership: MutabilityOwnership,
        otherOwnership: MutabilityOwnership,
        mergedOwnership: MutabilityOwnership,
    ): UNumericConstraints<Sort> {
        val (overlap, thisUnique, otherUnique) = this.numericConstraints
            .separate(other.numericConstraints, mergedOwnership)

        for (entry in thisUnique) {
            by.appendThis(entry.value.mkExpressions())
        }
        for (entry in otherUnique) {
            by.appendOther(entry.value.mkExpressions())
        }

        this.ownership = thisOwnership
        other.ownership = otherOwnership
        return UNumericConstraints(ctx, sort, mergedOwnership, overlap, constraintWatchList)
    }
}
