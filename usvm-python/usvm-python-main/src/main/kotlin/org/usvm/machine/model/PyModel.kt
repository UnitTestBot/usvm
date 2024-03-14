package org.usvm.machine.model

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KIntSort
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.USort
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.UArrayRegionId
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.array.length.UArrayLengthsRegionId
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.map.ref.URefMapRegionId
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.primitive.USetRegionId
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.set.ref.URefSetRegionId
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.PyContext
import org.usvm.machine.model.regions.WrappedArrayIndexRegion
import org.usvm.machine.model.regions.WrappedArrayLengthRegion
import org.usvm.machine.model.regions.WrappedRefMapRegion
import org.usvm.machine.model.regions.WrappedRefSetRegion
import org.usvm.machine.model.regions.WrappedSetRegion
import org.usvm.machine.types.ArrayType
import org.usvm.machine.types.IntDictType
import org.usvm.machine.types.IntSetType
import org.usvm.machine.types.ObjectDictType
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.RefDictType
import org.usvm.machine.types.RefSetType
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.key.USizeRegion
import org.usvm.model.UModelBase
import org.usvm.uctx

class PyModel(
    private val ctx: PyContext,
    private val underlyingModel: UModelBase<PythonType>,
    ps: UPathConstraints<PythonType>,
    suggestedPsInfo: PathConstraintsInfo? = null,
) : UModelBase<PythonType>(
    ctx,
    underlyingModel.stack,
    underlyingModel.types,
    underlyingModel.mocker,
    underlyingModel.regions,
    underlyingModel.nullRef
) {
    val forcedConcreteTypes: MutableMap<UConcreteHeapRef, PythonType> = mutableMapOf()
    val psInfo = suggestedPsInfo ?: getPathConstraintsInfo(ctx, ps, underlyingModel)

    val possibleRefKeys: Set<UConcreteHeapRef>
        get() = psInfo.setRefKeys

    val possibleIntKeys: Set<KInterpretedValue<KIntSort>>
        get() = psInfo.setIntKeys

    @Suppress("UNCHECKED_CAST")
    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort> {
        if (regionId is UArrayRegionId<*, *, *> &&
            regionId.sort == regionId.sort.uctx.addressSort &&
            regionId.arrayType == ArrayType
        ) {
            val region = super.getRegion(
                regionId
            ) as UReadOnlyMemoryRegion<UArrayIndexLValue<Any, Sort, KIntSort>, UAddressSort>
            return WrappedArrayIndexRegion(region, this, ctx, nullRef) as UReadOnlyMemoryRegion<Key, Sort>
        }
        if (regionId is UArrayLengthsRegionId<*, *> && regionId.sort == ctx.intSort && regionId.arrayType == ArrayType) {
            val region = super.getRegion(
                regionId
            ) as UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, KIntSort>, KIntSort>
            return WrappedArrayLengthRegion(ctx, region) as UReadOnlyMemoryRegion<Key, Sort>
        }
        if (regionId is URefSetRegionId<*> && regionId.setType == ObjectDictType) {
            val region = super.getRegion(
                regionId
            ) as UReadOnlyMemoryRegion<URefSetEntryLValue<ObjectDictType>, UBoolSort>
            return WrappedRefSetRegion(ctx, region, psInfo.setRefKeys)
                as UReadOnlyMemoryRegion<Key, Sort>
        }
        if (regionId is URefSetRegionId<*> && regionId.setType == RefDictType) {
            val region = super.getRegion(regionId) as UReadOnlyMemoryRegion<URefSetEntryLValue<RefDictType>, UBoolSort>
            return WrappedRefSetRegion(ctx, region, psInfo.setRefKeys) as UReadOnlyMemoryRegion<Key, Sort>
        }
        if (regionId is USetRegionId<*, *, *> && regionId.setType == IntDictType) {
            val region = super.getRegion(
                regionId
            ) as UReadOnlyMemoryRegion<USetEntryLValue<IntDictType, KIntSort, USizeRegion>, UBoolSort>
            return WrappedSetRegion(ctx, region, psInfo.setIntKeys) as UReadOnlyMemoryRegion<Key, Sort>
        }
        if (regionId is URefSetRegionId<*> && regionId.setType == RefSetType) {
            val region = super.getRegion(regionId) as UReadOnlyMemoryRegion<URefSetEntryLValue<RefSetType>, UBoolSort>
            return WrappedRefSetRegion(ctx, region, psInfo.setRefKeys) as UReadOnlyMemoryRegion<Key, Sort>
        }
        if (regionId is USetRegionId<*, *, *> && regionId.setType == IntSetType) {
            val region = super.getRegion(
                regionId
            ) as UReadOnlyMemoryRegion<USetEntryLValue<IntSetType, KIntSort, USizeRegion>, UBoolSort>
            return WrappedSetRegion(ctx, region, psInfo.setIntKeys) as UReadOnlyMemoryRegion<Key, Sort>
        }
        if (regionId is URefMapRegionId<*, *> && regionId.mapType == ObjectDictType) {
            val region = super.getRegion(
                regionId
            ) as UReadOnlyMemoryRegion<URefMapEntryLValue<ObjectDictType, UAddressSort>, UAddressSort>
            return WrappedRefMapRegion(
                ctx,
                region,
                psInfo.setRefKeys,
                underlyingModel
            ) as UReadOnlyMemoryRegion<Key, Sort>
        }
        return super.getRegion(regionId)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PyModel) {
            return false
        }
        return underlyingModel == other.underlyingModel
    }

    override fun hashCode(): Int {
        return underlyingModel.hashCode()
    }
}
