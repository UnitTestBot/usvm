package org.usvm

import org.ksmt.utils.asExpr
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree
import java.util.*

//region Memory region


/**
 * A typealias for a lambda that takes a key, a region and returns a reading from the region by the key.
 */
typealias UInstantiator<RegionId, Key, Sort> = (key: Key, UMemoryRegion<RegionId, Key, Sort>) -> UExpr<Sort>

/**
 * A uniform unbounded slice of memory. Indexed by [Key], stores symbolic values.
 *
 * @property regionId describes the source of the region. Memory regions with the same [regionId] represent the same
 * memory area, but in different states.
 *
 * @property defaultValue describes the initial values for the region. If [defaultValue] equals `null` then this region
 * is filled with symbolics.
 */
data class UMemoryRegion<RegionId : URegionId<Key>, Key, Sort : USort>(
    val regionId: RegionId,
    val sort: Sort,
    val updates: UMemoryUpdates<Key, Sort>,
    private val defaultValue: UExpr<Sort>?, // If defaultValue = null then this region is filled with symbolics
    private val instantiator: UInstantiator<RegionId, Key, Sort>,
) {
    private fun read(key: Key, updates: UMemoryUpdates<Key, Sort>): UExpr<Sort> {
        val lastUpdatedElement = updates.lastUpdatedElementOrNull()

        if (lastUpdatedElement == null && defaultValue != null) {
            // Reading from an untouched array filled with defaultValue
            return defaultValue
        }

        if (lastUpdatedElement != null) {
            if (lastUpdatedElement.includesConcretely(key, precondition = sort.ctx.trueExpr)) {
                // The last write has overwritten the key
                return lastUpdatedElement.value(key)
            }
        }

        val localizedRegion = if (updates === this.updates) {
            this
        } else {
            this.copy(updates = updates)
        }

        return instantiator(key, localizedRegion)
    }

    fun read(key: Key): UExpr<Sort> {
        if (sort == sort.uctx.addressSort) {
            // Here we split concrete heap addresses from symbolic ones to optimize further memory operations.
            return splittingRead(key) { it is UConcreteHeapRef }
        }

        val updates = updates.read(key)
        return read(key, updates)
    }

    /**
     * Reads key from this memory region, but 'bubbles up' entries satisfying predicates.
     * For example, imagine we read for example key z from array A with two updates: v written into x and w into y.
     * Usual [read] produces the expression
     *      A{x <- v}{y <- w}[z]
     * If v satisfies [predicate] and w does not, then [splittingRead] instead produces the expression
     *      ite(y != z /\ x = z, v, A{y <- w}[z]).
     * These two expressions are semantically equivalent, but the second one 'splits' v out of the rest
     * memory updates.
     */
    private fun splittingRead(key: Key, predicate: (UExpr<Sort>) -> Boolean): UExpr<Sort> {
        val ctx = sort.ctx
        val guardBuilder = GuardBuilder(ctx.trueExpr)
        val matchingWrites = ArrayList<GuardedExpr<UExpr<Sort>>>() // works faster than linked list
        val splittingUpdates = split(key, predicate, matchingWrites, guardBuilder).updates

        val reading = read(key, splittingUpdates)

        // TODO: maybe introduce special expression for such operations?
        val readingWithBubbledWrites = matchingWrites.foldRight(reading) { (expr, guard), acc ->
            //                         foldRight here ^^^^^^^^^ is important
            ctx.mkIte(guard, expr, acc)
        }


        return readingWithBubbledWrites
    }

    fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UMemoryRegion<RegionId, Key, Sort> {
        assert(value.sort == sort)

        val newUpdates = if (sort == sort.uctx.addressSort) {
            // we must split symbolic and concrete heap refs here,
            // because later in [splittingRead] we check value is UConcreteHeapRef
            var newUpdates = updates

            withHeapRef(
                value.asExpr(sort.uctx.addressSort),
                initialGuard = guard,
                blockOnConcrete = { (ref, guard) -> newUpdates = newUpdates.write(key, ref.asExpr(sort), guard) },
                blockOnSymbolic = { (ref, guard) -> newUpdates = newUpdates.write(key, ref.asExpr(sort), guard) }
            )

            newUpdates
        } else {
            updates.write(key, value, guard)
        }


        return this.copy(updates = newUpdates)
    }

    /**
     * Splits this [UMemoryRegion] on two parts:
     * * Values of [UUpdateNode]s satisfying [predicate] are added to the [matchingWrites].
     * * [UUpdateNode]s unsatisfying [predicate] remain in the result memory region.
     *
     * The [guardBuilder] is used to build guards for values added to [matchingWrites]. In the end, the [guardBuilder]
     * is updated and contains predicate indicating that the [key] can't be included in any of visited [UUpdateNode]s.
     *
     * @return new [UMemoryRegion] without writes satisfying [predicate] or this [UMemoryRegion] if no matching writes
     * were found.
     * @see [UMemoryUpdates.split], [splittingRead]
     */
    internal fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
    ): UMemoryRegion<RegionId, Key, Sort> {
        // TODO: either check in UMemoryRegion constructor that we do not construct memory region with
        //       non-null reference as default value, or implement splitting by default value.
        assert(defaultValue == null || !predicate(defaultValue))

        val count = matchingWrites.size
        val splitUpdates = updates.read(key).split(key, predicate, matchingWrites, guardBuilder)
        val sizeRemainedUnchanged = matchingWrites.size == count

        return if (sizeRemainedUnchanged) {
            this
        } else {
            this.copy(updates = splitUpdates)
        }
    }

    /**
     * Maps the region using [composer].
     * It is used in [UComposer] for composition operation.
     *
     * Note: after this operation a region returned as a result might be in `broken` state:
     * it might have both symbolic and concrete values as keys in it.
     */
    fun <Field, Type> map(
        composer: UComposer<Field, Type>,
        instantiator: UInstantiator<RegionId, Key, Sort> = this.instantiator,
    ): UMemoryRegion<RegionId, Key, Sort> {
        // Map the updates and the default value
        val mappedUpdates = updates.map(regionId.keyMapper(composer), composer)
        val mappedDefaultValue = defaultValue?.let { composer.compose(it) }

        // Note that we cannot use optimization with unchanged mappedUpdates and mappedDefaultValues here
        // since in a new region we might have an updated instantiator.
        // Therefore, we have to check their reference equality as well.
        if (mappedUpdates === updates && mappedDefaultValue === defaultValue && instantiator === this.instantiator) {
            return this
        }

        return UMemoryRegion(regionId, sort, mappedUpdates, mappedDefaultValue, instantiator)
    }

    @Suppress("UNCHECKED_CAST")
    fun <Field, Type> applyTo(heap: USymbolicHeap<Field, Type>) {
        // Apply each update on the copy
        updates.forEach {
            when (it) {
                is UPinpointUpdateNode<Key, Sort> -> regionId.write(it.key, sort, heap, it.value, it.guard)
                is URangedUpdateNode<*, *, *, Key, Sort> -> {
                    it.region.applyTo(heap)

                    val (srcFromRef, srcFromIdx) = it.keyConverter.srcSymbolicArrayIndex
                    val (dstFromRef, dstFromIdx) = it.keyConverter.dstFromSymbolicArrayIndex
                    val dstToIdx = it.keyConverter.dstToIndex
                    val arrayType = it.region.regionId.arrayType as Type

                    heap.memcpy(srcFromRef, dstFromRef, arrayType, sort, srcFromIdx, dstFromIdx, dstToIdx, it.guard)
                }
            }
        }
    }

    /**
     * @return Memory region which obtained from this one by overwriting the range of addresses [[fromKey] : [toKey]]
     * with values from memory region [fromRegion] read from range
     * of addresses [[keyConverter].convert([fromKey]) : [keyConverter].convert([toKey])]
     */
    fun <ArrayType, OtherRegionId : UArrayId<ArrayType, SrcKey>, SrcKey> copyRange(
        fromRegion: UMemoryRegion<OtherRegionId, SrcKey, Sort>,
        fromKey: Key, toKey: Key,
        keyConverter: UMemoryKeyConverter<SrcKey, Key>,
        guard: UBoolExpr,
    ): UMemoryRegion<RegionId, Key, Sort> {
        val updatesCopy = updates.copyRange(fromRegion, fromKey, toKey, keyConverter, guard)
        return this.copy(updates = updatesCopy)
    }

    override fun toString(): String =
        buildString {
            append('<')
            append(defaultValue)
            updates.forEach {
                append(it.toString())
            }
            append('>')
            append('@')
            append(regionId)
        }
}

