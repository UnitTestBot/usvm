package org.usvm.machine.interpreter

import org.jacodb.ets.model.EtsMethod
import org.usvm.UHeapRef

class TsFunction(
    val method: EtsMethod,
    val thisInstance: UHeapRef?,
)
