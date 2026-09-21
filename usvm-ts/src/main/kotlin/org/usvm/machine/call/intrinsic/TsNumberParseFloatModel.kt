package org.usvm.machine.call.intrinsic

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.usvm.UConcreteHeapRef
import org.usvm.api.evalTypeEquals
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.call.hasBuiltinGlobalOwner
import org.usvm.machine.call.loadBundledEtsIrUnknownCallModelArtifact
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue

/** Decimal prefix parsing in source, with exact binary64 arithmetic on a guarded finite domain. */
internal object TsNumberParseFloatModel : TsBuiltInUnknownCallModelFamily {
    private const val MAX_INPUT_LENGTH = 32

    override val models by lazy {
        val model = TsEtsIrUnknownCallModel(
            id = "ts.number.parseFloat",
            target = TsUnknownCallTarget(
                methodName = "parseFloat",
                failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
            ),
            artifact = loadBundledEtsIrUnknownCallModelArtifact(
                resourceName = "/org/usvm/machine/call/models/NumberParseFloatModels.ts",
                sourceFileName = "NumberParseFloatModels.ts",
                entryPointClassName = "NumberParseFloatModels",
                entryPointMethodName = "parseFloat",
            ),
            inputAdapter = TsEtsIrUnknownCallModelInputAdapter { _, call ->
                val owner = call.receiver?.source
                if (owner == null || !hasBuiltinGlobalOwner(owner, call.callee, expectedName = "Number")) {
                    null
                } else {
                    call.arguments.firstOrNull()?.resolved?.let(::listOf)
                }
            },
            domainGuard = TsEtsIrUnknownCallModelDomainGuard { state, _, inputs ->
                with(state.ctx) {
                    val input = inputs.single() as? UConcreteHeapRef
                        ?: return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    if (input.hasFakeValueBranch() || input in state.associatedFunction) {
                        return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    }
                    val characters = state.memory.read(mkFieldLValue(addressSort, input, "value"))
                    val lengthLocation = mkArrayLengthLValue(
                        characters,
                        EtsArrayType(EtsNumberType, dimensions = 1),
                    )
                    val length = state.memory.read(lengthLocation)
                    val isString = state.memory.types.evalTypeEquals(input, EtsStringType)
                    val isBounded = mkBvSignedLessOrEqualExpr(length, mkBv(MAX_INPUT_LENGTH))
                    mkAnd(isString, isBounded)
                }
            },
            requiredModelIds = setOf("ts.string.primitive.length", "ts.string.primitive.codeUnitAt"),
        )
        listOf(model)
    }
}
