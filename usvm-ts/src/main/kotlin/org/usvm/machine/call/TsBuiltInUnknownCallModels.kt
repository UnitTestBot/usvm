package org.usvm.machine.call

import org.usvm.machine.call.intrinsic.TsArrayPopIntrinsicModel
import org.usvm.machine.call.intrinsic.TsIntrinsicUnknownCallModelBackend

/** The intentionally small built-in catalog enabled by default for profile-based unknown-call dispatch. */
object TsBuiltInUnknownCallModels {
    const val ARRAY_POP_MODEL_ID: String = TsArrayPopIntrinsicModel.MODEL_ID

    val registry = TsUnknownCallModelRegistry(
        registrations = listOf(TsArrayPopIntrinsicModel.registration),
        backends = listOf(TsIntrinsicUnknownCallModelBackend),
    )
}
