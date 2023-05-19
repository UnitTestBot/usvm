package org.usvm.interpreter

import org.usvm.types.UTypeSystem
import org.usvm.language.SampleType

class SampleTypeSystem : UTypeSystem<SampleType> {
    override fun isSupertype(u: SampleType, t: SampleType): Boolean =
        u == t

    override fun isFinal(t: SampleType): Boolean = true

    override fun isMultipleInheritanceAllowedFor(t: SampleType): Boolean = false
}
