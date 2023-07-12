package org.usvm.machine

import org.usvm.language.SampleType
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.types.emptyTypeStream

class SampleTypeSystem : UTypeSystem<SampleType> {
    override fun isSupertype(u: SampleType, t: SampleType): Boolean =
        u == t

    override fun isFinal(t: SampleType): Boolean = true

    override fun isMultipleInheritanceAllowedFor(t: SampleType): Boolean = false
    override fun isInstantiable(t: SampleType): Boolean = true

    override fun findSubtypes(t: SampleType): Sequence<SampleType> = emptySequence()

    override fun topTypeStream(): UTypeStream<SampleType> = emptyTypeStream

    companion object {
        private val emptyTypeStream: UTypeStream<SampleType> = emptyTypeStream()
    }
}
