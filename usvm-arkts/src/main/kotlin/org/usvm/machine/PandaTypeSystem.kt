package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaClassTypeImpl
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class PandaTypeSystem(override val typeOperationsTimeout: Duration) : UTypeSystem<PandaType> {
    override fun isSupertype(supertype: PandaType, type: PandaType): Boolean = true

    override fun hasCommonSubtype(type: PandaType, types: Collection<PandaType>): Boolean = true

    override fun isFinal(type: PandaType): Boolean = true

    override fun isInstantiable(type: PandaType): Boolean = true

    override fun findSubtypes(type: PandaType): Sequence<PandaType> =
        sequenceOf()

    override fun topTypeStream(): UTypeStream<PandaType> = USupportTypeStream.from(
        this,
        PandaClassTypeImpl("GLOBAL")
    )
}