package org.usvm.collection.set

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UTransformer
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.DecomposedKey
import org.usvm.memory.KeyTransformer
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryUpdatesVisitor
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.USymbolicCollectionIdWithContextMemory
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.USymbolicCollectionUpdates
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.util.Region
import java.util.IdentityHashMap


abstract class USymbolicSetId<Element, Reg : Region<Reg>, out SetId : USymbolicSetId<Element, Reg, SetId>>(
    contextMemory: UWritableMemory<*>?
) : USymbolicCollectionIdWithContextMemory<Element, UBoolSort, SetId>(contextMemory) {

    fun defaultRegion(): Reg {
        if (contextMemory == null) {
            return baseRegion()
        }
        // TODO: get corresponding collection from contextMemory, recursively eval its region
        TODO()
    }

    abstract fun baseRegion(): Reg

    private val regionCache = IdentityHashMap<Any?, Region<*>>()

    /**
     * Returns over-approximation of keys collection set.
     */
    @Suppress("UNCHECKED_CAST")
    fun <ResultReg : Region<ResultReg>> region(updates: USymbolicCollectionUpdates<Element, UBoolSort>): ResultReg {
        val regionBuilder = USetRegionBuilder<Element, Reg>(this)
        val result = updates.accept(regionBuilder, regionCache as MutableMap<Any?, Reg>)
        return result as ResultReg
    }
}

class UAllocatedSetId<Element, Reg : Region<Reg>>(
    val elementInfo: USymbolicCollectionKeyInfo<Element, Reg>,
    contextMemory: UWritableMemory<*>?
) : USymbolicSetId<Element, Reg, UAllocatedSetId<Element, Reg>>(contextMemory) {

    override val sort: UBoolSort
        get() = TODO("Not yet implemented")

    override fun baseRegion(): Reg =
        elementInfo.bottomRegion()

    override fun UContext.mkReading(
        collection: USymbolicCollection<UAllocatedSetId<Element, Reg>, Element, UBoolSort>,
        key: Element
    ): UExpr<UBoolSort> {
        TODO("Not yet implemented")
    }

    override fun UContext.mkLValue(
        key: Element
    ): ULValue<*, UBoolSort> {
        TODO("Not yet implemented")
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: Element, value: UExpr<UBoolSort>, guard: UBoolExpr) {
        TODO("Not yet implemented")
    }

    override fun <Type> keyMapper(transformer: UTransformer<Type>): KeyTransformer<Element> {
        TODO("Not yet implemented")
    }

    override fun <Type> map(composer: UComposer<Type>): UAllocatedSetId<Element, Reg> {
        TODO("Not yet implemented")
    }

    override fun keyInfo(): USymbolicCollectionKeyInfo<Element, *> {
        TODO("Not yet implemented")
    }

    override fun emptyRegion(): USymbolicCollection<UAllocatedSetId<Element, Reg>, Element, UBoolSort> {
        TODO("Not yet implemented")
    }

    override fun rebindKey(key: Element): DecomposedKey<*, UBoolSort>? {
        TODO("Not yet implemented")
    }
}

class UInputSetId<Element, Reg : Region<Reg>>(
    val elementInfo: USymbolicCollectionKeyInfo<Element, Reg>,
    contextMemory: UWritableMemory<*>?
) : USymbolicSetId<Element, Reg, UInputSetId<Element, Reg>>(contextMemory) {

    override val sort: UBoolSort
        get() = TODO("Not yet implemented")

    override fun baseRegion(): Reg =
        elementInfo.topRegion()

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputSetId<Element, Reg>, Element, UBoolSort>,
        key: Element
    ): UExpr<UBoolSort> {
        TODO("Not yet implemented")
    }

    override fun UContext.mkLValue(
        key: Element
    ): ULValue<*, UBoolSort> {
        TODO("Not yet implemented")
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: Element, value: UExpr<UBoolSort>, guard: UBoolExpr) {
        TODO("Not yet implemented")
    }

    override fun <Type> keyMapper(transformer: UTransformer<Type>): KeyTransformer<Element> {
        TODO("Not yet implemented")
    }

    override fun <Type> map(composer: UComposer<Type>): UInputSetId<Element, Reg> {
        TODO("Not yet implemented")
    }

    override fun keyInfo(): USymbolicCollectionKeyInfo<Element, *> {
        TODO("Not yet implemented")
    }

    override fun rebindKey(key: Element): DecomposedKey<*, UBoolSort>? {
        TODO("Not yet implemented")
    }

    override fun emptyRegion(): USymbolicCollection<UInputSetId<Element, Reg>, Element, UBoolSort> {
        TODO("Not yet implemented")
    }
}

private class USetRegionBuilder<Key, Reg : Region<Reg>>(
    private val collectionId: USymbolicSetId<Key, Reg, *>
) : UMemoryUpdatesVisitor<Key, UBoolSort, Reg> {

    private val keyInfo = collectionId.keyInfo()

    override fun visitSelect(result: Reg, key: Key): UBoolExpr {
        error("Unexpected reading")
    }

    override fun visitInitialValue(): Reg =
        collectionId.defaultRegion()

    override fun visitUpdate(previous: Reg, update: UUpdateNode<Key, UBoolSort>): Reg = when (update) {
        is UPinpointUpdateNode -> {
            // TODO: removed keys
            val keyReg = keyInfo.keyToRegion(update.key)
            previous.union(keyReg.uncheckedCast())
        }

        is URangedUpdateNode<*, *, *, UBoolSort> -> {
            val updatedKeys: Reg = update.adapter.region()
            previous.union(updatedKeys)
        }
    }
}
