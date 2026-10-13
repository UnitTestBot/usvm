package org.usvm.machine.call.intrinsic

import io.ksmt.expr.KFp64Value
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.ets.model.EtsStringType
import org.usvm.UExpr
import org.usvm.api.evalTypeEquals
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelArtifact
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.call.loadBundledEtsIrUnknownCallModelArtifact
import org.usvm.machine.call.resolvedInstanceInputs
import org.usvm.machine.state.TsState
import org.usvm.sizeSort
import org.usvm.util.mkStringBackingElementLValue
import org.usvm.util.mkStringBackingLValue
import org.usvm.util.mkStringBackingLengthLValue
import org.usvm.util.stringFromCodeUnit

/** Built-in String algorithms implemented by ordinary TypeScript bodies. */
internal object TsStringEtsIrModelFamily : TsBuiltInUnknownCallModelFamily {
    private const val CLASS_NAME = "StringModels"
    private const val PRIMITIVES_CLASS_NAME = "StringModelPrimitives"
    private const val RESOURCE_NAME = "/org/usvm/machine/call/models/StringModels.ts"

    private val baseArtifact by lazy {
        loadBundledEtsIrUnknownCallModelArtifact(
            resourceName = RESOURCE_NAME,
            sourceFileName = "StringModels.ts",
            entryPointClassName = CLASS_NAME,
            entryPointMethodName = "charAt",
        )
    }

    private val sourceModelIds by lazy {
        models.filterIsInstance<TsEtsIrUnknownCallModel>().map { model -> model.id }.toSet()
    }

    private val primitiveClassSignature by lazy {
        baseArtifact.file.allClasses.single { it.name == PRIMITIVES_CLASS_NAME }.signature
    }

    private val optionalIndexAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs(maxArgumentCount = 1)
            ?: return@TsEtsIrUnknownCallModelInputAdapter null
        val index = inputs.getOrNull(1)

