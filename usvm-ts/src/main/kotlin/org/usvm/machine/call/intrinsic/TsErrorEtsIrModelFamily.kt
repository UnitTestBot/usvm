package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldSignature
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
import org.usvm.machine.expr.builtInErrorSignature
import org.usvm.machine.expr.builtInTypeErrorSignature
import org.usvm.types.single

/** Built-in Error and TypeError constructors with string messages. */
internal object TsErrorEtsIrModelFamily : TsBuiltInUnknownCallModelFamily {
    const val CONSTRUCTOR_ID: String = "ts.error.constructor"

    private const val RESOURCE = "/org/usvm/machine/call/models/ErrorModels.ts"

    private val artifact by lazy {
        loadBundledEtsIrUnknownCallModelArtifact(
            resourceName = RESOURCE,
            sourceFileName = "ErrorModels.ts",
            entryPointClassName = "ErrorModels",
            entryPointMethodName = "construct",
        )
    }

    internal fun ownsStorageDefinitionField(field: EtsFieldSignature): Boolean =
        field.enclosingClass.name == "ErrorValue" && field.enclosingClass.file == artifact.file.signature

    override val models: List<TsUnknownCallModel> by lazy {
        listOf(
            constructorModel(builtInErrorSignature, modelId = CONSTRUCTOR_ID, methodName = "construct"),
            constructorModel(
                builtInTypeErrorSignature,
                modelId = "ts.typeError.constructor",
                methodName = "constructTypeError",
            ),
        )
    }

    private fun constructorModel(
        builtinSignature: EtsClassSignature,
        modelId: String,
        methodName: String,
    ): TsUnknownCallModel {
        val target = TsUnknownCallTarget(
            methodName = CONSTRUCTOR_NAME,
            enclosingClassName = builtinSignature.name,
            failureReason = TsUnknownCallFailureReason.RECEIVER_CLASS_NOT_FOUND,
        )

        val entryPoint = artifact.file.allClasses.single { it.name == "ErrorModels" }
            .methods.single { it.name == methodName }

        return TsEtsIrUnknownCallModel(
            id = modelId,
            target = target,
            artifact = artifact.copy(entryPoint = entryPoint),
            domainGuard = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
                with(state.ctx) {
                    val receiver = inputs.getOrNull(0) as? UConcreteHeapRef
                        ?: return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    val messageValue = inputs.getOrNull(1)
                        ?: return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    if (messageValue.sort != addressSort) return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    val message = messageValue.asExpr(addressSort)
                    val builtinErrorType = EtsClassType(signature = builtinSignature)
                    val receiverRuntimeType = state.memory.typeStreamOf(receiver).single()

                    if (call.callee.enclosingClass != builtinSignature) {
                        return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    }
                    if (receiver.hasFakeValueBranch() || receiverRuntimeType != builtinErrorType) {
                        return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    }
                    val knownFunction = (message as? UConcreteHeapRef)?.let { ref ->
                        state.associatedFunction.containsKey(ref)
                    } == true
                    if (message.hasFakeValueBranch() || knownFunction) {
                        return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    }

                    val originalMessage = call.arguments.single().resolved
                        ?: return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    val referenceKindGuard = if (originalMessage.isFakeObject()) {
                        originalMessage.getFakeType(state.memory).refTypeExpr
                    } else {
                        trueExpr
                    }
                    mkAnd(referenceKindGuard, state.memory.types.evalTypeEquals(message, EtsStringType))
                }
            },
            inputAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
                val receiver = call.receiver?.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null
                val message = call.arguments.singleOrNull()?.resolved
                    ?: return@TsEtsIrUnknownCallModelInputAdapter null

                with(state.ctx) {
                    if (message.sort != addressSort) return@TsEtsIrUnknownCallModelInputAdapter null
                    val payload = if (message.isFakeObject()) message.extractRef(state.memory) else message
                    listOf(receiver, payload)
                }
            },
        )
    }
}
