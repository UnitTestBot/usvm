package org.usvm.collection.string

import io.ksmt.utils.cast
import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UCharSort
import org.usvm.UComposer
import org.usvm.UConcreteChar
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UMockEvaluator
import org.usvm.USort
import org.usvm.api.readArrayIndex
import org.usvm.api.writeArrayIndex
import org.usvm.character
import org.usvm.collection.array.UAllocatedArray
import org.usvm.collection.array.UAllocatedArrayId
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.UArrayRegion
import org.usvm.collection.array.UArrayRegionId
import org.usvm.collection.array.USymbolicArrayAllocatedToAllocatedCopyAdapter
import org.usvm.collection.array.USymbolicArrayIndex
import org.usvm.constraints.UTypeEvaluator
import org.usvm.getIntValue
import org.usvm.isTrue
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyRegistersStack
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionAdapter
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UWritableMemory
import org.usvm.mkSizeExpr

class UConcreteStringBuilder<Type, USizeSort : USort>(
    override val ctx: UContext<USizeSort>,
    private val regionId: UArrayRegionId<Type, UCharSort, USizeSort>,
    length: Int,
    private val targetAddress: UConcreteHeapAddress,
    private val composer: UComposer<Type, USizeSort>
) : UWritableMemory<Type>,
    UArrayRegion<Type, UCharSort, USizeSort> {

    var charArray: CharArray? = CharArray(length)
        private set
    private val otherArrays: MutableMap<UConcreteHeapAddress, MutableMap<Int, Char>> = hashMapOf()
    private var baseMemory: UWritableMemory<Type>? = null

    private inline fun notConcrete(cont: (UWritableMemory<Type>) -> Nothing): Nothing {
        require(baseMemory == null)
        val charArray = this.charArray
        require(charArray != null)
        baseMemory = composer.memory.toWritableMemory()
        val targetRef = ctx.mkConcreteHeapRef(targetAddress)
        // Flushing already accumulated writes...
        for (i in charArray.indices) {
            baseMemory!!.writeArrayIndex(
                targetRef,
                ctx.mkSizeExpr(i),
                regionId.arrayType,
                regionId.sort,
                ctx.mkChar(charArray[i]),
                ctx.trueExpr
            )
        }
        for ((addr, map) in otherArrays.entries) {
            val ref = ctx.mkConcreteHeapRef(addr)
            for ((i, v) in map) {
                baseMemory!!.writeArrayIndex(
                    ref,
                    ctx.mkSizeExpr(i),
                    regionId.arrayType,
                    regionId.sort,
                    ctx.mkChar(v),
                    ctx.trueExpr
                )
            }
        }
        this.charArray = null
        cont(baseMemory!!)
    }

    override fun read(key: UArrayIndexLValue<Type, UCharSort, USizeSort>): UExpr<UCharSort> {
        error("Unexpected operation on fake memory for concrete string building")
    }

    private fun write(address: UConcreteHeapAddress, index: Int, character: Char) {
        if (address == targetAddress) {
            val charArray = this.charArray
            require(charArray != null)
            if (index !in charArray.indices) {
                error("Unexpected operation on fake memory for concrete string building")
            }
            charArray[index] = character
        } else {
            val otherArray = otherArrays.getOrPut(address) { hashMapOf() }
            otherArray[index] = character
        }
    }

    override fun write(
        key: UArrayIndexLValue<Type, UCharSort, USizeSort>,
        value: UExpr<UCharSort>,
        guard: UBoolExpr
    ): UMemoryRegion<UArrayIndexLValue<Type, UCharSort, USizeSort>, UCharSort> {
        require(baseMemory == null)
        val index = ctx.getIntValue(key.index)
        val ref = key.ref
        if (index == null || value !is UConcreteChar || !guard.isTrue || ref !is UConcreteHeapRef) {
            notConcrete {
                return (it.getRegion(key.memoryRegionId) as UMemoryRegion<UArrayIndexLValue<Type, UCharSort, USizeSort>, UCharSort>).write(
                    key,
                    value,
                    guard
                )
            }
        }
        write(ref.address, index, value.character)
        return this
    }

    override fun <Key, Sort : USort> setRegion(
        regionId: UMemoryRegionId<Key, Sort>,
        newRegion: UMemoryRegion<Key, Sort>
    ) {
        if (newRegion !== this) {
            error("Unexpected operation on fake memory for concrete string building")
        }
    }

    override fun <Key, Sort : USort> write(lvalue: ULValue<Key, Sort>, rvalue: UExpr<Sort>, guard: UBoolExpr) {
        getRegion(lvalue.memoryRegionId).write(lvalue.key, rvalue, guard)
    }

    override fun allocStatic(type: Type): UConcreteHeapRef {
        if (baseMemory != null) {
            return baseMemory!!.allocStatic(type)
        }
        error("Unexpected operation on fake memory for concrete string building")
    }

    override fun allocConcrete(type: Type): UConcreteHeapRef {
        if (baseMemory != null) {
            return baseMemory!!.allocConcrete(type)
        }
        error("Unexpected operation on fake memory for concrete string building")
    }

    override val stack: UReadOnlyRegistersStack
        get() {
            if (baseMemory != null) {
                return baseMemory!!.stack
            }
            error("Unexpected operation on fake memory for concrete string building")
        }
    override val mocker: UMockEvaluator
        get() {
            if (baseMemory != null) {
                return baseMemory!!.mocker
            }
            error("Unexpected operation on fake memory for concrete string building")
        }
    override val types: UTypeEvaluator<Type>
        get() {
            if (baseMemory != null) {
                return baseMemory!!.types
            }
            error("Unexpected operation on fake memory for concrete string building")
        }

    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UMemoryRegion<Key, Sort> {
        if (baseMemory != null) {
            return baseMemory!!.getRegion(regionId).cast()
        }
        if (regionId != this.regionId) {
            error("Unexpected operation on fake memory for concrete string building")
        }
        return this.uncheckedCast()
    }

    override fun nullRef(): UHeapRef {
        if (baseMemory != null) {
            return baseMemory!!.nullRef()
        }
        error("Unexpected operation on fake memory for concrete string building")
    }

    override fun toWritableMemory(): UWritableMemory<Type> = this

    private fun <SrcKey, SrcSort : USort> copyToBaseMemory(
        mem: UWritableMemory<Type>,
        srcCollection: USymbolicCollection<USymbolicCollectionId<SrcKey, SrcSort, *>, SrcKey, SrcSort>,
        dstRef: UHeapRef,
        type: Type,
        elementSort: UCharSort,
        operationGuard: UBoolExpr,
        allocatedDstAdapter: (UConcreteHeapRef) -> USymbolicCollectionAdapter<SrcKey, UExpr<USizeSort>, SrcSort, UCharSort>,
        inputDstAdapter: (UHeapRef) -> USymbolicCollectionAdapter<SrcKey, USymbolicArrayIndex<USizeSort>, SrcSort, UCharSort>
    ): UArrayRegion<Type, UCharSort, USizeSort> {
        val region = mem.getRegion(regionId)
        check(region is UArrayRegion<Type, UCharSort, USizeSort>) { "memcpy is not applicable to $region" }
        return region.memcpy(
            srcCollection,
            dstRef,
            type,
            elementSort,
            operationGuard,
            allocatedDstAdapter,
            inputDstAdapter
        )
    }

    override fun <SrcKey, SrcSort : USort> memcpy(
        srcCollection: USymbolicCollection<USymbolicCollectionId<SrcKey, SrcSort, *>, SrcKey, SrcSort>,
        dstRef: UHeapRef,
        type: Type,
        elementSort: UCharSort,
        operationGuard: UBoolExpr,
        allocatedDstAdapter: (UConcreteHeapRef) -> USymbolicCollectionAdapter<SrcKey, UExpr<USizeSort>, SrcSort, UCharSort>,
        inputDstAdapter: (UHeapRef) -> USymbolicCollectionAdapter<SrcKey, USymbolicArrayIndex<USizeSort>, SrcSort, UCharSort>
    ): UArrayRegion<Type, UCharSort, USizeSort> {
        when {
            srcCollection.collectionId is UAllocatedArrayId<*, *, *> && dstRef is UConcreteHeapRef -> {
                val srcRefAddress = srcCollection.collectionId.address
                val adapter =
                    allocatedDstAdapter(dstRef) as USymbolicArrayAllocatedToAllocatedCopyAdapter<USizeSort, *, *>
                val fromSrcIdxValue = ctx.getIntValue(adapter.srcFrom)
                val fromDstIdxValue = ctx.getIntValue(adapter.dstFrom)
                val toDstIdxValue = ctx.getIntValue(adapter.dstTo)
                if (fromSrcIdxValue == null ||
                    fromDstIdxValue == null ||
                    toDstIdxValue == null ||
                    !operationGuard.isTrue
                ) {
                    notConcrete {
                        return copyToBaseMemory(
                            it,
                            srcCollection,
                            dstRef,
                            type,
                            elementSort,
                            operationGuard,
                            allocatedDstAdapter,
                            inputDstAdapter
                        )
                    }
                }
                if (srcRefAddress == targetAddress) {
                    // Should we process this case normally?
                    error("Unexpected operation on fake memory for concrete string building")
                }
                val length = toDstIdxValue - fromDstIdxValue + 1
                if (length < 0) {
                    error("Unexpected operation on fake memory for concrete string building")
                }
                val toSrcIdxValue = fromSrcIdxValue + length - 1
                for (i in fromSrcIdxValue..toSrcIdxValue) {
                    val character =
                        otherArrays[srcRefAddress]?.get(i) ?: composer.memory.readArrayIndex(
                            ctx.mkConcreteHeapRef(srcRefAddress),
                            ctx.mkSizeExpr(i),
                            type,
                            elementSort
                        )
                            .let { (it as? UConcreteChar)?.character } ?: notConcrete {
                            return copyToBaseMemory(
                                it,
                                srcCollection,
                                dstRef,
                                type,
                                elementSort,
                                operationGuard,
                                allocatedDstAdapter,
                                inputDstAdapter
                            )
                        }

                    write(dstRef.address, i - fromSrcIdxValue + fromDstIdxValue, character)
                }
                return this
            }

            srcCollection.collectionId is UStringCollectionId<*> &&
                    srcCollection.collectionId.string is UStringLiteralExpr &&
                    dstRef is UConcreteHeapRef -> {
                val string = srcCollection.collectionId.string.s
                for (i in string.indices) {
                    write(dstRef.address, i, string[i])
                }
                return this
            }

            else -> {
                notConcrete {
                    return copyToBaseMemory(
                        it,
                        srcCollection,
                        dstRef,
                        type,
                        elementSort,
                        operationGuard,
                        allocatedDstAdapter,
                        inputDstAdapter
                    )
                }
            }
        }
    }

    override fun initializeAllocatedArray(
        address: UConcreteHeapAddress,
        arrayType: Type,
        sort: UCharSort,
        content: Map<UExpr<USizeSort>, UExpr<UCharSort>>,
        operationGuard: UBoolExpr
    ): UArrayRegion<Type, UCharSort, USizeSort> {
        error("Unexpected operation on fake memory for concrete string building")
    }

    override fun getAllocatedArray(
        arrayType: Type,
        sort: UCharSort,
        address: UConcreteHeapAddress
    ): UAllocatedArray<Type, UCharSort, USizeSort> {
        error("Unexpected operation on fake memory for concrete string building")
    }

}