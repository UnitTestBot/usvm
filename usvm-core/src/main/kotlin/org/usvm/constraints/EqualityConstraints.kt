package org.usvm.constraints

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USymbolicHeapRef
import org.usvm.algorithms.DisjointSets
import org.usvm.isStaticHeapRef
import org.usvm.merging.MutableMergeGuard
import org.usvm.merging.UMergeable
import org.usvm.solver.UExprTranslator

/**
 * Represents equality constraints between symbolic heap references. There are three kinds of constraints:
 * - Equalities represented as collection of equivalence classes in union-find data structure [equalReferences].
 * - Disequalities: [referenceDisequalities].get(x).contains(y) means that x !== y.
 * - Nullable disequalities: [nullableDisequalities].get(x).contains(y) means that x !== y || (x == null && y == null).
 *
 * Maintains graph of disequality constraints. Tries to detect (or at least approximate) maximal set of distinct
 * symbolic heap references by fast-check of clique in disequality graph (not exponential!) (see [distinctReferences]).
 * All the rest disequalities (i.e., outside of the maximal clique) are stored into [referenceDisequalities].
 *
 * Important invariant: [distinctReferences], [referenceDisequalities] and [nullableDisequalities] include
 * *only* representatives of reference equivalence classes, i.e. only references x,
 * such that [equalReferences].find(x) == x.
 */
