package org.usvm.model.region

import io.ksmt.solver.KModel
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.memory.UAddressCounter
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.collection.region.USymbolicMapLengthRef
import org.usvm.memory.collection.region.USymbolicMapLengthRegion
import org.usvm.memory.collection.region.USymbolicMapLengthsRegionId
import org.usvm.model.AddressesMapping
import org.usvm.sampleUValue
import org.usvm.solver.UCollectionDecoder

abstract class USymbolicMapLengthModelRegion<MapType>(
    private val regionId: USymbolicMapLengthsRegionId<MapType>,
) : USymbolicMapLengthRegion<MapType> {
    abstract fun getInputSymbolicMapLength(): UReadOnlyMemoryRegion<UHeapRef, USizeSort>?

    override fun read(key: USymbolicMapLengthRef<MapType>): UExpr<USizeSort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        val ref = key.ref
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS) {
            "Unexpected ref in model: $ref"
        }

        return getInputSymbolicMapLength()?.read(ref)
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
    private var inputSymbolicMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>? = null

    override fun getInputSymbolicMapLength(): UReadOnlyMemoryRegion<UHeapRef, USizeSort>? {
        if (inputSymbolicMapLength == null) {
            inputSymbolicMapLength = inputLengthDecoder?.decodeCollection(model, addressesMapping)
        }
        return inputSymbolicMapLength
    }
}

class USymbolicMapLengthEagerModelRegion<SymbolicMapType>(
    regionId: USymbolicMapLengthsRegionId<SymbolicMapType>,
    private val inputSymbolicMapLength: UReadOnlyMemoryRegion<UHeapRef, USizeSort>?
) : USymbolicMapLengthModelRegion<SymbolicMapType>(regionId) {
    override fun getInputSymbolicMapLength(): UReadOnlyMemoryRegion<UHeapRef, USizeSort>? = inputSymbolicMapLength
}
