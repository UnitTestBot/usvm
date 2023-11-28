package org.usvm.memory

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.INITIAL_CONCRETE_ADDRESS
import org.usvm.INITIAL_STATIC_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UMockEvaluator
import org.usvm.UMocker
import org.usvm.USort
import org.usvm.constraints.UTypeConstraints
import org.usvm.constraints.UTypeEvaluator
import org.usvm.merging.MergeGuard
import org.usvm.merging.UMergeable

interface UMemoryRegionId<Key, Sort : USort> {
    val sort: Sort

    fun emptyRegion(): UMemoryRegion<Key, Sort>
}

interface UReadOnlyMemoryRegion<Key, Sort : USort> {
    fun read(key: Key): UExpr<Sort>
}

interface UMemoryRegion<Key, Sort : USort> : UReadOnlyMemoryRegion<Key, Sort> {
    fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UMemoryRegion<Key, Sort>
}

interface ULValue<Key, Sort : USort> {
    val sort: Sort
    val memoryRegionId: UMemoryRegionId<Key, Sort>
    val key: Key
}

/**
 * Current heap address holder. Calling [freshAllocatedAddress] updates [lastAllocatedAddress] or [lastStaticAddress] globally,
 * depending on was an allocated object created or static.
 * That is, allocation of an object in one state updates counter in all states.
 * This would help to avoid overlapping addresses in merged states.
 * Copying is prohibited.
 */
class UAddressCounter {
    private var lastAllocatedAddress: Int = INITIAL_CONCRETE_ADDRESS
    private var lastStaticAddress: Int = INITIAL_STATIC_ADDRESS

    /**
     * Returns the [lastAllocatedAddress] and increments it.
     */
    fun freshAllocatedAddress(): UConcreteHeapAddress = lastAllocatedAddress++

    /**
     * Returns the [lastStaticAddress] and decrements it.
     */
    fun freshStaticAddress(): UConcreteHeapAddress = lastStaticAddress--
}

interface UReadOnlyMemory<Type> {
    val stack: UReadOnlyRegistersStack
    val mocker: UMockEvaluator
    val types: UTypeEvaluator<Type>

    private fun <Key, Sort : USort> read(regionId: UMemoryRegionId<Key, Sort>, key: Key): UExpr<Sort> {
        val region = getRegion(regionId)
        return region.read(key)
    }

    fun <Key, Sort : USort> read(lvalue: ULValue<Key, Sort>) = read(lvalue.memoryRegionId, lvalue.key)

    fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort>

    fun nullRef(): UHeapRef

    fun toWritableMemory(): UWritableMemory<Type>
}

interface UWritableMemory<Type> : UReadOnlyMemory<Type> {
    fun <Key, Sort : USort> setRegion(regionId: UMemoryRegionId<Key, Sort>, newRegion: UMemoryRegion<Key, Sort>)

    fun <Key, Sort : USort> write(lvalue: ULValue<Key, Sort>, rvalue: UExpr<Sort>, guard: UBoolExpr)

    fun allocConcrete(type: Type): UConcreteHeapRef
    fun allocStatic(type: Type): UConcreteHeapRef
}

@Suppress("MemberVisibilityCanBePrivate")
class UMemory<Type, Method>(
    internal val ctx: UContext<*>,
    override val types: UTypeConstraints<Type>,
    override val stack: URegistersStack = URegistersStack(),
    private val mocks: UMocker<Method> = ctx.mocker(),
    persistentRegions: PersistentMap<UMemoryRegionId<*, *>, UMemoryRegion<*, *>> = persistentHashMapOf(),
) : UWritableMemory<Type>, UMergeable<UMemory<Type, Method>, MergeGuard> {
    private val regions = persistentRegions.builder()

    override val mocker: UMocker<Method>
        get() = mocks

    @Suppress("UNCHECKED_CAST")
    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UMemoryRegion<Key, Sort> {
        if (regionId is URegisterStackId) return stack as UMemoryRegion<Key, Sort>

        return regions.getOrPut(regionId) {
            regionId.emptyRegion()
        } as UMemoryRegion<Key, Sort>
    }

    override fun <Key, Sort : USort> setRegion(
        regionId: UMemoryRegionId<Key, Sort>,
        newRegion: UMemoryRegion<Key, Sort>
    ) {
        if (regionId is URegisterStackId) {
            check(newRegion === stack) { "Stack is mutable" }
            return
        }
        regions[regionId] = newRegion
    }

    override fun <Key, Sort : USort> write(lvalue: ULValue<Key, Sort>, rvalue: UExpr<Sort>, guard: UBoolExpr) =
        write(lvalue.memoryRegionId, lvalue.key, rvalue, guard)

    private fun <Key, Sort : USort> write(
        regionId: UMemoryRegionId<Key, Sort>,
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        val region = getRegion(regionId)
        val newRegion = region.write(key, value, guard)
        setRegion(regionId, newRegion)
    }

    override fun allocConcrete(type: Type): UConcreteHeapRef {
        val allocatedHeapRef = ctx.mkConcreteHeapRef(ctx.addressCounter.freshAllocatedAddress())
        types.allocate(allocatedHeapRef.address, type)

        return allocatedHeapRef
    }

    override fun allocStatic(type: Type): UConcreteHeapRef {
        val staticHeapRef = ctx.mkConcreteHeapRef(ctx.addressCounter.freshStaticAddress())
        types.allocate(staticHeapRef.address, type)

        return staticHeapRef
    }

    override fun nullRef(): UHeapRef = ctx.nullRef

    fun clone(typeConstraints: UTypeConstraints<Type>): UMemory<Type, Method> =
        UMemory(ctx, typeConstraints, stack.clone(), mocks.clone(), regions.build())

    override fun toWritableMemory() =
    // To be perfectly rigorous, we should clone stack and types here.
        // But in fact they should not be used, so to optimize things up, we don't touch them.
        UMemory(ctx, types, stack, mocks, regions.build())


    /**
     * Check if this [UMemory] can be merged with [other] memory.
     *
     * TODO: now only the following case is supported:
     *  - regions are reference equal
     *  - mocks are reference equal
     *  - types are not checked and taken from this [UMemory]
     *  - stacks are compared and merged deeply
     *
     * @return the merged memory.
     */
    override fun mergeWith(other: UMemory<Type, Method>, by: MergeGuard): UMemory<Type, Method>? {
        val ids = regions.keys
        val otherIds = other.regions.keys
        if (ids != otherIds) {
            return null
        }

        // TODO: types ?

        for (id in ids) {
            val leftRegion = getRegion(id)
            val rightRegion = other.getRegion(id)
            if (leftRegion !== rightRegion) {
                return null
            }
        }

        val mergedRegions = regions.build()
        val mergedStack = stack.mergeWith(other.stack, by) ?: return null
        val mergedMocks = mocks.mergeWith(other.mocks, by) ?: return null

        return UMemory(ctx, types, mergedStack, mergedMocks, mergedRegions)
    }
}
