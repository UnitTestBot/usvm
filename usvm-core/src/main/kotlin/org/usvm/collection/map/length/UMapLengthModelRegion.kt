package org.usvm.collection.map.length

import io.ksmt.solver.KModel
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class UMapLengthModelRegion<MapType>(
    private val regionId: UMapLengthRegionId<MapType>,
) : UMapLengthRegion<MapType> {
    val defaultValue by lazy { regionId.sort.sampleUValue() }

    abstract val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>?

    override fun read(key: UMapLengthLValue<MapType>): UExpr<USizeSort> {
        val ref = modelEnsureConcreteInputRef(key.ref) ?: return defaultValue
        return inputMapLength?.read(ref) ?: defaultValue
    }

    override fun write(
        key: UMapLengthLValue<MapType>,
        value: UExpr<USizeSort>,
        guard: UBoolExpr
    ): UMemoryRegion<UMapLengthLValue<MapType>, USizeSort> {
        error("Illegal operation for a model")
    }
}

class UMapLengthLazyModelRegion<MapType>(
    regionId: UMapLengthRegionId<MapType>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputLengthDecoder: UCollectionDecoder<UHeapRef, USizeSort>?
) : UMapLengthModelRegion<MapType>(regionId) {
    override val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>? by lazy {
        inputLengthDecoder?.decodeCollection(model, addressesMapping)
    }
}

class UMapLengthEagerModelRegion<MapType>(
    regionId: UMapLengthRegionId<MapType>,
    override val inputMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>?
) : UMapLengthModelRegion<MapType>(regionId)
