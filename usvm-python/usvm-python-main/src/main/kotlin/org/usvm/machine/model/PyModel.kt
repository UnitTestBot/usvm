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
import org.usvm.language.types.ArrayType
import org.usvm.language.types.ObjectDictType
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.UPythonContext
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelBase


class PyModel(
    private val ctx: UPythonContext,
    private val underlyingModel: UModelBase<PythonType>,
    private val typeSystem: PythonTypeSystem,
    ps: UPathConstraints<PythonType>
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
            return WrappedSetRegion(ctx, region, setKeys) as UReadOnlyMemoryRegion<Key, Sort>
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
    ps: UPathConstraints<PythonType>
): PyModel {
    if (this is PyModel)
        return this
    return PyModel(ctx, this, typeSystem, ps)
}