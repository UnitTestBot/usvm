package org.usvm.machine

import org.usvm.language.SampleType
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.types.emptyTypeStream
import kotlin.time.Duration

class SampleTypeSystem(override val typeOperationsTimeout: Duration) : UTypeSystem<SampleType> {
    override fun isSupertype(supertype: SampleType, type: SampleType): Boolean =
        supertype == type

    override fun isFinal(type: SampleType): Boolean = true

    override fun hasCommonSubtype(type: SampleType, types: Collection<SampleType>): Boolean = types.isEmpty()

    override fun isInstantiable(type: SampleType): Boolean = true

    override fun findSubtypes(type: SampleType): Sequence<SampleType> = emptySequence()

    override fun topTypeStream(): UTypeStream<SampleType> = emptyTypeStream

    companion object {
        private val emptyTypeStream: UTypeStream<SampleType> = emptyTypeStream()
    }
}
