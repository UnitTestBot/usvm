package org.usvm

data class GuardedExpr<out T>(
    val expr: T,
    val guard: UBoolExpr,
)

infix fun <T> T.with(guard: UBoolExpr) = GuardedExpr(this, guard)

internal data class SplitHeapRefs(
    val concreteHeapRefs: List<GuardedExpr<UConcreteHeapRef>>,
    val symbolicHeapRef: GuardedExpr<UHeapRef>?,
)

/**
 * Traverses the [ref] non-recursively and collects [UConcreteHeapRef]s and [USymbolicHeapRef] as well as
 * guards for them. The result [SplitHeapRefs.symbolicHeapRef] will be `null` if there are no [USymbolicHeapRef]s as
 * leafs in the [ref] ite. Otherwise, it will contain an ite with the guard protecting from bubbled up concrete refs.
 *
 * @param initialGuard an initial value for the accumulated guard.
 */
internal fun splitUHeapRef(ref: UHeapRef, initialGuard: UBoolExpr = ref.ctx.trueExpr): SplitHeapRefs {
    val concreteHeapRefs = mutableListOf<GuardedExpr<UConcreteHeapRef>>()

    val symbolicHeapRef = filter(ref, initialGuard) { guarded ->
        if (guarded.expr is USymbolicHeapRef) {
            true
        } else {
            @Suppress("UNCHECKED_CAST")
            concreteHeapRefs += (guarded as GuardedExpr<UConcreteHeapRef>)
            false
        }
    }

    return SplitHeapRefs(
        concreteHeapRefs,
        symbolicHeapRef,
    )
}

/**
 * Traverses the [ref], accumulating guards and applying the [blockOnConcrete] on [UConcreteHeapRef]s and
 * [blockOnSymbolic] on [USymbolicHeapRef]. An argument for the [blockOnSymbolic] is obtained by removing all concrete
 * heap refs from the [ref] if it's ite.
 *
 * @param initialGuard the initial value fot the guard to be passed to [blockOnConcrete] and [blockOnSymbolic].
 */
internal inline fun withHeapRef(
    ref: UHeapRef,
    initialGuard: UBoolExpr,
    crossinline blockOnConcrete: (GuardedExpr<UConcreteHeapRef>) -> Unit,
    crossinline blockOnSymbolic: (GuardedExpr<UHeapRef>) -> Unit,
) {
    if (initialGuard.isFalse) {
        return
    }

    when (ref) {
        is UConcreteHeapRef -> blockOnConcrete(ref with initialGuard)
        is USymbolicHeapRef -> blockOnSymbolic(ref with initialGuard)
        is UIteExpr<UAddressSort> -> {
            val (concreteHeapRefs, symbolicHeapRef) = splitUHeapRef(ref, initialGuard)

            symbolicHeapRef?.let { (ref, guard) -> blockOnSymbolic(ref with guard) }
            concreteHeapRefs.onEach { (ref, guard) -> blockOnConcrete(ref with guard) }
        }

        else -> error("Unexpected ref: $ref")
    }
}


/**
 * Reassembles [this] non-recursively with applying [concreteMapper] on [UConcreteHeapRef] and
 * [symbolicMapper] on [USymbolicHeapRef]. Respects [UIteExpr], so the structure of the result expression will be
 * the same as [this] is, but implicit simplifications may occur.
 */
internal inline fun <Sort : USort> UHeapRef.map(
    crossinline concreteMapper: (UConcreteHeapRef) -> UExpr<Sort>,
    crossinline symbolicMapper: (USymbolicHeapRef) -> UExpr<Sort>,
): UExpr<Sort> = when (this) {
    is UConcreteHeapRef -> concreteMapper(this)
    is USymbolicHeapRef -> symbolicMapper(this)
    is UIteExpr<UAddressSort> -> {
        /**
         * This code simulates DFS on a binary tree without an explicit recursion. Pair.second represents the first
         * unprocessed child of the pair.first (`0` means the left child, `1` means the right child).
         */
        val nodeToChild = mutableListOf<Pair<UHeapRef, Int>>()
        val completelyMapped = mutableListOf<UExpr<Sort>>()

        nodeToChild.add(this to 0)

        while (nodeToChild.isNotEmpty()) {
            val (ref, state) = nodeToChild.removeLast()
            when (ref) {
                is UConcreteHeapRef -> completelyMapped += concreteMapper(ref)
                is USymbolicHeapRef -> completelyMapped += symbolicMapper(ref)
                is UIteExpr<UAddressSort> -> {
                    when (state) {
                        0 -> {
                            nodeToChild += ref to 1
                            nodeToChild += ref.trueBranch to 0
                        }
                        1 -> {
                            nodeToChild += ref to 2
                            nodeToChild += ref.falseBranch to 0
                        }
                        2 -> {
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
 * Filters [ref] non-recursively with [predicate] and returns the result. A guard in the argument of the
 * [predicate] ensures that any path from the root to the filtered leaf is illegal.
 *
 * @return A guarded expression with the guard indicating that any leaf on which [predicate] returns `false`
 * is inaccessible.
 */
internal inline fun filter(
    ref: UHeapRef,
    initialGuard: UBoolExpr,
    crossinline predicate: (GuardedExpr<UHeapRef>) -> Boolean,
): GuardedExpr<UHeapRef>? = with(ref.ctx) {
    when (ref) {
        is USymbolicHeapRef,
        is UConcreteHeapRef,
        -> (ref with initialGuard).takeIf(predicate)
        is UIteExpr<UAddressSort> -> {
            /**
             * This code simulates DFS on a binary tree without an explicit recursion. Pair.second represents the first
             * unprocessed child of the pair.first (`0` means the left child, `1` means the right child).
             */
            val nodeToChild = mutableListOf<Pair<GuardedExpr<UHeapRef>, Int>>()
            val completelyMapped = mutableListOf<GuardedExpr<UHeapRef>?>()

            nodeToChild.add((ref with initialGuard) to 0)

            while (nodeToChild.isNotEmpty()) {
                val (guarded, state) = nodeToChild.removeLast()
                val (cur, guardFromTop) = guarded
                when (cur) {
                    is USymbolicHeapRef,
                    is UConcreteHeapRef,
                    -> completelyMapped += (cur with initialGuard).takeIf { predicate(cur with guardFromTop) }
                    is UIteExpr<UAddressSort> -> when (state) {
                        0 -> {
                            nodeToChild += guarded to 1
                            nodeToChild += (cur.trueBranch with mkAndNoFlat(guardFromTop, cur.condition)) to 0
                        }
                        1 -> {
                            nodeToChild += guarded to 2
                            nodeToChild += (cur.falseBranch with mkAndNoFlat(guardFromTop, !cur.condition)) to 0
                        }
                        2 -> {
                            val rhs = completelyMapped.removeLast()
                            val lhs = completelyMapped.removeLast()
                            val next = when {
                                lhs != null && rhs != null -> {
                                    val guard = (!cur.condition or lhs.guard) and (cur.condition or rhs.guard)
                                    mkIte(cur.condition, lhs.expr, rhs.expr) with guard
                                }
                                lhs != null -> {
                                    val guard = mkAndNoFlat(listOf(cur.condition, lhs.guard))
                                    lhs.expr with guard
                                }
                                rhs != null -> {
                                    val guard = mkAndNoFlat(listOf(!cur.condition, rhs.guard))
                                    rhs.expr with guard
                                }
                                else -> null
                            }
                            completelyMapped += next
                        }
                    }
                }

            }

            completelyMapped.single()
        }

        else -> error("Unexpected ref: $ref")
    }
}