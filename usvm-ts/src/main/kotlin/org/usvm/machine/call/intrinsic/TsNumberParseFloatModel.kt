package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
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
import org.usvm.util.mkStringBackingLValue
import org.usvm.util.mkStringBackingLengthLValue

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
                    val argument = inputs.single()
                    if (argument.sort != addressSort) return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    val input = argument.asExpr(addressSort)
                    val isStringConstant = input is UConcreteHeapRef && getStringConstantValue(input) != null
                    val hasBacking = input in state.boundedStringBackingRefs || isStringConstant
                    if (input.hasFakeValueBranch() || !hasBacking) {
                        return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                    }

                    val characters = state.memory.read(mkStringBackingLValue(input))
                    val length = state.memory.read(mkStringBackingLengthLValue(characters))
                    val isString = state.memory.types.evalTypeEquals(input, EtsStringType)
                    val hasNonNegativeLength = mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
                    val isWithinLengthLimit = mkBvSignedLessOrEqualExpr(length, mkBv(MAX_INPUT_LENGTH))
                    val isBounded = mkAnd(hasNonNegativeLength, isWithinLengthLimit)
                    mkAnd(isString, isBounded)
                }
            },
            requiredModelIds = setOf("ts.string.charCodeAt"),
        )
        listOf(model)
    }
}
