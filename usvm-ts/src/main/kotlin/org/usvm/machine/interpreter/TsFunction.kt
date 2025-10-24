package org.usvm.machine.interpreter

import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.UHeapRef

class TsFunction(
    val method: EtsMethodSignature,
    val thisInstance: UHeapRef?,
)
