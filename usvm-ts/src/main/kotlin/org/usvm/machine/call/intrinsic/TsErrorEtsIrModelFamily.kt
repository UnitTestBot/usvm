package org.usvm.machine.call.intrinsic

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.UConcreteHeapRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.typeStreamOf
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.call.loadBundledEtsIrUnknownCallModelArtifact
import org.usvm.types.single

/** Narrow built-in `Error(message: string)` constructor model. */
internal object TsErrorEtsIrModelFamily : TsBuiltInUnknownCallModelFamily {
    const val CONSTRUCTOR_ID: String = "ts.error.constructor"

    private const val ERROR_CLASS = "Error"
    private const val RESOURCE = "/org/usvm/machine/call/models/ErrorModels.ts"

    private val artifact by lazy {
        loadBundledEtsIrUnknownCallModelArtifact(
            resourceName = RESOURCE,
            sourceFileName = "ErrorModels.ts",
            entryPointClassName = "ErrorModels",
            entryPointMethodName = "construct",
        )
    }

    override val models: List<TsUnknownCallModel> by lazy {
        listOf(constructorModel())
    }

    private fun constructorModel(): TsUnknownCallModel {
        val target = TsUnknownCallTarget(
            methodName = CONSTRUCTOR_NAME,
            enclosingClassName = ERROR_CLASS,
            failureReason = TsUnknownCallFailureReason.RECEIVER_CLASS_NOT_FOUND,
        )

        return TsEtsIrUnknownCallModel(
            id = CONSTRUCTOR_ID,
            target = target,
            artifact = artifact,
            domainGuard = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
                with(state.ctx) {
                    val receiver = inputs.getOrNull(0) as? UConcreteHeapRef
                        ?: return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    val message = inputs.getOrNull(1) as? UConcreteHeapRef
                        ?: return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    val builtinSignature = EtsClassSignature.UNKNOWN.copy(name = ERROR_CLASS)
                    val builtinErrorType = EtsClassType(signature = builtinSignature)
                    val receiverRuntimeType = state.memory.typeStreamOf(receiver).single()

                    if (call.callee.enclosingClass != builtinSignature) {
                        return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    }
                    if (receiver.hasFakeValueBranch() || receiverRuntimeType != builtinErrorType) {
                        return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    }
                    if (message.hasFakeValueBranch()) {
                        return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    }

                    state.memory.types.evalTypeEquals(message, EtsStringType)
                }
            },
            inputAdapter = TsEtsIrUnknownCallModelInputAdapter { _, call ->
                val receiver = call.receiver?.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null
                val message = call.arguments.singleOrNull()?.resolved
                    ?: return@TsEtsIrUnknownCallModelInputAdapter null

                listOf(receiver, message)
            },
        )
    }
}
