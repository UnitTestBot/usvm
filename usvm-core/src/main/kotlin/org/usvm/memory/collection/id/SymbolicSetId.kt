package org.usvm.memory.collection.id

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UTransformer
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.UMemoryUpdatesVisitor
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.key.USymbolicCollectionKeyInfo
import org.usvm.memory.collection.USymbolicCollectionUpdates
import org.usvm.util.Region
import org.usvm.util.SetRegion
import java.util.IdentityHashMap


abstract class USymbolicSetId<Element, out SetId : USymbolicSetId<Element, SetId>>(
    contextMemory: UWritableMemory<*>?
) : USymbolicCollectionIdWithContextMemory<Element, UBoolSort, SetId>(contextMemory) {

    fun <Reg : Region<Reg>> defaultRegion(): Reg {
        if (contextMemory == null) {
            return SetRegion.empty<Int>() as Reg
        }
        // TODO: get corresponding collection from contextMemory, recursively eval its region
        TODO()
    }

    private val regionCache = IdentityHashMap<Any?, Region<*>>()

    /**
     * Returns over-approximation of keys collection set.
     */
    fun <Reg : Region<Reg>> region(updates: USymbolicCollectionUpdates<Element, UBoolSort>): Reg {
        val regionBuilder = SymbolicSetRegionBuilder<Element, Reg>(this)
        @Suppress("UNCHECKED_CAST")
        return updates.accept(regionBuilder, regionCache as MutableMap<Any?, Reg>)
    }
}

class UAllocatedSymbolicSetId<Element, Reg: Region<Reg>>(
    val elementInfo: USymbolicCollectionKeyInfo<Element, Reg>,
    contextMemory: UWritableMemory<*>?
) : USymbolicSetId<Element, UAllocatedSymbolicSetId<Element, Reg>>(contextMemory) {

    override val sort: UBoolSort
        get() = TODO("Not yet implemented")

    override val defaultValue: UBoolExpr?
        get() = TODO("Not yet implemented")

    override fun UContext.mkReading(
        collection: USymbolicCollection<UAllocatedSymbolicSetId<Element, Reg>, Element, UBoolSort>,
        key: Element
    ): UExpr<UBoolSort> {
        TODO("Not yet implemented")
    }

    override fun UContext.mkLValue(
        collection: USymbolicCollection<UAllocatedSymbolicSetId<Element, Reg>, Element, UBoolSort>,
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

    override fun <Type> map(composer: UComposer<Type>): UAllocatedSymbolicSetId<Element, Reg> {
        TODO("Not yet implemented")
    }

    override fun keyInfo(): USymbolicCollectionKeyInfo<Element, *> {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R {
        TODO("Not yet implemented")
    }

    fun emptyRegion(): UMemoryRegion<Element, UBoolSort> {
        TODO("Not yet implemented")
    }

    override fun rebindKey(key: Element): DecomposedKey<*, UBoolSort>? {
        TODO("Not yet implemented")
    }
}

class UInputSymbolicSetId<Element, Reg: Region<Reg>>(
    val elementInfo: USymbolicCollectionKeyInfo<Element, Reg>,
    contextMemory: UWritableMemory<*>?
) : USymbolicSetId<Element, UInputSymbolicSetId<Element, Reg>>(contextMemory) {

    override val sort: UBoolSort
        get() = TODO("Not yet implemented")

    override val defaultValue: UBoolExpr?
        get() = TODO("Not yet implemented")

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputSymbolicSetId<Element, Reg>, Element, UBoolSort>,
        key: Element
    ): UExpr<UBoolSort> {
        TODO("Not yet implemented")
    }

    override fun UContext.mkLValue(
        collection: USymbolicCollection<UInputSymbolicSetId<Element, Reg>, Element, UBoolSort>,
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

    override fun <Type> map(composer: UComposer<Type>): UInputSymbolicSetId<Element, Reg> {
        TODO("Not yet implemented")
    }

    override fun keyInfo(): USymbolicCollectionKeyInfo<Element, *> {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R {
        TODO("Not yet implemented")
    }

    override fun rebindKey(key: Element): DecomposedKey<*, UBoolSort>? {
        TODO("Not yet implemented")
    }
}

private class SymbolicSetRegionBuilder<Key, Reg : Region<Reg>>(
    private val collectionId: USymbolicSetId<Key, *>
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
