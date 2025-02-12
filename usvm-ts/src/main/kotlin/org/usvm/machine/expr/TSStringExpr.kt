package org.usvm.machine.expr

import com.jetbrains.rd.framework.util.RdCoroutineScope.Companion.override
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USymbol
import org.usvm.collections.immutable.getOrDefault
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.machine.TSContext
import org.usvm.machine.TSTransformer
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.guardedWrite
import org.usvm.sampleUValue

data class TSStringLValue(val addr: UHeapRef) : ULValue<TSStringLValue, TSStringSort> {
    override val sort: TSStringSort
        get() = addr.tctx.stringSort

    override val memoryRegionId: UMemoryRegionId<TSStringLValue, TSStringSort> = TSStringRegionId(sort)

    override val key: TSStringLValue
        get() = this
}

class TSStringRegionId(override val sort: TSStringSort) : UMemoryRegionId<TSStringLValue, TSStringSort> {
    override fun emptyRegion(): UMemoryRegion<TSStringLValue, TSStringSort> = TSStringMemoryRegion(sort)
}

internal class TSStringMemoryRegion(
    val sort: TSStringSort,
    private val concreteStringsPool: UPersistentHashMap<UConcreteHeapRef, UExpr<TSStringSort>> = persistentHashMapOf()
) : UMemoryRegion<TSStringLValue, TSStringSort> {

    override fun write(
        key: TSStringLValue,
        value: UExpr<TSStringSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<TSStringLValue, TSStringSort> {
        val updatedPools = when (key.addr) {
            is UConcreteHeapRef -> {
                val newConcreteStringPool: UPersistentHashMap<UConcreteHeapRef, UExpr<TSStringSort>> =
                    concreteStringsPool.guardedWrite<UConcreteHeapRef, TSStringSort>(
                        key.addr,
                        value,
                        guard,
                        ownership
                    ) { sort.sampleUValue() }
                newConcreteStringPool
            }

            else -> TODO("Not yet implemented")
        }

        return TSStringMemoryRegion(sort, updatedPools)
    }

    override fun read(key: TSStringLValue): UExpr<TSStringSort> {
        TODO("Not yet implemented")
    }
}

class TSStringReading internal constructor(
    ctx: UContext<*>,
    val regionId: TSStringRegionId,
    val addr: UHeapRef,
    override val sort: TSStringSort
) : USymbol<TSStringSort>(ctx) {
    override fun accept(transformer: KTransformerBase): KExpr<TSStringSort> {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }

    override fun internEquals(other: Any): Boolean {
        TODO("Not yet implemented")
    }

    override fun internHashCode(): Int {
        TODO("Not yet implemented")
    }
}

class TSConcreteString(ctx: TSContext, val value: String) : UExpr<TSStringSort>(ctx) {
    override val sort: TSStringSort
        get() = tctx.stringSort

    override fun accept(transformer: KTransformerBase): KExpr<TSStringSort> {
        require(transformer is TSTransformer) { "Only TS transformer is allowed here" }
        return transformer.transform(this)
    }

    override fun print(printer: ExpressionPrinter) {
        printer.append("\"$value\"")
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) { value }

    override fun internHashCode(): Int = hash(value)

}