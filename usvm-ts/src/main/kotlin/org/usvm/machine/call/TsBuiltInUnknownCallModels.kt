package org.usvm.machine.call

import org.usvm.machine.call.intrinsic.TsArrayShiftIntrinsicModel

/** The intentionally small built-in semantic-model catalog. */
object TsBuiltInUnknownCallModels {
    const val ARRAY_SHIFT_MODEL_ID: String = TsArrayShiftIntrinsicModel.MODEL_ID

    fun catalog(enabledModelIds: Set<String>? = null) = TsUnknownCallModelCatalog(
        models = listOf(TsArrayShiftIntrinsicModel),
        enabledModelIds = enabledModelIds,
    )
}
