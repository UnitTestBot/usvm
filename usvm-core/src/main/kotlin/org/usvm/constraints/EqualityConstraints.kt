package org.usvm.constraints

import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USymbolicHeapRef
import org.usvm.isStaticInitializedConcreteHeapRef
import org.usvm.isSymbolicHeapRef
import org.usvm.util.DisjointSets

/**
 * Represents equality constraints between heap references - symbolic or statically initialized concrete. There are three kinds of constraints:
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
    private val ctx: UContext,
    val equalReferences: DisjointSets<UHeapRef>,
    private val mutableDistinctReferences: MutableSet<UHeapRef>,
    private val mutableReferenceDisequalities: MutableMap<UHeapRef, MutableSet<UHeapRef>>,
    private val mutableNullableDisequalities: MutableMap<UHeapRef, MutableSet<UHeapRef>>,
) {
    constructor(ctx: UContext) : this(ctx, DisjointSets(), mutableSetOf(ctx.nullRef), mutableMapOf(), mutableMapOf())

    val distinctReferences: Set<UHeapRef> = mutableDistinctReferences

    val referenceDisequalities: Map<UHeapRef, Set<UHeapRef>> = mutableReferenceDisequalities

    val nullableDisequalities: Map<UHeapRef, Set<UHeapRef>> = mutableNullableDisequalities

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
        checkReferencesCorrectness(ref1, ref2) {
            referenceDisequalities[ref1]?.contains(ref2) ?: false
        }

    private fun containsNullableDisequality(ref1: UHeapRef, ref2: UHeapRef) =
        checkReferencesCorrectness(ref1, ref2) {
            nullableDisequalities[ref1]?.contains(ref2) ?: false
        }

    /**
     * Returns if [ref1] is identical to [ref2] in *all* models.
     */
    internal fun areEqual(ref1: UHeapRef, ref2: UHeapRef) =
        checkReferencesCorrectness(ref1, ref2) {
            equalReferences.connected(ref1, ref2)
        }

    /**
     * Returns if [ref] is null in all models.
     */
    internal fun isNull(ref: USymbolicHeapRef) = areEqual(ctx.nullRef, ref)

    private fun areDistinctRepresentatives(repr1: UHeapRef, repr2: UHeapRef): Boolean {
        checkReferencesCorrectness(repr1, repr2) {
            if (repr1 == repr2) {
                return false
            }

            val distinctByClique = distinctReferences.contains(repr1) && distinctReferences.contains(repr2)
            return distinctByClique || containsReferenceDisequality(repr1, repr2)
        }
    }

    /**
     * Returns if [ref1] is distinct from [ref2] in *all* models.
     */
    internal fun areDistinct(ref1: UHeapRef, ref2: UHeapRef): Boolean {
        checkReferencesCorrectness(ref1, ref2) {
            val repr1 = equalReferences.find(ref1)
            val repr2 = equalReferences.find(ref2)
            return areDistinctRepresentatives(repr1, repr2)
        }
    }

    /**
     * Returns if [ref] is not null in all models.
     */
    internal fun isNotNull(ref: UConcreteHeapRef) = areDistinct(ctx.nullRef, ref)

    /**
     * Adds an assertion that [ref1] is always equal to [ref2].
     */
    internal fun makeEqual(ref1: UHeapRef, ref2: UHeapRef) {
        checkReferencesCorrectness(ref1, ref2) {
            if (isContradicting) {
                return
            }

            equalReferences.union(ref1, ref2)
            // Contradictions will be checked by rename listener.
        }
    }

    /**
     * An important invariant of [distinctReferences] and [referenceDisequalities] is that they include *only*
     * representatives of reference equivalence classes, i.e. only references x such that [equalReferences].find(x) == x.
     * Here we react to merging of equivalence classes of [from] and [to] into one represented by [to], by eliminating
     * [from] and merging its disequality constraints into [to].
     */
    private fun rename(to: UHeapRef, from: UHeapRef) {
        checkReferencesCorrectness(to, from) {
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
                    makeNonEqual(to, it)
                }
            }

            val nullRepr = equalReferences.find(ctx.nullRef)
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
                makeEqual(to, nullRepr)
            } else {
                val removedFrom = mutableNullableDisequalities.remove(from)
                removedFrom?.forEach {
                    mutableNullableDisequalities[it]?.remove(from)
                    makeNonEqualOrBothNull(to, it)
                }
            }
        }
    }

    private fun addDisequalityUnguarded(repr1: UHeapRef, repr2: UHeapRef) {
        checkReferencesCorrectness(repr1, repr2) {
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
    }

    /**
     * Adds an assertion that [ref1] is never equal to [ref2].
     */
    internal fun makeNonEqual(ref1: UHeapRef, ref2: UHeapRef) {
        checkReferencesCorrectness(ref1, ref2) {
            if (isContradicting) {
                return
            }

            if (isStaticInitializedConcreteHeapRef(ref1) && isStaticInitializedConcreteHeapRef(ref2) && ref1 != ref2) {
                // Different static refs could not be equal
                return
            }

            val repr1 = equalReferences.find(ref1)
            val repr2 = equalReferences.find(ref2)

            if (repr1 == repr2) {
                contradiction()
                return
            }

            addDisequalityUnguarded(repr1, repr2)
            // Constraint (repr1 != repr2) is stronger than (repr1 != repr2) || (repr1 == repr2 == null), so no need to
            // keep the late one
            removeNullableDisequality(repr1, repr2)
        }
    }

    /**
     * Adds an assertion that [ref1] is never equal to [ref2] or both are null.
     */
    internal fun makeNonEqualOrBothNull(ref1: UHeapRef, ref2: UHeapRef) {
        checkReferencesCorrectness(ref1, ref2) {
            if (isContradicting) {
                return
            }

            val repr1 = equalReferences.find(ref1)
            val repr2 = equalReferences.find(ref2)

            if (repr1 == repr2) {
                // In this case, (repr1 != repr2) || (repr1 == null && repr2 == null) is equivalent to (repr1 == null).
                makeEqual(repr1, ctx.nullRef)
                return
            }

            val nullRepr = equalReferences.find(ctx.nullRef)
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
    }

    private fun removeNullableDisequality(repr1: UHeapRef, repr2: UHeapRef) {
        checkReferencesCorrectness(repr1, repr2) {
            if (containsNullableDisequality(repr1, repr2)) {
                mutableNullableDisequalities[repr1]?.remove(repr2)
                mutableNullableDisequalities[repr2]?.remove(repr1)
            }
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

    fun setTypesCheck(isStaticRefAssignableToSymbolic: (UConcreteHeapRef, USymbolicHeapRef) -> Boolean) {
        this.isStaticRefAssignableToSymbolic = isStaticRefAssignableToSymbolic
    }

    // TODO docs
    internal fun updateDisequality(allocatedStaticRef: UConcreteHeapRef) {
        if (!isStaticInitializedConcreteHeapRef(allocatedStaticRef)) {
            return
        }

        val referencesToRemove = distinctReferences.filter {
            it is USymbolicHeapRef && it !is UNullRef && isStaticRefAssignableToSymbolic(allocatedStaticRef, it)
        }
        for (ref in referencesToRemove) {
            val otherDistinctRefs = distinctReferences - ref
            mutableDistinctReferences.remove(ref)

            mutableReferenceDisequalities.getOrPut(ref) { mutableSetOf() }.addAll(otherDistinctRefs)

            // TODO do we need to add (forall distinctRef in otherDistinctRefs: mutableReferenceDisequalities[distinctRef] += ref)?
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
            newNullableDisequalities
        )
    }

    private inline fun <T> checkReferencesCorrectness(vararg references: UHeapRef, block: () -> T): T {
        references.forEach { ref ->
            require(isSymbolicHeapRef(ref) || isStaticInitializedConcreteHeapRef(ref)) {
                "Unexpected concrete ref $ref"
            }
        }

        return block()
    }
}
