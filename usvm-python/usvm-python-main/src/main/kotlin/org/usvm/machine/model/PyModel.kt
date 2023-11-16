package org.usvm.machine.model

import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.UArrayRegionId
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.array.length.UArrayLengthsRegionId
import org.usvm.collection.set.primitive.USetRegionId
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.set.ref.URefSetRegionId
import org.usvm.constraints.UPathConstraints
import org.usvm.language.types.*
import org.usvm.machine.UPythonContext
import org.usvm.machine.symbolicobjects.PreallocatedObjects
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelBase


class PyModel(
    private val ctx: UPythonContext,
    private val underlyingModel: UModelBase<PythonType>,
    private val typeSystem: PythonTypeSystem,
    ps: UPathConstraints<PythonType>,
    private val preallocatedObjects: PreallocatedObjects
) : UModelBase<PythonType>(
    ctx,
    underlyingModel.stack,
    underlyingModel.types,
    underlyingModel.mocker,
    underlyingModel.regions,
    underlyingModel.nullRef
) {
    private val setKeys = WrappedSetRegion.constructKeys(ctx, ps, underlyingModel)

    @Suppress("UNCHECKED_CAST")
    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort> {
        if (regionId is UArrayRegionId<*, *, *> &&
            regionId.sort == regionId.sort.uctx.addressSort &&
            regionId.arrayType == ArrayType
        ) {
            val region = super.getRegion(regionId) as UReadOnlyMemoryRegion<UArrayIndexLValue<Any, Sort, KIntSort>, UAddressSort>
            return WrappedArrayIndexRegion(region, this, ctx, nullRef) as UReadOnlyMemoryRegion<Key, Sort>
        }
        if (regionId is UArrayLengthsRegionId<*, *> && regionId.sort == ctx.intSort && regionId.arrayType == ArrayType) {
            val region = super.getRegion(regionId) as UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, KIntSort>, KIntSort>
            return WrappedArrayLengthRegion(ctx, region) as UReadOnlyMemoryRegion<Key, Sort>
        }
        if (regionId is URefSetRegionId<*> && regionId.setType == ObjectDictType) {
            val region = super.getRegion(regionId) as UReadOnlyMemoryRegion<URefSetEntryLValue<ObjectDictType>, UBoolSort>
            return WrappedSetRegion(ctx, region, setKeys, typeSystem, preallocatedObjects, underlyingModel.types, true)
                    as UReadOnlyMemoryRegion<Key, Sort>
        }
        if (regionId is URefSetRegionId<*> && regionId.setType == DictType(typeSystem)) {
            val region = super.getRegion(regionId) as UReadOnlyMemoryRegion<URefSetEntryLValue<DictType>, UBoolSort>
            return WrappedSetRegion(ctx, region, setKeys, typeSystem, preallocatedObjects, underlyingModel.types, false)
                    as UReadOnlyMemoryRegion<Key, Sort>
        }
        return super.getRegion(regionId)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PyModel)
            return false
        return underlyingModel == other.underlyingModel
    }

    override fun hashCode(): Int {
        return underlyingModel.hashCode()
    }
}

fun UModelBase<PythonType>.toPyModel(
    ctx: UPythonContext,
    typeSystem: PythonTypeSystem,
    ps: UPathConstraints<PythonType>,
    preallocatedObjects: PreallocatedObjects
): PyModel {
    if (this is PyModel)
        return this
    return PyModel(ctx, this, typeSystem, ps, preallocatedObjects)
}