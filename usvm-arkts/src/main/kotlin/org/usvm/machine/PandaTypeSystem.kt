package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class PandaTypeSystem(override val typeOperationsTimeout: Duration) : UTypeSystem<PandaType> {
    override fun isSupertype(supertype: PandaType, type: PandaType): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasCommonSubtype(type: PandaType, types: Collection<PandaType>): Boolean {
        TODO("Not yet implemented")
    }

    override fun isFinal(type: PandaType): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInstantiable(type: PandaType): Boolean {
        TODO("Not yet implemented")
    }

    override fun findSubtypes(type: PandaType): Sequence<PandaType> {
        TODO("Not yet implemented")
    }

    override fun topTypeStream(): UTypeStream<PandaType> {
        TODO("Not yet implemented")
    }
}