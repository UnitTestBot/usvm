package org.usvm.machine.model

import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.UArrayRegionId
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.array.length.UArrayLengthsRegion
import org.usvm.collection.array.length.UArrayLengthsRegionId
import org.usvm.language.types.ArrayLikeConcretePythonType
import org.usvm.language.types.ArrayType
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.UPythonContext
import org.usvm.machine.utils.PyModelWrapper
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelBase


class PyModel(
    val ctx: UPythonContext,
    private val underlyingModel: UModelBase<PythonType>,
    val typeSystem: PythonTypeSystem
) : UModelBase<PythonType>(
    ctx,
    underlyingModel.stack,
    underlyingModel.types,
    underlyingModel.mocker,
    underlyingModel.regions,
    underlyingModel.nullRef
) {
    private inner class WrappedArrayIndexRegion<ArrayType, Sort: USort>(
        val region: UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, KIntSort>, UAddressSort>,
        val model: PyModel,
        val ctx: UPythonContext
    ) : UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, KIntSort>, UAddressSort> {
        override fun read(key: UArrayIndexLValue<ArrayType, Sort, KIntSort>): UExpr<UAddressSort> {
            val underlyingResult = region.read(key)
            val array = key.ref as UConcreteHeapRef
            if (array.address > 0)  // allocated object
                return underlyingResult
            val arrayType = PyModelWrapper(model).getConcreteType(array)
            require(arrayType != null && arrayType is ArrayLikeConcretePythonType)
            val constraints = arrayType.elementConstraints
            if (constraints.all { it.applyInterpreted(array, underlyingResult as UConcreteHeapRef, model, ctx) }) {
                return underlyingResult
            }
            return nullRef
        }
    }

    private inner class WrappedArrayLengthRegion(
        val ctx: UPythonContext,
        val region: UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, KIntSort>, KIntSort>
    ): UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, KIntSort>, KIntSort> {
        override fun read(key: UArrayLengthLValue<ArrayType, KIntSort>): UExpr<KIntSort> {
            val underlyingResult = region.read(key)
            if (ctx.mkArithLt(underlyingResult, ctx.mkIntNum(0)).isTrue) {
                return ctx.mkIntNum(0)
            }
            return underlyingResult
        }
    }

    /* private inner class WrappedSetRegion<SetType>(
        val region: UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort>,
        val ps: UPathConstraints<SetType>
    ): UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort>{

        val realRegion by lazy { TODO() }
        override fun read(key: URefSetEntryLValue<SetType>): UExpr<UBoolSort> {
            return key.setRef.uctx.mkBool(key in realRegion)
        }

    } */

    @Suppress("UNCHECKED_CAST")
    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort> {
        if (regionId is UArrayRegionId<*, *, *> &&
            regionId.sort == regionId.sort.uctx.addressSort &&
            regionId.arrayType == ArrayType
        ) {
            val region = super.getRegion(regionId) as UReadOnlyMemoryRegion<UArrayIndexLValue<Any, Sort, KIntSort>, UAddressSort>
            return WrappedArrayIndexRegion(region, this, ctx) as UReadOnlyMemoryRegion<Key, Sort>
        }
        if (regionId is UArrayLengthsRegionId<*, *> && regionId.sort == ctx.intSort && regionId.arrayType == ArrayType) {
            val region = super.getRegion(regionId) as UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, KIntSort>, KIntSort>
            return WrappedArrayLengthRegion(ctx, region) as UReadOnlyMemoryRegion<Key, Sort>
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

fun UModelBase<PythonType>.toPyModel(ctx: UPythonContext, typeSystem: PythonTypeSystem): PyModel {
    if (this is PyModel)
        return this
    return PyModel(ctx, this, typeSystem)
}