class UEqualityConstraints private constructor(
    internal val ctx: UContext<*>,
    private val equalReferences: DisjointSets<UHeapRef>,
    private val mutableDistinctReferences: MutableSet<UHeapRef>,
    private val mutableReferenceDisequalities: MutableMap<UHeapRef, MutableSet<UHeapRef>>,
    private val mutableNullableDisequalities: MutableMap<UHeapRef, MutableSet<UHeapRef>>,
) : UMergeable<UEqualityConstraints, MutableMergeGuard> {
    constructor(ctx: UContext<*>) : this(
        ctx,
        DisjointSets(representativeSelector = RefsRepresentativeSelector),
        mutableSetOf(ctx.nullRef),
        mutableMapOf(),
        mutableMapOf(),
    )

    internal val distinctReferences: Set<UHeapRef> get() = mutableDistinctReferences

    internal val referenceDisequalities: Map<UHeapRef, Set<UHeapRef>> get() = mutableReferenceDisequalities

    internal val nullableDisequalities: Map<UHeapRef, Set<UHeapRef>> get() = mutableNullableDisequalities

    /**
     * Determines whether a static ref could be assigned to a symbolic, according to additional information.
     */
    private lateinit var isStaticRefAssignableToSymbolic: (UConcreteHeapRef, USymbolicHeapRef) -> Boolean

    init {
        equalReferences.subscribe(::rename)
    }

    var isContradicting = false
        private set

    private fun contradiction() {
        isContradicting = true
        equalReferences.clear()
        mutableDistinctReferences.clear()
        mutableReferenceDisequalities.clear()
    }

    private fun containsReferenceDisequality(ref1: UHeapRef, ref2: UHeapRef): Boolean =
        referenceDisequalities[ref1]?.contains(ref2) ?: false

    private fun containsNullableDisequality(ref1: UHeapRef, ref2: UHeapRef) =
        nullableDisequalities[ref1]?.contains(ref2) ?: false

    /**
     * Returns if [ref1] is identical to [ref2] in *all* models.
     */
    internal fun areEqual(ref1: USymbolicHeapRef, ref2: USymbolicHeapRef): Boolean =
        equalReferences.connected(ref1, ref2)

    internal fun findRepresentative(ref: UHeapRef) = equalReferences.find(ref)

    private fun areDistinctRepresentatives(repr1: UHeapRef, repr2: UHeapRef): Boolean {
        if (repr1 == repr2) {
            return false
        }

        val distinctByClique = distinctReferences.contains(repr1) && distinctReferences.contains(repr2)
        return distinctByClique || containsReferenceDisequality(repr1, repr2)
    }

    /**
     * Returns if [ref1] is distinct from [ref2] in *all* models.
     */
    internal fun areDistinct(ref1: USymbolicHeapRef, ref2: USymbolicHeapRef): Boolean {
        val repr1 = findRepresentative(ref1)
        val repr2 = findRepresentative(ref2)

        return areDistinctRepresentatives(repr1, repr2)
    }

    /**
     * Adds an assertion that two symbolic refs are always equal.
     */
    internal fun makeEqual(firstSymbolicRef: USymbolicHeapRef, secondSymbolicRef: USymbolicHeapRef) {
        makeRefEqual(firstSymbolicRef, secondSymbolicRef)
    }

    /**
     * Adds an assertion that the symbolic ref [symbolicRef] always equals to the static ref [staticRef].
     */
    internal fun makeEqual(symbolicRef: USymbolicHeapRef, staticRef: UConcreteHeapRef) {
        requireStaticRef(staticRef)

        makeRefEqual(symbolicRef, staticRef)
    }

    private fun makeRefEqual(ref1: UHeapRef, ref2: UHeapRef) {
        if (isContradicting) {
            return
        }

        equalReferences.union(ref1, ref2)
        // Contradictions will be checked by rename listener.
    }

    /**
     * An important invariant of [distinctReferences] and [referenceDisequalities] is that they include *only*
     * representatives of reference equivalence classes, i.e. only references x such that [equalReferences].find(x) == x.
     * Here we react to merging of equivalence classes of [from] and [to] into one represented by [to], by eliminating
     * [from] and merging its disequality constraints into [to].
     */
    private fun rename(to: UHeapRef, from: UHeapRef) {
        if (distinctReferences.contains(from)) {
            if (distinctReferences.contains(to)) {
                contradiction()
                return
            }
            mutableDistinctReferences.remove(from)
            mutableDistinctReferences.add(to)
        }

        val fromDiseqs = referenceDisequalities[from]

        if (fromDiseqs != null) {
            if (fromDiseqs.contains(to)) {
                contradiction()
                return
            }

            mutableReferenceDisequalities.remove(from)
            fromDiseqs.forEach {
                mutableReferenceDisequalities[it]?.remove(from)
                makeRefNonEqual(to, it)
            }
        }

        val nullRepr = findRepresentative(ctx.nullRef)
        if (to == nullRepr) {
            // x == null satisfies nullable disequality (x !== y || (x == null && y == null))
            val removedFrom = mutableNullableDisequalities.remove(from)
            val removedTo = mutableNullableDisequalities.remove(to)
            removedFrom?.forEach {
                mutableNullableDisequalities[it]?.remove(from)
            }
            removedTo?.forEach {
                mutableNullableDisequalities[it]?.remove(to)
            }
        } else if (containsNullableDisequality(from, to)) {
            // If x === y, nullable disequality can hold only if both references are null
            makeRefEqual(to, nullRepr)
        } else {
            val removedFrom = mutableNullableDisequalities.remove(from)
            removedFrom?.forEach {
                mutableNullableDisequalities[it]?.remove(from)
                makeRefNonEqualOrBothNull(to, it)
            }
        }
    }

    private fun addDisequalityUnguarded(repr1: UHeapRef, repr2: UHeapRef) {
        when (distinctReferences.size) {
            0 -> {
                require(referenceDisequalities.isEmpty())
                // Init clique with {repr1, repr2}
                mutableDistinctReferences.add(repr1)
                mutableDistinctReferences.add(repr2)
                return
            }

            1 -> {
                val onlyRef = distinctReferences.single()
                if (repr1 == onlyRef) {
                    mutableDistinctReferences.add(repr2)
                    return
                }
                if (repr2 == onlyRef) {
                    mutableDistinctReferences.add(repr1)
                    return
                }
            }
        }

        val ref1InClique = distinctReferences.contains(repr1)
        val ref2InClique = distinctReferences.contains(repr2)

        if (ref1InClique && ref2InClique) {
            return
        }

        if (containsReferenceDisequality(repr1, repr2)) {
            return
        }

        if (ref1InClique || ref2InClique) {
            val refInClique = if (ref1InClique) repr1 else repr2
            val refNotInClique = if (ref1InClique) repr2 else repr1

            if (distinctReferences.all { it == refInClique || containsReferenceDisequality(refNotInClique, it) }) {
                // Ref is not in clique and disjoint from all refs in clique. Thus, we can join it to clique...
                mutableReferenceDisequalities[refNotInClique]?.removeAll(distinctReferences)

                for (ref in distinctReferences) {
                    mutableReferenceDisequalities[ref]?.remove(refNotInClique)
                }

                mutableDistinctReferences.add(refNotInClique)
                return
            }
        }

        (mutableReferenceDisequalities.getOrPut(repr1) { mutableSetOf() }).add(repr2)
        (mutableReferenceDisequalities.getOrPut(repr2) { mutableSetOf() }).add(repr1)
    }

    /**
     * Adds an assertion that between two [USymbolicHeapRef] refs that they are never equal.
     */
    internal fun makeNonEqual(symbolicRef1: USymbolicHeapRef, symbolicRef2: USymbolicHeapRef) {
        makeRefNonEqual(symbolicRef1, symbolicRef2)
    }

    /**
     * Adds an assertion that the symbolic ref [symbolicRef] is never equal to the static ref [staticRef].
     */
    internal fun makeNonEqual(symbolicRef: USymbolicHeapRef, staticRef: UConcreteHeapRef) {
        requireStaticRef(staticRef)

        makeRefNonEqual(symbolicRef, staticRef)
    }

    private fun makeRefNonEqual(ref1: UHeapRef, ref2: UHeapRef) {
        if (isContradicting) {
            return
        }

        if (isStaticHeapRef(ref1) && isStaticHeapRef(ref2) && ref1 != ref2) {
            // No need to do anything for static refs as they could not be equal
            return
        }

        val repr1 = findRepresentative(ref1)
        val repr2 = findRepresentative(ref2)

        if (repr1 == repr2) {
            contradiction()
            return
        }

        addDisequalityUnguarded(repr1, repr2)
        // Constraint (repr1 != repr2) is stronger than (repr1 != repr2) || (repr1 == repr2 == null), so no need to
        // keep the late one
        removeNullableDisequality(repr1, repr2)
    }

    /**
     * Adds an assertion that [ref1] is never equal to [ref2] or both are null.
     */
    internal fun makeNonEqualOrBothNull(ref1: USymbolicHeapRef, ref2: USymbolicHeapRef) {
        makeRefNonEqualOrBothNull(ref1, ref2)
    }

    private fun makeRefNonEqualOrBothNull(ref1: UHeapRef, ref2: UHeapRef) {
        if (isContradicting) {
            return
        }

        if (isStaticHeapRef(ref1) && isStaticHeapRef(ref2) && ref1 != ref2) {
            // No need to do anything for static refs as they could not be equal or null
            return
        }

        val repr1 = findRepresentative(ref1)
        val repr2 = findRepresentative(ref2)

        if (repr1 == repr2) {
            // In this case, (repr1 != repr2) || (repr1 == null && repr2 == null) is equivalent to (repr1 == null).
            makeRefEqual(repr1, ctx.nullRef)
            return
        }

        val nullRepr = findRepresentative(ctx.nullRef)
        if (repr1 == nullRepr || repr2 == nullRepr) {
            // In this case, (repr1 != repr2) || (repr1 == null && repr2 == null) always holds
            return
        }

        if (areDistinctRepresentatives(repr1, nullRepr) || areDistinctRepresentatives(repr2, nullRepr)) {
            // In this case, (repr1 != repr2) || (repr1 == null && repr2 == null) is simply (repr1 != repr2)
            addDisequalityUnguarded(repr1, repr2)
            return
        }

        (mutableNullableDisequalities.getOrPut(repr1) { mutableSetOf() }).add(repr2)
        (mutableNullableDisequalities.getOrPut(repr2) { mutableSetOf() }).add(repr1)
    }

    private fun removeNullableDisequality(repr1: UHeapRef, repr2: UHeapRef) {
        if (containsNullableDisequality(repr1, repr2)) {
            mutableNullableDisequalities[repr1]?.remove(repr2)
            mutableNullableDisequalities[repr2]?.remove(repr1)
        }
    }

    /**
     * Starts listening for the equivalence classes merging events.
     * When two equivalence classes with representatives x and y get merged into one with representative x,
     * [equalityCallback] (x, y) is called.
     * Note that the order of arguments matters: the first argument is a representative of the new equivalence class.
     */
    fun subscribeEquality(equalityCallback: (UHeapRef, UHeapRef) -> Unit) {
        equalReferences.subscribe(equalityCallback)
    }

    /**
     * Sets [isStaticRefAssignableToSymbolic] according to information from [UTypeConstraints].
     */
    fun setTypesCheck(isStaticRefAssignableToSymbolic: (UConcreteHeapRef, USymbolicHeapRef) -> Boolean) {
        this.isStaticRefAssignableToSymbolic = isStaticRefAssignableToSymbolic
    }

    /**
     * Given a newly allocated static ref [allocatedStaticRef], updates [distinctReferences] and
     * [mutableReferenceDisequalities] in the following way - removes all symbolic refs that may be equal to the
     * [allocatedStaticRef] (according to the [isStaticRefAssignableToSymbolic]) from the [distinctReferences] and
     * moves the information about disequality for these refs to the [mutableReferenceDisequalities].
     * After that, adds the [allocatedStaticRef] to the [mutableDistinctReferences].
     */
    internal fun updateDisequality(allocatedStaticRef: UConcreteHeapRef) {
        if (!isStaticHeapRef(allocatedStaticRef)) {
            return
        }

        // Move from the clique to the [mutableDistinctReferences]
        // all symbolic refs that are typely compatible with this static ref
        val referencesToRemove = distinctReferences.filter {
            it is USymbolicHeapRef && it !is UNullRef && isStaticRefAssignableToSymbolic(allocatedStaticRef, it)
        }

        // Here we need to save a copy of distinct refs to use all of them except the single ref from removed references
        val oldDistinctRefs = distinctReferences.toSet()

        for (ref in referencesToRemove) {
            val otherDistinctRefs = oldDistinctRefs - ref
            mutableDistinctReferences.remove(ref)

            mutableReferenceDisequalities.getOrPut(ref) { hashSetOf() }.addAll(otherDistinctRefs)
            otherDistinctRefs.forEach {
                mutableReferenceDisequalities.getOrPut(it) { hashSetOf() } += ref
            }
        }

        mutableDistinctReferences += allocatedStaticRef
    }

    /**
     * Creates a mutable copy of this structure.
     * Note that current subscribers get unsubscribed!
     */
    fun clone(): UEqualityConstraints {
        if (isContradicting) {
            val result = UEqualityConstraints(ctx, DisjointSets(), mutableSetOf(), mutableMapOf(), mutableMapOf())
            result.isContradicting = true
            return result
        }

        val newEqualReferences = equalReferences.clone()
        val newDistinctReferences = distinctReferences.toMutableSet()
        val newReferenceDisequalities = mutableMapOf<UHeapRef, MutableSet<UHeapRef>>()
        val newNullableDisequalities = mutableMapOf<UHeapRef, MutableSet<UHeapRef>>()

        referenceDisequalities.mapValuesTo(newReferenceDisequalities) { it.value.toMutableSet() }
        nullableDisequalities.mapValuesTo(newNullableDisequalities) { it.value.toMutableSet() }

        return UEqualityConstraints(
            ctx,
            newEqualReferences,
            newDistinctReferences,
            newReferenceDisequalities,
            newNullableDisequalities,
        )
    }

    private fun requireStaticRef(ref: UHeapRef) {
        require(isStaticHeapRef(ref)) {
            "Expected static ref but got $ref"
        }
    }

    override fun mergeWith(other: UEqualityConstraints, by: MutableMergeGuard): UEqualityConstraints? {
        // TODO: refactor it
        if (mutableDistinctReferences != other.mutableDistinctReferences) {
            return null
        }
        if (mutableReferenceDisequalities != other.mutableReferenceDisequalities) {
            return null
        }
        if (mutableNullableDisequalities != other.mutableNullableDisequalities) {
            return null
        }
        if (equalReferences != other.equalReferences) {
            return null
        }
        return this
    }

    fun constraints(translator: UExprTranslator<*, *>): Sequence<UBoolExpr> {
        var index = 1

        val result = mutableListOf<UBoolExpr>()

        val nullRepr = findRepresentative(ctx.nullRef)
        for (ref in distinctReferences) {
            // Static refs are already translated as a values of an uninterpreted sort
            if (isStaticHeapRef(ref)) {
                continue
            }

            val refIndex = if (ref == nullRepr) 0 else index++
            val translatedRef = translator.translate(ref)
            val preInterpretedValue = ctx.mkUninterpretedSortValue(ctx.addressSort, refIndex)
            result += ctx.mkEq(translatedRef, preInterpretedValue)
        }

        for ((key, value) in equalReferences) {
            val translatedLeft = translator.translate(key)
            val translatedRight = translator.translate(value)
            result += ctx.mkEq(translatedLeft, translatedRight)
        }

        val processedConstraints = mutableSetOf<Pair<UHeapRef, UHeapRef>>()

        for ((ref1, disequalRefs) in referenceDisequalities.entries) {
            for (ref2 in disequalRefs) {
                if (!processedConstraints.contains(ref2 to ref1)) {
                    processedConstraints.add(ref1 to ref2)
                    val translatedRef1 = translator.translate(ref1)
                    val translatedRef2 = translator.translate(ref2)
                    result += ctx.mkNot(ctx.mkEq(translatedRef1, translatedRef2))
                }
            }
        }

        processedConstraints.clear()
        val translatedNull = translator.transform(ctx.nullRef)
        for ((ref1, disequalRefs) in nullableDisequalities.entries) {
            for (ref2 in disequalRefs) {
                if (!processedConstraints.contains(ref2 to ref1)) {
                    processedConstraints.add(ref1 to ref2)
                    val translatedRef1 = translator.translate(ref1)
                    val translatedRef2 = translator.translate(ref2)

                    val disequalityConstraint = ctx.mkNot(ctx.mkEq(translatedRef1, translatedRef2))
                    val nullConstraint1 = ctx.mkEq(translatedRef1, translatedNull)
                    val nullConstraint2 = ctx.mkEq(translatedRef2, translatedNull)
                    result += ctx.mkOr(disequalityConstraint, ctx.mkAnd(nullConstraint1, nullConstraint2))
                }
            }
        }

        return result.asSequence()
    }

    /**
     * A representative selector that prefers static refs over all others, and the null ref over other symbolic.
     */
    private object RefsRepresentativeSelector : DisjointSets.RepresentativeSelector<UHeapRef> {
        override fun shouldSelectAsRepresentative(value: UHeapRef): Boolean =
            isStaticHeapRef(value) || value is UNullRef
    }
}
