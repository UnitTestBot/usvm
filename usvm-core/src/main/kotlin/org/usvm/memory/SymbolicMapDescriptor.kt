package org.usvm.memory

import io.ksmt.cache.hash
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.uctx
import org.usvm.util.Region
import org.usvm.util.SetRegion

abstract class USymbolicMapDescriptor<Key : USort, Value : USort, Reg : Region<Reg>> {
    abstract val keySort: Key
    abstract val valueSort: Value
    abstract val defaultValue: UExpr<Value> // not used for descriptor comparison
    abstract val info: Any?

    abstract fun mkKeyRegion(key: UExpr<Key>): Reg
    abstract fun mkKeyRangeRegion(key1: UExpr<Key>, key2: UExpr<Key>): Reg

    abstract fun keyEqSymbolic(key1: UExpr<Key>, key2: UExpr<Key>): UBoolExpr
    abstract fun keyCmpSymbolic(key1: UExpr<Key>, key2: UExpr<Key>): UBoolExpr
    abstract fun keyCmpConcrete(key1: UExpr<Key>, key2: UExpr<Key>): Boolean

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as USymbolicMapDescriptor<*, *, *>

        if (keySort != other.keySort) return false
        if (valueSort != other.valueSort) return false
        return info == other.info
    }

    override fun hashCode(): Int = hash(keySort, valueSort, info)
    override fun toString(): String =
        "Descriptor(keySort=$keySort, valueSort=$valueSort, info=$info)"
}

class UHeapRefPoint(val ref: UHeapRef) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UHeapRefPoint) return false

        val otherRef = other.ref
        return if (ref is UConcreteHeapRef) {
            otherRef is UConcreteHeapRef && ref.address == otherRef.address
        } else {
            otherRef !is UConcreteHeapRef
        }
    }

    override fun hashCode(): Int = if (ref is UConcreteHeapRef) ref.address else -1

    override fun toString(): String = "$ref"
}

class USymbolicObjectReferenceMapDescriptor<Value : USort>(
    override val valueSort: Value,
    override val defaultValue: UExpr<Value>,
    override val info: Any? = null
) : USymbolicMapDescriptor<UAddressSort, Value, SetRegion<UHeapRefPoint>>() {

    override val keySort: UAddressSort = valueSort.uctx.addressSort

    override fun mkKeyRegion(
        key: UHeapRef
    ): SetRegion<UHeapRefPoint> = SetRegion.singleton(UHeapRefPoint(key))

    override fun mkKeyRangeRegion(
        key1: UHeapRef,
        key2: UHeapRef
    ) = error("Heap references should not be used in range queries!")

    override fun keyEqSymbolic(
        key1: UHeapRef,
        key2: UHeapRef
    ): UBoolExpr = with(key1.ctx) {
        key1 eq key2
    }

    override fun keyCmpSymbolic(
        key1: UHeapRef,
        key2: UHeapRef
    ): UBoolExpr = error("Heap references should not be compared!")

    override fun keyCmpConcrete(
        key1: UHeapRef,
        key2: UHeapRef
    ): Boolean = error("Heap references should not be compared!")
}

class USymbolicIndexMapDescriptor<Value : USort>(
    override val valueSort: Value,
    override val defaultValue: UExpr<Value>,
    override val info: Any? = null
) : USymbolicMapDescriptor<USizeSort, Value, UArrayIndexRegion>() {
    override val keySort: USizeSort = valueSort.uctx.sizeSort

    override fun mkKeyRegion(key: UExpr<USizeSort>): UArrayIndexRegion =
        indexRegion(key)

    override fun mkKeyRangeRegion(key1: UExpr<USizeSort>, key2: UExpr<USizeSort>): UArrayIndexRegion =
        indexRangeRegion(key1, key2)

    override fun keyEqSymbolic(key1: UExpr<USizeSort>, key2: UExpr<USizeSort>): UBoolExpr =
        indexEq(key1, key2)

    override fun keyCmpSymbolic(key1: UExpr<USizeSort>, key2: UExpr<USizeSort>): UBoolExpr =
        indexLeSymbolic(key1, key2)

    override fun keyCmpConcrete(key1: UExpr<USizeSort>, key2: UExpr<USizeSort>): Boolean =
        indexLeConcrete(key1, key2)
}