        when {
            index == null || index == state.ctx.mkUndefinedValue() -> inputs.take(1) + state.ctx.mkFp64(0.0)
            index.sort == state.ctx.fp64Sort -> inputs
            else -> null
        }
    }

    private val optionalPositionAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs(maxArgumentCount = 2)
            ?: return@TsEtsIrUnknownCallModelInputAdapter null
        val search = inputs.getOrNull(1)
        if (search?.sort != state.ctx.addressSort) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }

        val position = inputs.getOrNull(2)
        when {
            position == null || position == state.ctx.mkUndefinedValue() -> inputs.take(2) + state.ctx.mkFp64(0.0)
            position.sort == state.ctx.fp64Sort -> inputs
            else -> null
        }
    }

    private val optionalEndPositionAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs(maxArgumentCount = 2)
            ?: return@TsEtsIrUnknownCallModelInputAdapter null
        val search = inputs.getOrNull(1)
        if (search?.sort != state.ctx.addressSort) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }

        val position = inputs.getOrNull(2)
        when {
            position == null || position == state.ctx.mkUndefinedValue() -> {
                inputs.take(2) + state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort)
            }

            position.sort == state.ctx.fp64Sort -> inputs
            else -> null
        }
    }

    private val sliceAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs(maxArgumentCount = 2)
            ?: return@TsEtsIrUnknownCallModelInputAdapter null
        val receiver = inputs.firstOrNull()?.takeIf { it.sort == state.ctx.addressSort }
            ?: return@TsEtsIrUnknownCallModelInputAdapter null
        val arguments = inputs.drop(1)
        val hasUnsupportedArgument = arguments.any { value ->
            value != state.ctx.mkUndefinedValue() && value.sort != state.ctx.fp64Sort
        }
        if (hasUnsupportedArgument) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }

        fun numericOrDefault(index: Int, default: UExpr<*>): UExpr<*> {
            val value = arguments.getOrNull(index) ?: return default
            return if (value == state.ctx.mkUndefinedValue()) default else value
        }

        val start = numericOrDefault(index = 0, default = state.ctx.mkFp64(0.0))
        val end = numericOrDefault(
            index = 1,
            default = state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort),
        )

        listOf(receiver, start, end)
    }

    private val receiverOnlyAdapter = TsEtsIrUnknownCallModelInputAdapter { _, call ->
        call.resolvedInstanceInputs(maxArgumentCount = 0)
    }

    private fun stringDomain(state: TsState, input: UExpr<*>?): org.usvm.UBoolExpr = with(state.ctx) {
        if (input == null || input.sort != addressSort) {
            return falseExpr
        }

        val receiver = input.asExpr(addressSort)
        val hasBacking = receiver in state.boundedStringBackingRefs && !receiver.hasFakeValueBranch()
        if (!hasBacking) {
            return falseExpr
        }

        state.memory.types.evalTypeEquals(receiver, EtsStringType)
    }

    private val receiverDomain = TsEtsIrUnknownCallModelDomainGuard { state, _, inputs ->
        stringDomain(state = state, input = inputs.firstOrNull())
    }

    private val receiverAndSearchDomain = TsEtsIrUnknownCallModelDomainGuard { state, _, inputs ->
        val receiverGuard = stringDomain(state = state, input = inputs.getOrNull(0))
        val searchGuard = stringDomain(state = state, input = inputs.getOrNull(1))

        state.ctx.mkAnd(receiverGuard, searchGuard)
    }

    private val asciiReceiverDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        with(state.ctx) {
            val receiverGuard = receiverDomain.evaluate(state, call, inputs)
            if (receiverGuard == falseExpr) {
                return@TsEtsIrUnknownCallModelDomainGuard falseExpr
            }

            val receiver = inputs.first().asExpr(addressSort)
            val characters = state.memory.read(mkStringBackingLValue(receiver))
                .asExpr(addressSort)
            val length = state.memory.read(mkStringBackingLengthLValue(characters))
            val characterGuards = (0 until MAX_ASCII_CASE_LENGTH).map { index ->
                val symbolicIndex = mkBv(index)
                val codeUnit = state.memory.read(
                    mkStringBackingElementLValue(ref = characters, index = symbolicIndex)
                )
                val isOutsideLength = mkBvSignedGreaterOrEqualExpr(symbolicIndex, length)
                val asciiMax = mkBv(ASCII_MAX_CODE_UNIT, bv16Sort)
                val isAscii = mkBvUnsignedLessOrEqualExpr(codeUnit, asciiMax)
                mkOr(
                    isOutsideLength,
                    isAscii,
                )
            }

            val maximumLength = mkBv(MAX_ASCII_CASE_LENGTH)
            val withinLengthLimit = mkBvSignedLessOrEqualExpr(length, maximumLength)
            mkAnd(
                receiverGuard,
                withinLengthLimit,
                *characterGuards.toTypedArray(),
            )
        }
    }

    override val models: List<TsUnknownCallModel> by lazy {
        listOf(
            sourceModel(
                id = "ts.string.charAt",
                methodName = "charAt",
                inputAdapter = optionalIndexAdapter,
                domainGuard = receiverDomain,
            ),
            sourceModel(
                id = "ts.string.indexOf",
                methodName = "indexOf",
                inputAdapter = optionalPositionAdapter,
                domainGuard = receiverAndSearchDomain,
            ),
            sourceModel(
                id = "ts.string.includes",
                methodName = "includes",
                inputAdapter = optionalPositionAdapter,
                domainGuard = receiverAndSearchDomain,
            ),
            sourceModel(
                id = "ts.string.charCodeAt",
                methodName = "charCodeAt",
                inputAdapter = optionalIndexAdapter,
                domainGuard = receiverDomain,
            ),
            sourceModel(
                id = "ts.string.startsWith",
                methodName = "startsWith",
                inputAdapter = optionalPositionAdapter,
                domainGuard = receiverAndSearchDomain,
            ),
            sourceModel(
                id = "ts.string.endsWith",
                methodName = "endsWith",
                inputAdapter = optionalEndPositionAdapter,
                domainGuard = receiverAndSearchDomain,
            ),
            sourceModel(
                id = "ts.string.lastIndexOf",
                methodName = "lastIndexOf",
                inputAdapter = optionalEndPositionAdapter,
                domainGuard = receiverAndSearchDomain,
            ),
            sourceModel(
                id = "ts.string.slice",
                methodName = "slice",
                inputAdapter = sliceAdapter,
                domainGuard = receiverDomain,
            ),
            sourceModel(
                id = "ts.string.substring",
                methodName = "substring",
                inputAdapter = sliceAdapter,
                domainGuard = receiverDomain,
            ),
            sourceModel(
                id = "ts.string.trim",
                methodName = "trim",
                inputAdapter = receiverOnlyAdapter,
                domainGuard = receiverDomain,
            ),
            sourceModel(
                id = "ts.string.trimStart",
                methodName = "trimStart",
                inputAdapter = receiverOnlyAdapter,
                domainGuard = receiverDomain,
            ),
            sourceModel(
                id = "ts.string.trimEnd",
                methodName = "trimEnd",
                inputAdapter = receiverOnlyAdapter,
                domainGuard = receiverDomain,
            ),
            sourceModel(
                id = "ts.string.toUpperCase",
                methodName = "toUpperCase",
                inputAdapter = receiverOnlyAdapter,
                domainGuard = asciiReceiverDomain,
            ),
            sourceModel(
                id = "ts.string.toLowerCase",
                methodName = "toLowerCase",
                inputAdapter = receiverOnlyAdapter,
                domainGuard = asciiReceiverDomain,
            ),
            primitiveModel(
                methodName = "codeUnitAt",
                arity = 2,
                implementation = ::stringCodeUnitAt,
            ),
            primitiveModel(
                methodName = "fromCodeUnit",
                arity = 1,
                implementation = ::stringFromCodeUnit,
            ),
        )
    }

    private fun sourceModel(
        id: String,
        methodName: String,
        inputAdapter: TsEtsIrUnknownCallModelInputAdapter,
        domainGuard: TsEtsIrUnknownCallModelDomainGuard,
    ): TsUnknownCallModel = TsEtsIrUnknownCallModel(
        id = id,
        target = TsUnknownCallTarget(
            methodName = methodName,
            enclosingClassName = "String",
            failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
        ),
        artifact = artifact(methodName),
        domainGuard = domainGuard,
        inputAdapter = inputAdapter,
        requiredModelIds = buildSet {
            val convertsCase = methodName in setOf("toUpperCase", "toLowerCase")
            if (convertsCase) {
                add(PRIMITIVE_CODE_UNIT_AT_ID)
                add(PRIMITIVE_FROM_CODE_UNIT_ID)
            } else {
                add(MATH_FLOOR_MODEL_ID)
                if (methodName == "charCodeAt") {
                    add(PRIMITIVE_CODE_UNIT_AT_ID)
                }
            }
            if (methodName in setOf("trim", "trimStart", "trimEnd")) {
                add(PRIMITIVE_CODE_UNIT_AT_ID)
            }
        },
    )

    private fun artifact(methodName: String): TsEtsIrUnknownCallModelArtifact {
        val artifact = baseArtifact
        val entryPoint = artifact.file.allClasses
            .single { it.name == CLASS_NAME }
            .methods
            .single { it.name == methodName }

        return artifact.copy(entryPoint = entryPoint)
    }

    private fun primitiveModel(
        methodName: String,
        arity: Int,
        implementation: (TsState, List<UExpr<*>>) -> TsUnknownCallModelExecution?,
    ): TsUnknownCallModel = TsPrimitiveUnknownCallModel(
        idPrefix = "ts.string.primitive",
        methodName = methodName,
        enclosingClassName = PRIMITIVES_CLASS_NAME,
        arity = arity,
        helperClassSignature = { primitiveClassSignature },
        sourceClassSignature = { baseArtifact.entryPoint.signature.enclosingClass },
        owningModelIds = { sourceModelIds },
        implementation = implementation,
    )

    private fun stringCodeUnitAt(
        state: TsState,
        inputs: List<UExpr<*>>,
    ): TsUnknownCallModelExecution? = with(state.ctx) {
        val receiver = inputs.getOrNull(0)?.takeIf { it.sort == addressSort }?.asExpr(addressSort) ?: return null
        val fpIndex = inputs.getOrNull(1)?.takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort) ?: return null
        if (receiver.hasFakeValueBranch()) {
            return null
        }

        val index = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = fpIndex,
            bvSize = sizeSort.sizeBits.toInt(),
            isSigned = true,
        ).asExpr(sizeSort)
        val characters = state.memory.read(mkStringBackingLValue(receiver))
        val codeUnit = state.memory.read(
            mkStringBackingElementLValue(ref = characters, index = index)
        )
        val receiverIsString = state.memory.types.evalTypeEquals(receiver, EtsStringType)
        val result = mkBvToFpExpr(
            sort = fp64Sort,
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = codeUnit.cast(),
            signed = false,
        )

        singleSuccessor(
            state = state,
            guard = receiverIsString,
            result = result,
        )
    }

    private fun stringFromCodeUnit(
        state: TsState,
        inputs: List<UExpr<*>>,
    ): TsUnknownCallModelExecution? = with(state.ctx) {
        val code = inputs.singleOrNull()?.takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort) ?: return null
        val successor = TsUnknownCallModelSuccessor(
            guard = trueExpr,
            completion = TsUnknownCallModelCompletion.Normal {
                if (code is KFp64Value) {
                    mkInitializedStringConstant(code.value.toInt().toChar().toString())
                } else {
                    stringFromCodeUnit(code)
                }
            },
        )

        TsUnknownCallModelExecution(
            successors = listOf(successor),
        )
    }

    private fun singleSuccessor(
        state: TsState,
        guard: org.usvm.UBoolExpr,
        result: UExpr<*>,
    ): TsUnknownCallModelExecution {
        val successor = TsUnknownCallModelSuccessor(
            guard = guard,
            completion = TsUnknownCallModelCompletion.Normal { result },
        )

        return TsUnknownCallModelExecution(
            successors = listOf(successor),
            residualGuard = guard.takeUnless { it == state.ctx.trueExpr }?.let(state.ctx::mkNot),
        )
    }

    private const val PRIMITIVE_CODE_UNIT_AT_ID = "ts.string.primitive.codeUnitAt"
    private const val PRIMITIVE_FROM_CODE_UNIT_ID = "ts.string.primitive.fromCodeUnit"
    private const val MATH_FLOOR_MODEL_ID = "ts.math.floor"
    private const val MAX_ASCII_CASE_LENGTH = 16
    private const val ASCII_MAX_CODE_UNIT = 0x7f
}