class GuardBuilder(nonMatchingUpdates: UBoolExpr) {
    var nonMatchingUpdatesGuard: UBoolExpr = nonMatchingUpdates
        private set

    operator fun plusAssign(guard: UBoolExpr) {
        nonMatchingUpdatesGuard = guarded(guard)
    }

    /**
     * @return [expr] guarded by this guard builder. Implementation uses [UContext.mkAndNoFlat], because we accumulate
     * [nonMatchingUpdatesGuard] and otherwise it would take quadratic time.
     */
    fun guarded(expr: UBoolExpr): UBoolExpr = expr.ctx.mkAndNoFlat(nonMatchingUpdatesGuard, expr)
}

//endregion

//region Instantiations

typealias USymbolicArrayIndex = Pair<UHeapRef, USizeExpr>

fun heapRefEq(ref1: UHeapRef, ref2: UHeapRef): UBoolExpr =
    ref1.uctx.mkHeapRefEq(ref1, ref2)  // TODO: use simplified equality!

@Suppress("UNUSED_PARAMETER")
fun heapRefCmpSymbolic(ref1: UHeapRef, ref2: UHeapRef): UBoolExpr =
    error("Heap references should not be compared!")

@Suppress("UNUSED_PARAMETER")
fun heapRefCmpConcrete(ref1: UHeapRef, ref2: UHeapRef): Boolean =
    error("Heap references should not be compared!")

