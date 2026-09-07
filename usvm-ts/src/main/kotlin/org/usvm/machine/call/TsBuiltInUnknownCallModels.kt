package org.usvm.machine.call

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsNumberType
import org.usvm.machine.call.intrinsic.TsArrayShiftIntrinsicModel

/** The intentionally small built-in semantic-model catalog. */
object TsBuiltInUnknownCallModels {
    const val ARRAY_SHIFT_MODEL_ID: String = TsArrayShiftIntrinsicModel.MODEL_ID
    const val ARRAY_POP_MODEL_ID: String = "ts.array.pop"

    private val arrayPopModel: TsUnknownCallModel by lazy {
        val artifact = loadBundledEtsIrUnknownCallModelArtifact(
            resourceName = "/org/usvm/machine/call/models/ArrayModels.ts",
            sourceFileName = "ArrayModels.ts",
            entryPointClassName = "ArrayModels",
            entryPointMethodName = "pop",
        )

        TsEtsIrUnknownCallModel(
            id = ARRAY_POP_MODEL_ID,
            target = TsUnknownCallTarget(
                methodName = "pop",
                failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
            ),
            artifact = artifact,
            domainGuard = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
                with(state.ctx) {
                    val receiver = inputs.singleOrNull()
                    val receiverType = call.receiver?.source?.type as? EtsArrayType
                    val isNumberArray = receiverType?.dimensions == 1 && receiverType.elementType == EtsNumberType
                    if (receiver?.sort != addressSort || receiver.containsFakeObject() || !isNumberArray) {
                        falseExpr
                    } else {
                        state.memory.types.evalIsSubtype(
                            receiver.asExpr(addressSort),
                            EtsArrayType(EtsNumberType, dimensions = 1),
                        )
                    }
                }
            },
        )
    }

    fun catalog(enabledModelIds: Set<String>? = null): TsUnknownCallModelCatalog {
        val models = buildList {
            if (enabledModelIds == null || ARRAY_SHIFT_MODEL_ID in enabledModelIds) {
                add(TsArrayShiftIntrinsicModel)
            }
            if (enabledModelIds == null || ARRAY_POP_MODEL_ID in enabledModelIds) {
                add(arrayPopModel)
            }
        }

        return TsUnknownCallModelCatalog(models, enabledModelIds)
    }
}
