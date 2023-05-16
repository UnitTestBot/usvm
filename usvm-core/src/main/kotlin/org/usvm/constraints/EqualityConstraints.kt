package org.usvm.constraints

import org.usvm.UHeapRef
import org.usvm.util.DisjointSets

/**
 * Represents equality constraints between heap references.
 * Stores equivalence classes into union-find data structure [equalReferences].
 * Maintains graph of disequality constraints. Tries to detect (or at least approximate) maximal set of distinct heap references
 * by fast-check of clique in disequality graph (not exponential!) (see [distinctReferences]).
 * All the rest disequalities (i.e., outside of the maximal clique) are stored into [referenceDisequalities].
 *
 * @note Important invariant: [distinctReferences] and [referenceDisequalities] include *only*
 * representatives of reference equivalence classes, i.e. only references x such that [equalReferences].find(x) == x.
 */
class UEqualityConstraints private constructor(
    val equalReferences: DisjointSets<UHeapRef>,
    private val mutableDistinctReferences: MutableSet<UHeapRef>,
    private val mutableReferenceDisequalities: MutableMap<UHeapRef, MutableSet<UHeapRef>>,
) {
    constructor(): this(DisjointSets(), mutableSetOf(), mutableMapOf())

    val distinctReferences: Set<UHeapRef> = mutableDistinctReferences

    val referenceDisequalities: Map<UHeapRef, Set<UHeapRef>> = mutableReferenceDisequalities

    init {
        equalReferences.subscribe(::rename)
    }

    var isContradiction = false
        private set

    private fun contradiction() {
        isContradiction = true
        equalReferences.clear()
        mutableDistinctReferences.clear()
        mutableReferenceDisequalities.clear()
    }

    private fun containsReferenceDisequality(ref1: UHeapRef, ref2: UHeapRef) =
        referenceDisequalities[ref1]?.contains(ref2) ?: false

    /**
     * Returns if [ref1] is identical to [ref2] in *all* models.
     */
    fun areEqual(ref1: UHeapRef, ref2: UHeapRef) =
        equalReferences.connected(ref1, ref2)

    /**
     * Returns if [ref1] is distinct from [ref2] in *all* models.
     */
    fun areDistinct(ref1: UHeapRef, ref2: UHeapRef): Boolean {
        val repr1 = equalReferences.find(ref1)
        val repr2 = equalReferences.find(ref2)
        if (repr1 == repr2) {
            return false
        }

        val distinctByClique = distinctReferences.contains(repr1) && distinctReferences.contains(repr2)
        return distinctByClique || containsReferenceDisequality(repr1, repr2)
    }

    /**
     * Adds an assertion that [ref1] is always equal to [ref2].
     */
    fun addReferenceEquality(ref1: UHeapRef, ref2: UHeapRef) {
        if (isContradiction) {
            return
        }

        if (areDistinct(ref1, ref2)) {
            contradiction()
            return
        }

        equalReferences.union(ref1, ref2)
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

        val fromDiseqs = referenceDisequalities[from] ?: return

        if (fromDiseqs.contains(to)) {
            contradiction()
            return
        }

        mutableReferenceDisequalities.remove(from)
        fromDiseqs.forEach {
            mutableReferenceDisequalities[it]?.remove(from)
            addReferenceDisequality(to, it)
        }
    }

    /**
     * Adds an assertion that [ref1] is never equal to [ref2].
     */
    fun addReferenceDisequality(ref1: UHeapRef, ref2: UHeapRef) {
        if (isContradiction) {
            return
        }

        val repr1 = equalReferences.find(ref1)
        val repr2 = equalReferences.find(ref2)

        if (repr1 == repr2) {
            contradiction()
            return
        }

        if (distinctReferences.isEmpty()) {
            require(referenceDisequalities.isEmpty())
            // Init clique with {repr1, repr2}
            mutableDistinctReferences.add(repr1)
            mutableDistinctReferences.add(repr2)
            return
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
     * Starts listening for the equivalence classes merging events.
     * When two equivalence classes with representatives x and y get merged into one with representative x,
     * [equalityCallback] (x, y) is called.
     * Note that the order of arguments matters: the first argument is a representative of the new equivalence class.
     */
    fun subscribe(equalityCallback: (UHeapRef, UHeapRef) -> Unit) {
        equalReferences.subscribe(equalityCallback)
    }

    /**
     * Creates a mutable copy of this structure.
     * Note that current subscribers get unsubscribed!
     */
    fun clone(): UEqualityConstraints {
        if (isContradiction) {
            val result = UEqualityConstraints(DisjointSets(), mutableSetOf(), mutableMapOf())
            result.isContradiction = true
            return result
        }

        val newEqualReferences = equalReferences.clone()
        val newDistinctReferences = distinctReferences.toMutableSet()
        val newReferenceDisequalities = mutableMapOf<UHeapRef, MutableSet<UHeapRef>>()

        referenceDisequalities.mapValuesTo(newReferenceDisequalities) { it.value.toMutableSet() }

        return UEqualityConstraints(newEqualReferences, newDistinctReferences, newReferenceDisequalities)
    }
}
