package org.usvm.types.single

import org.usvm.types.USingleTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE

object SingleTypeSystem : UTypeSystem<SingleTypeSystem.SingleType> {
    override val typeOperationsTimeout: Duration = INFINITE

    object SingleType

    override fun isSupertype(supertype: SingleType, type: SingleType): Boolean = true

    override fun hasCommonSubtype(type: SingleType, types: Collection<SingleType>): Boolean = types.isEmpty()

    override fun isFinal(type: SingleType): Boolean = true

    override fun isInstantiable(type: SingleType): Boolean = true

    override fun findSubtypes(type: SingleType): Sequence<SingleType> = emptySequence()

    override fun topTypeStream(): UTypeStream<SingleType> = USingleTypeStream(this, SingleType)
}