fun indexEq(idx1: USizeExpr, idx2: USizeExpr): UBoolExpr =
    idx1.ctx.mkEq(idx1, idx2)  // TODO: use simplified equality!

fun indexLeSymbolic(idx1: USizeExpr, idx2: USizeExpr): UBoolExpr =
    idx1.ctx.mkBvSignedLessOrEqualExpr(idx1, idx2)  // TODO: use simplified comparison!

fun indexLeConcrete(idx1: USizeExpr, idx2: USizeExpr): Boolean =
    // TODO: to optimize things up, we could pass path constraints here and lookup the numeric bounds for idx1 and idx2
    idx1 == idx2 || (idx1 is UConcreteSize && idx2 is UConcreteSize && idx1.numberValue <= idx2.numberValue)

fun refIndexEq(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): UBoolExpr = with(idx1.first.ctx) {
    // TODO: use simplified operations!
    return@with (idx1.first eq idx2.first) and indexEq(idx1.second, idx2.second)
}

fun refIndexCmpSymbolic(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): UBoolExpr = with(idx1.first.ctx) {
    return@with (idx1.first eq idx2.first) and indexLeSymbolic(idx1.second, idx2.second)
}

fun refIndexCmpConcrete(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): Boolean =
    idx1.first == idx2.first && indexLeConcrete(idx1.second, idx2.second)

// TODO: change it to intervals region
typealias UArrayIndexRegion = SetRegion<UIndexType>

fun indexRegion(idx: USizeExpr): UArrayIndexRegion =
    when (idx) {
        is UConcreteSize -> SetRegion.singleton(idx.numberValue)
        else -> SetRegion.universe()
    }

fun indexRangeRegion(idx1: USizeExpr, idx2: USizeExpr): UArrayIndexRegion =
    when (idx1) {
        is UConcreteSize ->
            when (idx2) {
                is UConcreteSize -> SetRegion.ofSequence((idx1.numberValue..idx2.numberValue).asSequence())
                else -> SetRegion.universe()
            }

        else -> SetRegion.universe()
    }

