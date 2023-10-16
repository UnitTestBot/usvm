package org.usvm.memory

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.INITIAL_CONCRETE_ADDRESS
import org.usvm.INITIAL_STATIC_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIndexedMocker
import org.usvm.UMockEvaluator
import org.usvm.UMockSymbol
import org.usvm.UMocker
import org.usvm.USort
import org.usvm.constraints.UTypeConstraints
import org.usvm.constraints.UTypeEvaluator

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
object UAddressCounter {
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
    private var mocks: UMocker<Method> = UIndexedMocker(ctx),
    private var regions: PersistentMap<UMemoryRegionId<*, *>, UMemoryRegion<*, *>> = persistentMapOf(),
    internal val addressCounter: UAddressCounter = UAddressCounter,
) : UWritableMemory<Type> {

    override val mocker: UMockEvaluator
        get() = mocks

    @Suppress("UNCHECKED_CAST")
    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UMemoryRegion<Key, Sort> {
        if (regionId is URegisterStackId) return stack as UMemoryRegion<Key, Sort>

        val region = regions[regionId]
        if (region != null) return region as UMemoryRegion<Key, Sort>

        val newRegion = regionId.emptyRegion()
        regions = regions.put(regionId, newRegion)
        return newRegion
    }

    override fun <Key, Sort : USort> setRegion(
        regionId: UMemoryRegionId<Key, Sort>,
        newRegion: UMemoryRegion<Key, Sort>
    ) {
        if (regionId is URegisterStackId) {
            check(newRegion === stack) { "Stack is mutable" }
            return
        }
        regions = regions.put(regionId, newRegion)
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
        val allocatedHeapRef = ctx.mkConcreteHeapRef(addressCounter.freshAllocatedAddress())
        types.allocate(allocatedHeapRef.address, type)

        return allocatedHeapRef
    }

    override fun allocStatic(type: Type): UConcreteHeapRef {
        val staticHeapRef = ctx.mkConcreteHeapRef(addressCounter.freshStaticAddress())
        types.allocate(staticHeapRef.address, type)

        return staticHeapRef
    }

    override fun nullRef(): UHeapRef = ctx.nullRef

    fun <Sort : USort> mock(body: UMocker<Method>.() -> Pair<UMockSymbol<Sort>, UMocker<Method>>): UMockSymbol<Sort> {
        val (result, updatedMocker) = mocks.body()
        mocks = updatedMocker
        return result
    }

    fun clone(typeConstraints: UTypeConstraints<Type>): UMemory<Type, Method> =
        UMemory(ctx, typeConstraints, stack.clone(), mocks, regions, addressCounter)

    override fun toWritableMemory() =
    // To be perfectly rigorous, we should clone stack and types here.
        // But in fact they should not be used, so to optimize things up, we don't touch them.
        UMemory(ctx, types, stack, mocks, regions, addressCounter)
}
