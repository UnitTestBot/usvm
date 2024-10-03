package org.usvm.collection.set

import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.isFalse
import org.usvm.memory.UMemoryUpdatesVisitor
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UUpdateNode
import org.usvm.regions.Region

internal class USetRegionBuilder<Element, Reg : Region<Reg>>(
    private val baseRegion: Reg,
    private val keyInfo: USymbolicCollectionKeyInfo<Element, Reg>,
    private val topRegion: Reg
) : UMemoryUpdatesVisitor<Element, UBoolSort, Reg> {
    override fun visitSelect(result: Reg, key: Element): UExpr<UBoolSort> {
        error("Unexpected reading")
    }

    override fun visitInitialValue(): Reg = baseRegion

    override fun visitUpdate(
        previous: Reg,
        update: UUpdateNode<Element, UBoolSort>
    ): Reg = when (update) {
        is UPinpointUpdateNode -> {
            val keyReg = keyInfo.keyToRegion(update.key)
            when {
                keyReg == topRegion -> topRegion
                update.guard.isFalse -> previous
//                update.guard.isTrue && update.value.isFalse -> previous.subtract(keyReg)
                else -> previous.union(keyReg)
            }
        }

        is URangedUpdateNode<*, *, Element, *, UBoolSort> -> {
            if (update.guard.isFalse) {
                previous
            } else {
                val updatedKeys = update.adapter.region<Reg>()
                previous.union(updatedKeys)
            }
        }
    }
}
