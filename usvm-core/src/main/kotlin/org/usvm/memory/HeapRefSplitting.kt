package org.usvm.memory

import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.USymbolicHeapRef
import org.usvm.isFalse
import org.usvm.isStaticHeapRef
import org.usvm.uctx

data class GuardedExpr<out T>(
    val expr: T,
    val guard: UBoolExpr,
)

infix fun <T> T.with(guard: UBoolExpr) = GuardedExpr(this, guard)

infix fun <T> GuardedExpr<T>.withAlso(guard: UBoolExpr) = GuardedExpr(expr, guard.ctx.mkAnd(this.guard, guard))

/**
 * @param concreteHeapRefs a list of split concrete heap refs with their guards.
 * @param symbolicHeapRef an ite made of all [USymbolicHeapRef]s with its guard, the single [USymbolicHeapRef] if it's
 * the single [USymbolicHeapRef] in the base expression or `null` if there are no [USymbolicHeapRef]s at all.
 */
data class SplitHeapRefs(
    val concreteHeapRefs: List<GuardedExpr<UConcreteHeapRef>>,
    val symbolicHeapRef: List<GuardedExpr<UHeapRef>>,
)

/**
 * Traverses the [ref] non-recursively and collects allocated [UConcreteHeapRef]s and [USymbolicHeapRef] with static
 * [UConcreteHeapRef] as well as guards for them. If the object is not a [UConcreteHeapRef] nor a [USymbolicHeapRef],
 * e.g. KConst<UAddressSort>, treats such an object as a [USymbolicHeapRef].
 *
 * The result [SplitHeapRefs.symbolicHeapRef] will be `null` if there are no [USymbolicHeapRef]s as
 * leafs in the [ref] ite. Otherwise, it will contain an ite with the guard protecting from bubbled up concrete refs.
 *
 * @param initialGuard an initial value for the accumulated guard.
 * @param ignoreNullRefs if true, then null references will be ignored. It means that all leafs with nulls
 * considered unsatisfiable, so we assume their guards equal to false, and they won't be added to the result.
 * @param collapseHeapRefs if true, then collapses all [USymbolicHeapRef]s (or static refs) to the one [UIteExpr].
 * Otherwise, collects all [USymbolicHeapRef]s (and static refs) with their guards.
 * @param staticIsConcrete if true, then collects static refs as concrete heap refs.
 */
fun splitUHeapRef(
    ref: UHeapRef,
    initialGuard: UBoolExpr = ref.ctx.trueExpr,
    ignoreNullRefs: Boolean = true,
    collapseHeapRefs: Boolean = true,
    staticIsConcrete: Boolean = false,
): SplitHeapRefs {
    val concreteHeapRefs = mutableListOf<GuardedExpr<UConcreteHeapRef>>()
    val symbolicHeapRefs = mutableListOf<GuardedExpr<UHeapRef>>()

    val symbolicHeapRef = filter(ref, initialGuard, ignoreNullRefs) { guarded ->
        val expr = guarded.expr

        // Static refs may alias symbolic refs so they should be not filtered out
        if (expr is UConcreteHeapRef && (staticIsConcrete || !isStaticHeapRef(expr))) {
            @Suppress("UNCHECKED_CAST")
            concreteHeapRefs += guarded as GuardedExpr<UConcreteHeapRef>
            false
        } else {
            if (collapseHeapRefs) {
                true
            } else {
                symbolicHeapRefs += guarded
                false
            }
        }
    }

    if (collapseHeapRefs && symbolicHeapRef != null) {
        symbolicHeapRefs += symbolicHeapRef
    }

    return SplitHeapRefs(
        concreteHeapRefs,
        symbolicHeapRefs,
    )
}

/**
 * Accumulates value starting with [initial], traversing [ref], accumulating guards and applying the [blockOnConcrete]
 * on allocated [UConcreteHeapRef]s, and [blockOnSymbolic] on
 * [USymbolicHeapRef]. An argument for the [blockOnSymbolic] is obtained by removing all concrete heap refs from the [ref] if it's ite.
 *
 * @param initialGuard the initial value fot the guard to be passed to [blockOnConcrete] and [blockOnSymbolic].
 * @param ignoreNullRefs if true, then null references will be ignored. It means that all leafs with nulls
 * considered unsatisfiable, so we assume their guards equal to false.
 * @param collapseHeapRefs see docs in [splitUHeapRef].
 * @param staticIsConcrete apply [blockOnConcrete] or [blockOnSymbolic] on static [UConcreteHeapRef]s.
 */
