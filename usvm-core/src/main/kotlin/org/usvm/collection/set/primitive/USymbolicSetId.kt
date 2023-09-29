package org.usvm.collection.set.primitive

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.set.USetUpdatesVisitor
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.collection.set.USymbolicSetElementRegion
import org.usvm.collection.set.USymbolicSetKeyInfo
import org.usvm.memory.ULValue
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UTreeUpdates
import org.usvm.memory.UWritableMemory
import org.usvm.uctx
import org.usvm.regions.Region
import org.usvm.regions.emptyRegionTree
import java.util.IdentityHashMap

abstract class USymbolicSetId<SetType, ElementSort : USort, Element, ElementReg : Region<ElementReg>, Reg : Region<Reg>,
        out SetId : USymbolicSetId<SetType, ElementSort, Element, ElementReg, Reg, SetId>>(
    val elementSort: ElementSort,
    val setType: SetType,
    val elementInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, ElementReg>,
) : USymbolicCollectionId<Element, UBoolSort, SetId> {
    override val sort: UBoolSort
        get() = elementSort.uctx.boolSort

    fun setRegionId(): USetRegionId<SetType, ElementSort, ElementReg> =
        USetRegionId(elementSort, setType, elementInfo)
}

class UAllocatedSetId<SetType, ElementSort : USort, Reg : Region<Reg>>(
    val setAddress: UConcreteHeapAddress,
    elementSort: ElementSort,
    setType: SetType,
    elementInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, Reg>,
) : USymbolicSetId<SetType, ElementSort, UExpr<ElementSort>, Reg, Reg,
    UAllocatedSetId<SetType, ElementSort, Reg>>(elementSort, setType, elementInfo) {

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort>,
        key: UExpr<ElementSort>,
        composer: UComposer<*, *>?,
    ): UExpr<UBoolSort> {
        if (collection.updates.isEmpty()) {
            return sort.uctx.falseExpr
        }

        if (composer == null) {
            return sort.uctx.mkAllocatedSetReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory()
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: UExpr<ElementSort>,
        value: UExpr<UBoolSort>,
        guard: UBoolExpr,
    ) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UExpr<ElementSort>): ULValue<*, UBoolSort> =
        USetEntryLValue(elementSort, sort.uctx.mkConcreteHeapRef(setAddress), key, setType, elementInfo)

    override fun emptyRegion(): UAllocatedSet<SetType, ElementSort, Reg> {
        val updates = UTreeUpdates<UExpr<ElementSort>, Reg, UBoolSort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    override fun keyInfo() = elementInfo

    private val regionCache = IdentityHashMap<Any?, Any>()

    fun <R : Region<R>> region(
        collection: USymbolicCollection<UAllocatedSetId<SetType, ElementSort, *>, UExpr<ElementSort>, UBoolSort>,
        keyInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, R>,
    ): R {
        val regionBuilder = USetUpdatesVisitor(
            baseRegion = keyInfo.bottomRegion(),
            keyInfo = keyInfo,
            topRegion = keyInfo.topRegion()
        )
        return collection.updates.accept(
            regionBuilder,
            regionCache.uncheckedCast()
        )
    }
}

class UInputSetId<SetType, ElementSort : USort, Reg : Region<Reg>>(
    elementSort: ElementSort,
    setType: SetType,
    elementInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, Reg>,
) : USymbolicSetId<SetType, ElementSort, USymbolicSetElement<ElementSort>, Reg, USymbolicSetElementRegion<Reg>,
    UInputSetId<SetType, ElementSort, Reg>>(elementSort, setType, elementInfo) {

    override fun instantiate(
        collection: USymbolicCollection<UInputSetId<SetType, ElementSort, Reg>, USymbolicSetElement<ElementSort>, UBoolSort>,
        key: USymbolicSetElement<ElementSort>,
        composer: UComposer<*, *>?,
    ): UExpr<UBoolSort> {
        if (composer == null) {
            return sort.uctx.mkInputSetReading(collection, key.first, key.second)
        }

        val memory = composer.memory.toWritableMemory()
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: USymbolicSetElement<ElementSort>,
        value: UExpr<UBoolSort>,
        guard: UBoolExpr,
    ) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: USymbolicSetElement<ElementSort>): ULValue<*, UBoolSort> =
        USetEntryLValue(elementSort, key.first, key.second, setType, elementInfo)

    override fun emptyRegion(): UInputSet<SetType, ElementSort, Reg> {
        val updates = UTreeUpdates<USymbolicSetElement<ElementSort>, USymbolicSetElementRegion<Reg>, UBoolSort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    override fun keyInfo() = USymbolicSetKeyInfo(elementInfo)

    @Suppress("UNUSED_PARAMETER")
    fun <R : Region<R>> region(
        collection: USymbolicCollection<UInputSetId<SetType, ElementSort, *>, USymbolicSetElement<ElementSort>, UBoolSort>,
        keyInfo: USymbolicCollectionKeyInfo<USymbolicSetElement<ElementSort>, R>,
    ): R = keyInfo.topRegion()
}
