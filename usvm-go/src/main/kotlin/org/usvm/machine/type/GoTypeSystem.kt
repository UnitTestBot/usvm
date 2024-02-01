package org.usvm.machine.type

import org.usvm.bridge.GoBridge
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class GoTypeSystem(
    private val bridge: GoBridge,
    override val typeOperationsTimeout: Duration
) : UTypeSystem<GoType> {
    private val goAnyType = bridge.getAnyType()
    private val topTypeStream by lazy { USupportTypeStream.from(this, goAnyType) }

    override fun topTypeStream(): UTypeStream<GoType> {
        return topTypeStream
    }

    override fun findSubtypes(type: GoType): Sequence<GoType> {
        val (subTypes, count) = bridge.findSubTypes(type)
        return subTypes.asSequence().take(count)
    }

    override fun isInstantiable(type: GoType): Boolean {
        return bridge.isInstantiable(type)
    }

    override fun isFinal(type: GoType): Boolean {
        return bridge.isFinal(type)
    }

    override fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean {
        return bridge.hasCommonSubtype(type, types)
    }

    override fun isSupertype(supertype: GoType, type: GoType): Boolean {
        return bridge.isSupertype(supertype, type)
    }
}