inline fun <R> foldHeapRef(
    ref: UHeapRef,
    initial: R,
    initialGuard: UBoolExpr,
    ignoreNullRefs: Boolean = true,
    collapseHeapRefs: Boolean = true,
    staticIsConcrete: Boolean = false,
    blockOnConcrete: (R, GuardedExpr<UConcreteHeapRef>) -> R,
    blockOnSymbolic: (R, GuardedExpr<UHeapRef>) -> R,
): R {
    if (initialGuard.isFalse) {
        return initial
    }

    return when {
        isStaticHeapRef(ref) -> if (staticIsConcrete) {
            blockOnConcrete(initial, ref with initialGuard)
        } else {
            blockOnSymbolic(initial, ref with initialGuard)
        }
        ref is UConcreteHeapRef -> blockOnConcrete(initial, ref with initialGuard)
        ref is UNullRef -> if (!ignoreNullRefs) {
            blockOnSymbolic(initial, ref with initialGuard)
        } else {
            initial
        }
        ref is USymbolicHeapRef -> blockOnSymbolic(initial, ref with initialGuard)
        ref is UIteExpr<UAddressSort> -> {
            val (concreteHeapRefs, symbolicHeapRefs) = splitUHeapRef(
                ref,
                initialGuard,
                collapseHeapRefs = collapseHeapRefs,
                staticIsConcrete = staticIsConcrete
            )

            var acc = initial
            symbolicHeapRefs.forEach { (ref, guard) -> acc = blockOnSymbolic(acc, ref with guard) }
            concreteHeapRefs.forEach { (ref, guard) -> acc = blockOnConcrete(acc, ref with guard) }
            acc
        }

        else -> error("Unexpected ref: $ref")
    }
}

/**
 * Executes [foldHeapRef] with passed [blockOnSymbolic] as a blockOnStatic.
 */
inline fun <R> foldHeapRefWithStaticAsSymbolic(
    ref: UHeapRef,
    initial: R,
    initialGuard: UBoolExpr,
    ignoreNullRefs: Boolean = true,
    collapseHeapRefs: Boolean = true,
    blockOnConcrete: (R, GuardedExpr<UConcreteHeapRef>) -> R,
    blockOnSymbolic: (R, GuardedExpr<UHeapRef>) -> R,
): R = foldHeapRef(
    ref,
    initial,
    initialGuard,
    ignoreNullRefs,
    collapseHeapRefs,
    staticIsConcrete = false,
    blockOnConcrete = blockOnConcrete,
    blockOnSymbolic = blockOnSymbolic
)

inline fun <R> foldHeapRef2(
    ref0: UHeapRef,
    ref1: UHeapRef,
    initial: R,
    initialGuard: UBoolExpr,
    ignoreNullRefs: Boolean = true,
    blockOnConcrete0Concrete1: (R, UConcreteHeapRef, UConcreteHeapRef, UBoolExpr) -> R,
    blockOnConcrete0Symbolic1: (R, UConcreteHeapRef, UHeapRef, UBoolExpr) -> R,
    blockOnSymbolic0Concrete1: (R, UHeapRef, UConcreteHeapRef, UBoolExpr) -> R,
    blockOnSymbolic0Symbolic1: (R, UHeapRef, UHeapRef, UBoolExpr) -> R,
): R = foldHeapRefWithStaticAsSymbolic(
    ref = ref0,
    initial = initial,
    initialGuard = initialGuard,
    ignoreNullRefs = ignoreNullRefs,
    blockOnConcrete = { r0, (concrete0, guard0) ->
        foldHeapRefWithStaticAsSymbolic(
            ref = ref1,
            initial = r0,
            initialGuard = guard0,
            ignoreNullRefs = ignoreNullRefs,
            blockOnConcrete = { r1, (concrete1, guard1) ->
                blockOnConcrete0Concrete1(r1, concrete0, concrete1, guard1)
            },
            blockOnSymbolic = { r1, (inputRef1, guard1) ->
                blockOnConcrete0Symbolic1(r1, concrete0, inputRef1, guard1)
            }
        )
    },
    blockOnSymbolic = { r0, (inputRef0, guard0) ->
        foldHeapRefWithStaticAsSymbolic(
            ref = ref1,
            initial = r0,
            initialGuard = guard0,
            ignoreNullRefs = ignoreNullRefs,
            blockOnConcrete = { r1, (concrete1, guard1) ->
                blockOnSymbolic0Concrete1(r1, inputRef0, concrete1, guard1)
            },
            blockOnSymbolic = { r1, (inputRef1, guard1) ->
                blockOnSymbolic0Symbolic1(r1, inputRef0, inputRef1, guard1)
            }
        )
    },
)

