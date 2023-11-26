package org.usvm.machine

import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class GoTypeSystem(
    override val typeOperationsTimeout: Duration
) : UTypeSystem<GoType> {
    override fun topTypeStream(): UTypeStream<GoType> {
        TODO("Not yet implemented")
    }

    override fun findSubtypes(type: GoType): Sequence<GoType> {
        TODO("Not yet implemented")
    }

    override fun isInstantiable(type: GoType): Boolean {
        TODO("Not yet implemented")
    }

    override fun isFinal(type: GoType): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasCommonSubtype(type: GoType, types: Collection<GoType>): Boolean {
        TODO("Not yet implemented")
    }

    override fun isSupertype(supertype: GoType, type: GoType): Boolean {
        TODO("Not yet implemented")
    }
}