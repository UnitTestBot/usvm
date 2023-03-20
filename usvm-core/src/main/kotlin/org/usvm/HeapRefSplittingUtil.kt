package org.usvm

data class GuardedExpr<out T>(
    val expr: T,
    val guard: UBoolExpr,
)

infix fun <T> T.with(guard: UBoolExpr) = GuardedExpr(this, guard)

internal data class SplitHeapRefs(
    val concreteHeapRefs: List<GuardedExpr<UConcreteHeapRef>>,
    val symbolicHeapRefs: List<GuardedExpr<USymbolicHeapRef>>,
)

/**
 * Traverses the [ref] non-recursively and collects [UConcreteHeapRef]s and [USymbolicHeapRef]s as well as
 * guards for them.
 *
 * @param initialGuard an initial value for the accumulated guard.
 */
internal fun splitUHeapRef(ref: UHeapRef, initialGuard: UBoolExpr = ref.ctx.trueExpr): SplitHeapRefs {
    val ctx = ref.ctx
    val workList = mutableListOf(ref with initialGuard)
    val concreteHeapRefs = mutableListOf<GuardedExpr<UConcreteHeapRef>>()
    val symbolicHeapRefs = mutableListOf<GuardedExpr<USymbolicHeapRef>>()

    while (workList.isNotEmpty()) {
        val (currentRef, guard) = workList.removeLast()
        if (guard.isFalse) {
            continue
        }
        when (currentRef) {
            is UConcreteHeapRef -> concreteHeapRefs += currentRef with guard
            is USymbolicHeapRef -> symbolicHeapRefs += currentRef with guard
            is UIteExpr<UAddressSort> -> {
                val trueGuard = ctx.mkAndNoFlat(guard, currentRef.condition)
                val falseGuard = ctx.mkAndNoFlat(guard, ctx.mkNot(currentRef.condition))

                workList += currentRef.trueBranch with trueGuard
                workList += currentRef.falseBranch with falseGuard
            }

            else -> error("Unexpected ref: $currentRef")
        }
    }

    return SplitHeapRefs(
        concreteHeapRefs,
        symbolicHeapRefs,
    )
}

/**
 * Traverses the [ref], accumulating guards and applying the [blockOnConcrete] on [UConcreteHeapRef]s and
 * [blockOnSymbolic] on [USymbolicHeapRef]s.
 *
 * @param initialGuard the initial value fot the guard to be passed to [blockOnConcrete] and [blockOnSymbolic].
 */
internal inline fun doWithHeapRef(
    ref: UHeapRef,
    initialGuard: UBoolExpr,
    blockOnConcrete: (GuardedExpr<UConcreteHeapRef>) -> Unit,
    blockOnSymbolic: (GuardedExpr<USymbolicHeapRef>) -> Unit,
) {
    if (initialGuard.isFalse) {
        return
    }

    when (ref) {
        is UConcreteHeapRef -> blockOnConcrete(ref with initialGuard)
        is USymbolicHeapRef -> blockOnSymbolic(ref with initialGuard)
        is UIteExpr<UAddressSort> -> {
            val (concreteHeapRefs, symbolicHeapRefs) = splitUHeapRef(ref, initialGuard)
            concreteHeapRefs.onEach { (ref, guard) -> blockOnConcrete(ref with guard) }
            symbolicHeapRefs.onEach { (ref, guard) -> blockOnSymbolic(ref with guard) }
        }

        else -> error("Unexpected ref: $ref")
    }
}


/**
 * Reassembles [this] with applying [blockOnConcrete] on [UConcreteHeapRef] and
 * [blockOnSymbolic] on [USymbolicHeapRef]. Respects [UIteExpr], so the structure of the result expression will be
 * exactly the same as [this] is, but implicit simplifications may occur.
 *
 * TODO: get rid of recursion here
 */
internal fun <Sort : USort> UHeapRef.mapHeapRef(
    blockOnConcrete: (UConcreteHeapRef) -> UExpr<Sort>,
    blockOnSymbolic: (USymbolicHeapRef) -> UExpr<Sort>,
): UExpr<Sort> = when (this) {
    is UConcreteHeapRef -> blockOnConcrete(this)
    is USymbolicHeapRef -> blockOnSymbolic(this)
    is UIteExpr<UAddressSort> -> {
        val trueRes = trueBranch.mapHeapRef(blockOnConcrete, blockOnSymbolic)
        val falseRes = falseBranch.mapHeapRef(blockOnConcrete, blockOnSymbolic)

        ctx.mkIte(condition, trueRes, falseRes)
    }

    else -> error("Unexpected ref: $this")
}