private const val LEFT_CHILD = 0
private const val RIGHT_CHILD = 1
private const val DONE = 2


/**
 * Reassembles [this] non-recursively with applying [concreteMapper] on allocated [UConcreteHeapRef], [staticMapper] on
 * static [UConcreteHeapRef], and [symbolicMapper] on [USymbolicHeapRef]. Respects [UIteExpr], so the structure of
 * the result expression will be the same as [this] is, but implicit simplifications may occur.
 *
 * @param ignoreNullRefs if true, then null references will be ignored. It means that all leafs with nulls
 * considered unsatisfiable, so we assume their guards equal to false. If [ignoreNullRefs] is true and [this] is
 * [UNullRef], throws an [IllegalArgumentException].
 */
internal inline fun <Sort : USort> UHeapRef.map(
    concreteMapper: (UConcreteHeapRef) -> UExpr<Sort>,
    staticMapper: (UConcreteHeapRef) -> UExpr<Sort>,
    symbolicMapper: (USymbolicHeapRef) -> UExpr<Sort>,
    ignoreNullRefs: Boolean = true,
): UExpr<Sort> = when {
    isStaticHeapRef(this) -> staticMapper(this)
    this is UConcreteHeapRef -> concreteMapper(this)
    this is UNullRef -> {
        require(!ignoreNullRefs) { "Got nullRef on the top!" }
        symbolicMapper(this)
    }

    this is USymbolicHeapRef -> symbolicMapper(this)
    this is UIteExpr<UAddressSort> -> {
        /**
         * This code simulates DFS on a binary tree without an explicit recursion. Pair.second represents the first
         * unprocessed child of the pair.first (`0` means the left child, `1` means the right child).
         */
        val nodeToChild = mutableListOf<Pair<UHeapRef, Int>>()
        val completelyMapped = mutableListOf<UExpr<Sort>>()

        nodeToChild.add(this to LEFT_CHILD)


        while (nodeToChild.isNotEmpty()) {
            val (ref, state) = nodeToChild.removeLast()
            when {
                isStaticHeapRef(ref) -> completelyMapped += staticMapper(ref)
                ref is UConcreteHeapRef -> completelyMapped += concreteMapper(ref)
                ref is USymbolicHeapRef -> completelyMapped += symbolicMapper(ref)
                ref is UIteExpr<UAddressSort> -> {

                    when (state) {
                        LEFT_CHILD -> {
                            when {
                                ignoreNullRefs && ref.trueBranch == uctx.nullRef -> {
                                    nodeToChild += ref.falseBranch to LEFT_CHILD
                                }

                                ignoreNullRefs && ref.falseBranch == uctx.nullRef -> {
                                    nodeToChild += ref.trueBranch to LEFT_CHILD
                                }

                                else -> {
                                    nodeToChild += ref to RIGHT_CHILD
                                    nodeToChild += ref.trueBranch to LEFT_CHILD
                                }
                            }
                        }

                        RIGHT_CHILD -> {
                            nodeToChild += ref to DONE
                            nodeToChild += ref.falseBranch to LEFT_CHILD
                        }

                        DONE -> {
                            // we firstly process the left child of [cur], so it will be under the top of the stack
                            // the top of the stack will be the right child
                            val rhs = completelyMapped.removeLast()
                            val lhs = completelyMapped.removeLast()
                            completelyMapped += ctx.mkIte(ref.condition, lhs, rhs)
                        }
                    }
                }
            }
        }

        completelyMapped.single()
    }

    else -> error("Unexpected ref: $this")
}

/**
 * Executes [foldHeapRef] with passed [concreteMapper] as a staticMapper.
 */
internal inline fun <Sort : USort> UHeapRef.mapWithStaticAsConcrete(
    concreteMapper: (UConcreteHeapRef) -> UExpr<Sort>,
    symbolicMapper: (USymbolicHeapRef) -> UExpr<Sort>,
    ignoreNullRefs: Boolean = true,
): UExpr<Sort> = map(
    concreteMapper,
    staticMapper = concreteMapper,
    symbolicMapper,
    ignoreNullRefs
)

/**
 * Executes [foldHeapRef] with passed [symbolicMapper] as a staticMapper.
 */
internal inline fun <Sort : USort> UHeapRef.mapWithStaticAsSymbolic(
    concreteMapper: (UConcreteHeapRef) -> UExpr<Sort>,
    symbolicMapper: (UHeapRef) -> UExpr<Sort>,
    ignoreNullRefs: Boolean = true,
): UExpr<Sort> = map(
    concreteMapper,
    staticMapper = symbolicMapper,
    symbolicMapper,
    ignoreNullRefs
)

