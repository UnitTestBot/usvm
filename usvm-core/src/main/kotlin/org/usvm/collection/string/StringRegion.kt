package org.usvm.collection.string

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.UBoolExpr
import org.usvm.UCharSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.array.UArrayRegion
import org.usvm.collection.array.UArrayRegionId
import org.usvm.isStaticHeapRef
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.UWritableMemory
import org.usvm.memory.foldHeapRef
import org.usvm.memory.guardedWrite
import org.usvm.memory.map
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.solver.URegionDecoder
import org.usvm.uctx
import org.usvm.withSizeSort

data class UStringLValue(val ref: UHeapRef): ULValue<UStringLValue, UStringSort> {
    override val sort: UStringSort = ref.uctx.stringSort

    override val memoryRegionId: UMemoryRegionId<UStringLValue, UStringSort> =
        UStringRegionId(ref.uctx)

    override val key: UStringLValue
        get() = this
}

data class UStringRegionId(val ctx: UContext<*>) :
    UMemoryRegionId<UStringLValue, UStringSort> {
    override val sort: UStringSort = ctx.stringSort

    override fun emptyRegion(): UStringRegion =
        UStringMemoryRegion()
}

typealias UInputStrings = USymbolicCollection<UInputStringId, UHeapRef, UStringSort>

interface UStringRegion : UMemoryRegion<UStringLValue, UStringSort>

internal class UStringMemoryRegion(
    /**
     * Strings with negative concrete heap addresses -- can be aliased by input strings
     */
    private var internedStrings: PersistentMap<UConcreteHeapAddress, UStringExpr> = persistentHashMapOf(),
    /**
     * Strings with positive concrete heap addresses -- cannot be aliased by input strings
     */
    private var allocatedStrings: PersistentMap<UConcreteHeapAddress, UStringExpr> = persistentHashMapOf(),
    private var inputStrings: UInputStrings? = null
): UStringRegion {

    private fun getAllocatedString(ref: UConcreteHeapRef): UStringExpr {
        var string = allocatedStrings[ref.address]
        if (string == null) {
            string = ref.uctx.mkEmptyString()
            allocatedStrings.put(ref.address, string)
        }
        return string
    }

    private fun getInternedString(ref: UConcreteHeapRef): UStringExpr {
        var string = internedStrings[ref.address]
        if (string == null) {
            string = ref.uctx.mkEmptyString()
            internedStrings.put(ref.address, string)
        }
        return string
    }

    private fun getInputString(ref: UHeapRef): UStringExpr {
        if (inputStrings == null)
            inputStrings = UInputStringId(ref.uctx).emptyCollection()
        return inputStrings!!.read(ref)
    }

    private fun updateInternedString(ref: UConcreteHeapRef, updated: UStringExpr, guard: UBoolExpr): UStringMemoryRegion {
        val updatedInternedStrings = internedStrings.guardedWrite(ref.address, updated, guard) {ref.uctx.mkEmptyString()}
        return UStringMemoryRegion(updatedInternedStrings, allocatedStrings, inputStrings)
    }

    private fun updateAllocatedString(ref: UConcreteHeapRef, updated: UStringExpr, guard: UBoolExpr): UStringMemoryRegion {
        val updatedAllocatedStrings = allocatedStrings.guardedWrite(ref.address, updated, guard) {ref.uctx.mkEmptyString()}
        return UStringMemoryRegion(internedStrings, updatedAllocatedStrings, inputStrings)
    }

    private fun updateInputString(ref: UHeapRef, updated: UStringExpr, guard: UBoolExpr): UStringMemoryRegion {
        val input = inputStrings ?: UInputStringId(ref.uctx).emptyCollection()
        return UStringMemoryRegion(internedStrings, allocatedStrings, input.write(ref, updated, guard))
    }

    inline fun <Sort: USort> mapString(ref: UHeapRef, mapper: (UStringExpr) -> UExpr<Sort>): UExpr<Sort> =
        ref.map({mapper(getAllocatedString(it))}, {mapper(getInternedString(it))}, {mapper(getInputString(it))})

    internal fun getString(ref: UHeapRef): UStringExpr =
        ref.map(::getAllocatedString, ::getInternedString, ::getInputString)

    override fun read(key: UStringLValue): UExpr<UStringSort> =
        getString(key.ref)

    override fun write(
        key: UStringLValue,
        value: UExpr<UStringSort>,
        guard: UBoolExpr
    ): UMemoryRegion<UStringLValue, UStringSort> = foldHeapRef(key.ref, initial = this,
        initialGuard = guard,
        ignoreNullRefs = true,
        collapseHeapRefs = true,
        staticIsConcrete = true,
        blockOnConcrete = { acc, guardedRef ->
            if (isStaticHeapRef(guardedRef.expr))
                acc.updateInternedString(guardedRef.expr, value, guardedRef.guard)
            else
                acc.updateAllocatedString(guardedRef.expr, value, guardedRef.guard)
        },
        blockOnSymbolic = { acc, guardedRef -> acc.updateInputString(guardedRef.expr, value, guardedRef.guard)})
}

