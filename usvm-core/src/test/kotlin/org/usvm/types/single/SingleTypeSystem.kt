package org.usvm.types.single

import org.usvm.types.USingleTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

object SingleTypeSystem : UTypeSystem<SingleTypeSystem.SingleType> {
    object SingleType

    override fun isSupertype(u: SingleType, t: SingleType): Boolean = true

    override fun isMultipleInheritanceAllowedFor(t: SingleType): Boolean = false

    override fun isFinal(t: SingleType): Boolean = true

    override fun isInstantiable(t: SingleType): Boolean = true

    override fun findSubtypes(t: SingleType): Sequence<SingleType> = emptySequence()

    override fun topTypeStream(): UTypeStream<SingleType> = USingleTypeStream(this, SingleType)
}
