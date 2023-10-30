package org.usvm.machine.interpreter

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.jacodb.api.cfg.JcLambdaExpr
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
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
    private val callSites: PersistentMap<UConcreteHeapAddress, JcLambdaCallSite> = persistentHashMapOf()
) : UMemoryRegion<Nothing, UAddressSort> {
    fun writeCallSite(callSite: JcLambdaCallSite) =
        JcLambdaCallSiteMemoryRegion(ctx, callSites.put(callSite.ref.address, callSite))

    fun findCallSite(ref: UConcreteHeapRef): JcLambdaCallSite? = callSites[ref.address]

    override fun read(key: Nothing): UExpr<UAddressSort> {
        error("Unsupported operation for call site region")
    }

    override fun write(
        key: Nothing,
        value: UExpr<UAddressSort>,
        guard: UBoolExpr
    ): UMemoryRegion<Nothing, UAddressSort> {
        error("Unsupported operation for call site region")
    }
}

data class JcLambdaCallSite(
    val ref: UConcreteHeapRef,
    val lambda: JcLambdaExpr,
    val callSiteArgs: List<UExpr<*>>
)
