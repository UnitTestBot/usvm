package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsNumberType
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.call.loadBundledEtsIrUnknownCallModelArtifact
import org.usvm.machine.state.TsState
import org.usvm.util.arrayStorageType

/** Built-in `Array.pop` implemented by an ordinary TypeScript body. */
internal object TsArrayPopEtsIrModel : TsBuiltInUnknownCallModel {
    override val id: String = "ts.array.pop"
    override val target = TsUnknownCallTarget(
        methodName = "pop",
        failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
    )

    private val model by lazy {
        TsEtsIrUnknownCallModel(
            id = id,
            target = target,
            artifact = loadBundledEtsIrUnknownCallModelArtifact(
                resourceName = "/org/usvm/machine/call/models/ArrayModels.ts",
                sourceFileName = "ArrayModels.ts",
                entryPointClassName = "ArrayModels",
                entryPointMethodName = "pop",
            ),
            domainGuard = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
                with(state.ctx) {
                    val receiver = inputs.singleOrNull()
                    if (receiver?.sort != addressSort || receiver.containsFakeObject()) {
                        falseExpr
                    } else {
                        val array = receiver.asExpr(addressSort)
                        val receiverType = state.arrayStorageType(array, call.receiver?.source?.type) as? EtsArrayType
                        if (receiverType?.dimensions != 1 || receiverType.elementType != EtsNumberType) {
                            falseExpr
                        } else {
                            state.memory.types.evalIsSubtype(array, receiverType)
                        }
                    }
                }
            },
        )
    }

    override val additionalSceneFiles get() = model.additionalSceneFiles

    override fun apply(state: TsState, call: TsUnknownCall) = model.apply(state, call)
}
