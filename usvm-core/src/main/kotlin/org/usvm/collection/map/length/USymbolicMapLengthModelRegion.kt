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

abstract class USymbolicMapLengthModelRegion<MapType>(
    private val regionId: USymbolicMapLengthsRegionId<MapType>,
) : USymbolicMapLengthRegion<MapType> {
    abstract val inputSymbolicMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>?

    override fun read(key: USymbolicMapLengthRef<MapType>): UExpr<USizeSort> {
        val ref = modelEnsureConcreteInputRef(key.ref)
        return inputSymbolicMapLength
            ?.read(ref)
            ?: regionId.sort.sampleUValue()
    }

    override fun write(
        key: USymbolicMapLengthRef<MapType>,
        value: UExpr<USizeSort>,
        guard: UBoolExpr
    ): UMemoryRegion<USymbolicMapLengthRef<MapType>, USizeSort> {
        error("Illegal operation for a model")
    }
}

class USymbolicMapLengthLazyModelRegion<MapType>(
    regionId: USymbolicMapLengthsRegionId<MapType>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputLengthDecoder: UCollectionDecoder<UHeapRef, USizeSort>?
) : USymbolicMapLengthModelRegion<MapType>(regionId) {
    override val inputSymbolicMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>? by lazy {
        inputLengthDecoder?.decodeCollection(model, addressesMapping)
    }
}

class USymbolicMapLengthEagerModelRegion<SymbolicMapType>(
    regionId: USymbolicMapLengthsRegionId<SymbolicMapType>,
    override val inputSymbolicMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>?
) : USymbolicMapLengthModelRegion<SymbolicMapType>(regionId)
