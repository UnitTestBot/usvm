package org.usvm.machine.expr

import org.jacodb.ets.model.EtsType
import org.usvm.UHeapRef

/** A concrete name observed on an input reference; immutable metadata is shared safely by state clones. */
data class TrackedObjectProperty(
    val instance: UHeapRef,
    val name: String,
    val declaredType: EtsType?,
    val optional: Boolean = false,
)
