package org.usvm.bridge

import com.sun.jna.Callback
import com.sun.jna.Structure

@Suppress("unused")
@Structure.FieldOrder(
    "mkIntRegisterReading",
    "mkIntSignedLessExpr",
    "mkIntSignedGreaterExpr",
    "mkIfInst",
    "mkReturnInst",
)
open class Api(
    @JvmField var mkIntRegisterReading: Callback = DiscardCallback(),
    @JvmField var mkIntSignedLessExpr: Callback = DiscardCallback(),
    @JvmField var mkIntSignedGreaterExpr: Callback = DiscardCallback(),
    @JvmField var mkIfInst: Callback = DiscardCallback(),
    @JvmField var mkReturnInst: Callback = DiscardCallback(),
) : Structure(), Structure.ByValue

class DiscardCallback : Callback
