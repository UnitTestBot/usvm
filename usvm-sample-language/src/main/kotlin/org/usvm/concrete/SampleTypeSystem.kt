package org.usvm.concrete

import org.usvm.UTypeSystem
import org.usvm.language.SampleType

class SampleTypeSystem : UTypeSystem<SampleType> {
    override fun isSupertype(u: SampleType, t: SampleType): Boolean =
        u == t

}
