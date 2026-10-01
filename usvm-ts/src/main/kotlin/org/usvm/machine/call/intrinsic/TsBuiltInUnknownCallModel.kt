package org.usvm.machine.call.intrinsic

import org.usvm.machine.call.TsUnknownCallModel

/** Implement as an object in this package; the sealed hierarchy registers every built-in automatically. */
internal sealed interface TsBuiltInUnknownCallModel : TsUnknownCallModel

/** Supplies a parameterized family of built-in semantic models from one object. */
internal sealed interface TsBuiltInUnknownCallModelFamily {
    val models: List<TsUnknownCallModel>
}