fun refIndexRegion(idx: USymbolicArrayIndex): UArrayIndexRegion = indexRegion(idx.second)
fun refIndexRangeRegion(
    idx1: USymbolicArrayIndex,
    idx2: USymbolicArrayIndex,
): UArrayIndexRegion = indexRangeRegion(idx1.second, idx2.second)

typealias UInputFieldRegion<Field, Sort> = UMemoryRegion<UInputFieldRegionId<Field>, UHeapRef, Sort>
typealias UAllocatedArrayRegion<ArrayType, Sort> = UMemoryRegion<UAllocatedArrayId<ArrayType>, USizeExpr, Sort>
typealias UInputArrayRegion<ArrayType, Sort> = UMemoryRegion<UInputArrayId<ArrayType>, USymbolicArrayIndex, Sort>
typealias UInputArrayLengthRegion<ArrayType> = UMemoryRegion<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>

typealias KeyMapper<Key> = (Key) -> Key

val <Field, Sort : USort> UInputFieldRegion<Field, Sort>.field
    get() = regionId.field

val <ArrayType, Sort : USort> UAllocatedArrayRegion<ArrayType, Sort>.allocatedArrayType
    get() = regionId.arrayType
val <ArrayType, Sort : USort> UAllocatedArrayRegion<ArrayType, Sort>.allocatedAddress
    get() = regionId.address

val <ArrayType, Sort : USort> UInputArrayRegion<ArrayType, Sort>.inputArrayType
    get() = regionId.arrayType

val <ArrayType> UInputArrayLengthRegion<ArrayType>.inputLengthArrayType
    get() = regionId.arrayType

fun <Field, Sort : USort> emptyInputFieldRegion(
    field: Field,
    sort: Sort,
    instantiator: UInstantiator<UInputFieldRegionId<Field>, UHeapRef, Sort>,
): UInputFieldRegion<Field, Sort> = UMemoryRegion(
    UInputFieldRegionId(field),
    sort,
    UEmptyUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic),
    defaultValue = null,
    instantiator
)

fun <ArrayType, Sort : USort> emptyAllocatedArrayRegion(
    arrayType: ArrayType,
    address: UConcreteHeapAddress,
    sort: Sort,
    instantiator: UInstantiator<UAllocatedArrayId<ArrayType>, USizeExpr, Sort>,
): UAllocatedArrayRegion<ArrayType, Sort> {
    val updates = UTreeUpdates<USizeExpr, UArrayIndexRegion, Sort>(
        updates = emptyRegionTree(),
        ::indexRegion, ::indexRangeRegion, ::indexEq, ::indexLeConcrete, ::indexLeSymbolic
    )
    val regionId = UAllocatedArrayId(arrayType, address)
    return UMemoryRegion(regionId, sort, updates, sort.defaultValue(), instantiator)
}

fun <ArrayType, Sort : USort> emptyInputArrayRegion(
    arrayType: ArrayType,
    sort: Sort,
    instantiator: UInstantiator<UInputArrayId<ArrayType>, USymbolicArrayIndex, Sort>,
): UInputArrayRegion<ArrayType, Sort> {
    val updates = UTreeUpdates<USymbolicArrayIndex, UArrayIndexRegion, Sort>(
        updates = emptyRegionTree(),
        ::refIndexRegion, ::refIndexRangeRegion, ::refIndexEq, ::refIndexCmpConcrete, ::refIndexCmpSymbolic
    )
    return UMemoryRegion(UInputArrayId(arrayType), sort, updates, defaultValue = null, instantiator)
}

fun <ArrayType> emptyArrayLengthRegion(
    arrayType: ArrayType,
    ctx: UContext,
    instantiator: UInstantiator<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>,
): UInputArrayLengthRegion<ArrayType> =
    UMemoryRegion(
        UInputArrayLengthId(arrayType),
        ctx.sizeSort,
        UEmptyUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic),
        defaultValue = null,
        instantiator
    )

//endregion