internal fun <ArrayType, ArrayElementSort : USort, USizeSort : USort> UWritableMemory<*>.copyStringContentToArray(
    srcString: UStringExpr,
    dstRef: UHeapRef,
    arrayType: ArrayType,
    elementSort: ArrayElementSort,
    guard: UBoolExpr,
    converter: ((UExpr<UCharSort>) -> UExpr<ArrayElementSort>)? = null
) {
    val arrayRegionId = UArrayRegionId<_, _, USizeSort>(arrayType, elementSort)
    val arrayRegion = getRegion(arrayRegionId)

    check(arrayRegion is UArrayRegion<ArrayType, ArrayElementSort, USizeSort>) {
        "Can't copy string content to $arrayRegion"
    }

    val srcCollectionId = UStringCollectionId<USizeSort>(ctx.withSizeSort(), srcString)
    val srcCollection = srcCollectionId.emptyCollection()
    val newArrayRegion = arrayRegion.memcpy(srcCollection, dstRef, arrayType, elementSort, guard,
        allocatedDstAdapter = { ref -> UStringToAllocatedArrayAdapter(ref, converter) },
        inputDstAdapter = { ref -> UStringToInputArrayAdapter(ref, converter) })
    setRegion(arrayRegionId, newArrayRegion)
}

/**
 * Works in two states: completing and non-completing. State can be switched by [setCompletion] method.
 * - In completing state, refs without explicit interpretation are mapped to empty string.
 *   For example, if region does not give an explicit interpretation for s, then
 *   UStringRepeatExpr(s, x) will be evaluated to empty string.
 * - In non-completing state, returns symbolic strings. This is done to obtain partial string evaluation in model.
 *   For example, if we have UStringRepeatExpr(s, x) and x = 5 in model, then evaluation in this model
 *   in pending state will give UStringRepeatExpr(s, x), without completing s to empty string.
 */
class UStringModelRegion(val regionId: UStringRegionId) : UReadOnlyMemoryRegion<UStringLValue, UStringSort> {
    private var strings: MutableMap<UConcreteHeapAddress, UStringLiteralExpr> = mutableMapOf()
    private var isCompleting = true

    internal fun setCompletion(complete: Boolean) {
        isCompleting = complete
    }

    internal fun add(mapping: Map<UConcreteHeapAddress, UStringLiteralExpr>) {
        strings.putAll(mapping)
    }

    override fun read(key: UStringLValue): UExpr<UStringSort> {
        val strings = this.strings
        val ref = modelEnsureConcreteInputRef(key.ref)
        return strings[ref.address] ?:
            if (isCompleting) regionId.ctx.mkEmptyString()
            else regionId.ctx.mkStringFromLanguage(key.ref)
    }

    val isEmpty: Boolean
        get() = strings.isEmpty()
}

class UStringModelRegionDecoder(val regionId: UStringRegionId): URegionDecoder<UStringLValue, UStringSort> {
    override fun decodeLazyRegion(model: UModelEvaluator<*>, assertions: List<KExpr<KBoolSort>>) =
        UStringModelRegion(regionId)
}
