package org.usvm.interpreter

import org.usvm.language.SampleType
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

class SampleTypeSystem : UTypeSystem<SampleType> {
    override fun isSupertype(u: SampleType, t: SampleType): Boolean =
        u == t

    override fun isFinal(t: SampleType): Boolean = true

    override fun isMultipleInheritanceAllowedFor(t: SampleType): Boolean = false

    override fun isInstantiable(t: SampleType): Boolean {
        TODO("Not yet implemented")
    }

    override fun findSubtypes(t: SampleType): Sequence<SampleType> {
        TODO("Not yet implemented")
    }

    override fun topTypeStream(): UTypeStream<SampleType> {
        TODO("Not yet implemented")
    }
}