/**
 * Filters [ref] non-recursively with [predicate] and returns the result. A guard in the argument of the
 * [predicate] consists of a predicate from the root to the passed leaf.
 *
 * Guarantees that [predicate] will be called exactly once on each leaf.
 *
 * @return A guarded expression with the guard indicating that any leaf on which [predicate] returns `false`
 * is inaccessible. `Null` is returned when all leafs match [predicate].
 */
internal inline fun filter(
    ref: UHeapRef,
    initialGuard: UBoolExpr,
    ignoreNullRefs: Boolean,
    predicate: (GuardedExpr<UHeapRef>) -> Boolean,
): GuardedExpr<UHeapRef>? = with(ref.ctx) {
    when (ref) {
        is UIteExpr<UAddressSort> -> {
            /**
             * This code simulates DFS on a binary tree without an explicit recursion. Pair.second represents the first
             * unprocessed child of the pair.first, or all childs processed if pair.second equals [DONE].
             */
            val nodeToChild = mutableListOf<Pair<GuardedExpr<UHeapRef>, Int>>()
            val completelyMapped = mutableListOf<GuardedExpr<UHeapRef>?>()

            nodeToChild.add((ref with initialGuard) to LEFT_CHILD)

            while (nodeToChild.isNotEmpty()) {
                val (guarded, state) = nodeToChild.removeLast()
                val (cur, guardFromTop) = guarded
                when (cur) {
                    is UIteExpr<UAddressSort> -> when (state) {
                        LEFT_CHILD -> {
                            when {
                                ignoreNullRefs && cur.trueBranch == cur.uctx.nullRef -> {
                                    nodeToChild += (cur.falseBranch with guardFromTop) to LEFT_CHILD
                                }

                                ignoreNullRefs && cur.falseBranch == cur.uctx.nullRef -> {
                                    nodeToChild += (cur.trueBranch with guardFromTop) to LEFT_CHILD
                                }

                                else -> {
                                    nodeToChild += guarded to RIGHT_CHILD
                                    val leftGuard = mkAnd(guardFromTop, cur.condition, flat = false)
                                    nodeToChild += (cur.trueBranch with leftGuard) to LEFT_CHILD
                                }
                            }
                        }

                        RIGHT_CHILD -> {
                            nodeToChild += guarded to DONE
                            val guardRhs = mkAnd(guardFromTop, !cur.condition, flat = false)
                            nodeToChild += (cur.falseBranch with guardRhs) to LEFT_CHILD
                        }

                        DONE -> {
                            // we firstly process the left child of [cur], so it will be under the top of the stack
                            // the top of the stack will be the right child
                            val rhs = completelyMapped.removeLast()
                            val lhs = completelyMapped.removeLast()
                            val next = when {
                                lhs != null && rhs != null -> {
                                    val leftPart = mkOr(!cur.condition, lhs.guard, flat = false)

                                    val rightPart = mkOr(cur.condition, rhs.guard, flat = false)

                                    /**
                                     *```
                                     *           cur.condition | guard = ( cur.condition -> lhs.guard) &&
                                     *             /        \            (!cur.condition -> rhs.guard)
                                     *            /          \
                                     *           /            \
                                     *          /              \
                                     *         /                \
                                     * lhs.expr | lhs.guard   rhs.expr | rhs.guard
                                     *```
                                     */
                                    val guard = mkAnd(leftPart, rightPart, flat = false)
                                    mkIte(cur.condition, lhs.expr, rhs.expr) with guard
                                }

                                lhs != null -> {
                                    val guard = mkAnd(cur.condition, lhs.guard, flat = false)
                                    lhs.expr with guard
                                }

                                rhs != null -> {
                                    val guard = mkAnd(!cur.condition, rhs.guard, flat = false)
                                    rhs.expr with guard
                                }

                                else -> null
                            }
                            completelyMapped += next
                        }
                    }
                    // USymbolicHeapRef, UConcreteHeapRef, KConst<UAddressSort>
                    else -> completelyMapped += (cur with trueExpr).takeIf { predicate(cur with guardFromTop) }
                }

            }

            completelyMapped.single()?.withAlso(initialGuard)
        }
        else -> if (ref != ref.uctx.nullRef || !ignoreNullRefs) {
            (ref with initialGuard).takeIf(predicate)
        } else {
            null
        }
    }
}
