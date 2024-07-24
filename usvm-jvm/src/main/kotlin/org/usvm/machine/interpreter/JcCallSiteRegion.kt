package org.usvm.machine.interpreter

import org.jacodb.api.jvm.cfg.JcLambdaExpr
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.JcContext
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId

class JcLambdaCallSiteRegionId(private val ctx: JcContext) : UMemoryRegionId<Nothing, UAddressSort> {
    override val sort: UAddressSort
        get() = ctx.addressSort

    override fun emptyRegion(): UMemoryRegion<Nothing, UAddressSort> =
        JcLambdaCallSiteMemoryRegion(ctx)
}

internal class JcLambdaCallSiteMemoryRegion(
    private val ctx: JcContext,
    private val callSites: UPersistentHashMap<UConcreteHeapAddress, JcLambdaCallSite> = persistentHashMapOf(),
) : UMemoryRegion<Nothing, UAddressSort> {
    fun writeCallSite(callSite: JcLambdaCallSite, ownership: MutabilityOwnership) =
        JcLambdaCallSiteMemoryRegion(ctx, callSites.put(callSite.ref.address, callSite, ownership))

    fun findCallSite(ref: UConcreteHeapRef): JcLambdaCallSite? = callSites[ref.address]

    override fun read(key: Nothing): UExpr<UAddressSort> {
        error("Unsupported operation for call site region")
    }

    override fun write(
        key: Nothing,
        value: UExpr<UAddressSort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership,
    ): UMemoryRegion<Nothing, UAddressSort> {
        error("Unsupported operation for call site region")
    }
}

data class JcLambdaCallSite(
    val ref: UConcreteHeapRef,
    val lambda: JcLambdaExpr,
    val callSiteArgs: List<UExpr<*>>
)
