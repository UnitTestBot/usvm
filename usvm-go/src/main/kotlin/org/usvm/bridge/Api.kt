package org.usvm.bridge

import com.sun.jna.Callback
import com.sun.jna.Structure

@Suppress("unused")
@Structure.FieldOrder(
    "mkIntRegisterReading",
    "mkLess",
    "mkGreater",
    "mkAdd",
    "mkIf",
    "mkReturn",
)
open class Api(
    @JvmField var mkIntRegisterReading: Callback = DiscardCallback(),
    @JvmField var mkLess: Callback = DiscardCallback(),
    @JvmField var mkGreater: Callback = DiscardCallback(),
    @JvmField var mkAdd: Callback = DiscardCallback(),
    @JvmField var mkIf: Callback = DiscardCallback(),
    @JvmField var mkReturn: Callback = DiscardCallback(),
) : Structure(), Structure.ByValue

class DiscardCallback : Callback
