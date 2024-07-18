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

class PyModel(
    private val ctx: PyContext,
    private val underlyingModel: UModelBase<PythonType>,
    info: GivenPathConstraintsInfo,
) : UModelBase<PythonType>(
    ctx,
    underlyingModel.stack,
    underlyingModel.types,
    underlyingModel.mocker,
    underlyingModel.regions,
    underlyingModel.nullRef
) {
    val forcedConcreteTypes: MutableMap<UConcreteHeapRef, PythonType> = mutableMapOf()
    val psInfo = when (info) {
        is GenerateNewFromPathConstraints -> getPathConstraintsInfo(ctx, info.ps, underlyingModel)
        is UseOldPathConstraintsInfo -> info.oldInfo
    }

    val possibleRefKeys: Set<UConcreteHeapRef>
        get() = psInfo.setRefKeys

    val possibleIntKeys: Set<KInterpretedValue<KIntSort>>
        get() = psInfo.setIntKeys

    @Suppress("UNCHECKED_CAST")
    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort> {
        val region = super.getRegion(regionId)

        return when {
            regionId is UArrayRegionId<*, *, *> &&
                regionId.sort == ctx.addressSort &&
                regionId.arrayType == ArrayType
            -> {
                WrappedArrayIndexRegion(
                    region as UReadOnlyMemoryRegion<UArrayIndexLValue<Any, Sort, KIntSort>, UAddressSort>,
                    this,
                    ctx,
                    nullRef
                ) as UReadOnlyMemoryRegion<Key, Sort>
            }

            regionId is UArrayLengthsRegionId<*, *> &&
                regionId.sort == ctx.intSort &&
                regionId.arrayType == ArrayType
            -> {
                WrappedArrayLengthRegion(
                    ctx,
                    region as UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, KIntSort>, KIntSort>
                ) as UReadOnlyMemoryRegion<Key, Sort>
            }

            regionId is URefSetRegionId<*> && regionId.setType == ObjectDictType -> {
                WrappedRefSetRegion(
                    ctx,
                    region as UReadOnlyMemoryRegion<URefSetEntryLValue<ObjectDictType>, UBoolSort>,
                    psInfo.setRefKeys
                ) as UReadOnlyMemoryRegion<Key, Sort>
            }

            regionId is URefSetRegionId<*> && regionId.setType == RefDictType -> {
                WrappedRefSetRegion(
                    ctx,
                    region as UReadOnlyMemoryRegion<URefSetEntryLValue<RefDictType>, UBoolSort>,
                    psInfo.setRefKeys
                ) as UReadOnlyMemoryRegion<Key, Sort>
            }

            regionId is USetRegionId<*, *, *> && regionId.setType == IntDictType -> {
                WrappedSetRegion(
                    ctx,
                    region as UReadOnlyMemoryRegion<USetEntryLValue<IntDictType, KIntSort, USizeRegion>, UBoolSort>,
                    psInfo.setIntKeys
                ) as UReadOnlyMemoryRegion<Key, Sort>
            }

            regionId is URefSetRegionId<*> && regionId.setType == RefSetType -> {
                WrappedRefSetRegion(
                    ctx,
                    region as UReadOnlyMemoryRegion<URefSetEntryLValue<RefSetType>, UBoolSort>,
                    psInfo.setRefKeys
                ) as UReadOnlyMemoryRegion<Key, Sort>
            }

            regionId is USetRegionId<*, *, *> && regionId.setType == IntSetType -> {
                WrappedSetRegion(
                    ctx,
                    region as UReadOnlyMemoryRegion<USetEntryLValue<IntSetType, KIntSort, USizeRegion>, UBoolSort>,
                    psInfo.setIntKeys
                ) as UReadOnlyMemoryRegion<Key, Sort>
            }

            regionId is URefMapRegionId<*, *> && regionId.mapType == ObjectDictType -> {
                WrappedRefMapRegion(
                    ctx,
                    region as UReadOnlyMemoryRegion<URefMapEntryLValue<ObjectDictType, UAddressSort>, UAddressSort>,
                    psInfo.setRefKeys,
                    underlyingModel
                ) as UReadOnlyMemoryRegion<Key, Sort>
            }

            else -> {
                region
            }
        }
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

sealed interface GivenPathConstraintsInfo

class GenerateNewFromPathConstraints(val ps: UPathConstraints<PythonType>) : GivenPathConstraintsInfo

class UseOldPathConstraintsInfo(val oldInfo: PathConstraintsInfo) : GivenPathConstraintsInfo
