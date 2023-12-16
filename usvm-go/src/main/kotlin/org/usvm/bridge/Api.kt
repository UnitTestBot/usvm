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
    "mkVariable",
    "getLastBlock",
    "setLastBlock",
)
open class Api(
    @JvmField var mkIntRegisterReading: Callback = DiscardCallback(),
    @JvmField var mkLess: Callback = DiscardCallback(),
    @JvmField var mkGreater: Callback = DiscardCallback(),
    @JvmField var mkAdd: Callback = DiscardCallback(),
    @JvmField var mkIf: Callback = DiscardCallback(),
    @JvmField var mkReturn: Callback = DiscardCallback(),
    @JvmField var mkVariable: Callback = DiscardCallback(),
    @JvmField var getLastBlock: Callback = DiscardCallback(),
    @JvmField var setLastBlock: Callback = DiscardCallback(),
) : Structure(), Structure.ByValue

class DiscardCallback : Callback
