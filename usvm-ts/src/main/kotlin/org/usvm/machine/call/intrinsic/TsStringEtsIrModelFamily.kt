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
import org.usvm.machine.expr.mkEcmaScriptToUint32
import org.usvm.machine.expr.mkEcmaScriptWhitespace
import org.usvm.machine.state.TsState
import org.usvm.sizeSort
import org.usvm.util.copyStringRange
import org.usvm.util.mkStringBackingElementLValue
import org.usvm.util.mkStringBackingLValue
import org.usvm.util.mkStringBackingLengthLValue
import org.usvm.util.stringFromCodeUnit
import org.usvm.util.stringLength

/** Built-in String algorithms implemented by ordinary TypeScript bodies. */
internal object TsStringEtsIrModelFamily : TsBuiltInUnknownCallModelFamily {
    private const val CLASS_NAME = "StringModels"
    private const val PRIMITIVES_CLASS_NAME = "StringModelPrimitives"
    private const val RESOURCE_NAME = "/org/usvm/machine/call/models/StringModels.ts"
    private const val REPLACE_ALL_INPUT_COUNT = 3
    private const val DEFAULT_SPLIT_LIMIT = 4_294_967_295.0
    private const val SPLIT_OMITTED_SEPARATOR_INPUT_INDEX = 3

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

    private val replaceAllAdapter = TsEtsIrUnknownCallModelInputAdapter { _, call ->
        if (call.arguments.size >= 2) call.resolvedInstanceInputs(maxArgumentCount = 2) else null
    }

    private val replaceAllDomain = TsEtsIrUnknownCallModelDomainGuard { state, _, inputs ->
        if (inputs.size != REPLACE_ALL_INPUT_COUNT) {
            state.ctx.falseExpr
        } else {
            val guards = inputs.map { value -> stringDomain(state = state, input = value) }
            state.ctx.mkAnd(guards)
        }
    }

    private val splitAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        with(state.ctx) {
            val inputs = call.resolvedInstanceInputs(maxArgumentCount = 2)
                ?: return@TsEtsIrUnknownCallModelInputAdapter null
            val separator = inputs.getOrNull(1)
            val omitted = separator == null || separator == mkUndefinedValue()
            val limit = inputs.getOrNull(2)
            val numericLimit = limit?.takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort)
            val convertedLimit = when {
                limit == null || limit == mkUndefinedValue() -> mkFp64(DEFAULT_SPLIT_LIMIT)
                numericLimit != null -> mkBvToFpExpr(
                    sort = fp64Sort,
                    roundingMode = fpRoundingModeSortDefaultValue(),
                    value = mkEcmaScriptToUint32(numericLimit),
                    signed = false,
                )
                else -> return@TsEtsIrUnknownCallModelInputAdapter null
            }
            val search = if (omitted) state.mkInitializedStringConstant("") else requireNotNull(separator)
            listOf(inputs.first(), search, convertedLimit, mkBool(omitted))
        }
    }

    private val splitDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        with(state.ctx) {
            val stringsGuard = receiverAndSearchDomain.evaluate(state, call, inputs)
            if (stringsGuard == falseExpr) return@TsEtsIrUnknownCallModelDomainGuard falseExpr

            val receiver = inputs.first().asExpr(addressSort)
            val characters = state.memory.read(mkStringBackingLValue(receiver))
            val length = state.memory.read(mkStringBackingLengthLValue(characters))
            val boundedResult = mkBvSignedLessOrEqualExpr(length, mkBv(SOURCE_ARRAY_MODEL_CAPACITY - 1))
            val zeroLimit = mkFpEqualExpr(inputs[2].asExpr(fp64Sort), mkFp64(0.0))
            val omittedSeparator = inputs[SPLIT_OMITTED_SEPARATOR_INPUT_INDEX].asExpr(boolSort)
            mkAnd(stringsGuard, mkOr(boundedResult, zeroLimit, omittedSeparator))
        }
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
                id = "ts.string.split",
                methodName = "split",
                inputAdapter = splitAdapter,
                domainGuard = splitDomain,
            ),
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
                id = "ts.string.replaceAll",
                methodName = "replaceAll",
                inputAdapter = replaceAllAdapter,
                domainGuard = replaceAllDomain,
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
            arrayPrimitiveModel(
                methodName = "allocateStrings",
                arity = 1,
                implementation = TsDenseArrayPrimitives::allocateStrings,
            ),
            arrayPrimitiveModel(
                methodName = "truncateDense",
                arity = 2,
                implementation = TsDenseArrayPrimitives::truncateDense,
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
            primitiveModel(
                methodName = "isWhitespace",
                arity = 1,
                implementation = ::stringIsWhitespace,
            ),
            primitiveModel(
                methodName = "copyNormalizedRange",
                arity = 3,
                implementation = ::stringCopyNormalizedRange,
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
            if (methodName == "split") {
                add("ts.array.primitive.allocateStrings")
                add("ts.array.primitive.truncateDense")
            }
            if (methodName != "charAt" && methodName != "slice" && methodName != "substring") {
                add(PRIMITIVE_CODE_UNIT_AT_ID)
            }
            if (methodName in setOf("toUpperCase", "toLowerCase", "replaceAll")) {
                add(PRIMITIVE_FROM_CODE_UNIT_ID)
            }
            if (methodName in setOf("trim", "trimStart", "trimEnd")) {
                add(PRIMITIVE_IS_WHITESPACE_ID)
            }
            if (methodName in setOf("slice", "substring", "split", "trim", "trimStart", "trimEnd", "replaceAll")) {
                add(PRIMITIVE_COPY_NORMALIZED_RANGE_ID)
            }
            val normalizesPosition = methodName in setOf(
                "charAt", "charCodeAt", "indexOf", "includes", "slice", "substring",
                "startsWith", "endsWith", "lastIndexOf",
            )
            if (normalizesPosition) {
                add(MATH_FLOOR_MODEL_ID)
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

    private val arrayPrimitiveClassSignature by lazy {
        baseArtifact.file.allClasses.single { it.name == "ArrayModelPrimitives" }.signature
    }

    private fun arrayPrimitiveModel(
        methodName: String,
        arity: Int,
        implementation: (TsState, List<UExpr<*>>) -> TsUnknownCallModelExecution?,
    ): TsUnknownCallModel = TsPrimitiveUnknownCallModel(
        idPrefix = "ts.array.primitive",
        methodName = methodName,
        enclosingClassName = "ArrayModelPrimitives",
        arity = arity,
        helperClassSignature = { arrayPrimitiveClassSignature },
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

    private fun stringIsWhitespace(
        state: TsState,
        inputs: List<UExpr<*>>,
    ): TsUnknownCallModelExecution? = with(state.ctx) {
        val code = inputs.singleOrNull()?.takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort) ?: return null
        // The owning source models pass only values read from their UTF-16 backing.
        val codeUnit = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = code,
            bvSize = bv16Sort.sizeBits.toInt(),
            isSigned = false,
        ).asExpr(bv16Sort)

        singleSuccessor(state = state, guard = trueExpr, result = mkEcmaScriptWhitespace(codeUnit))
    }

    private fun stringCopyNormalizedRange(
        state: TsState,
        inputs: List<UExpr<*>>,
    ): TsUnknownCallModelExecution? = with(state.ctx) {
        val receiver = inputs.getOrNull(0)?.takeIf { it.sort == addressSort }?.asExpr(addressSort) ?: return null
        val fpFrom = inputs.getOrNull(1)?.takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort) ?: return null
        val fpTo = inputs.getOrNull(2)?.takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort) ?: return null
        val hasBacking = receiver in state.boundedStringBackingRefs && !receiver.hasFakeValueBranch()
        if (!hasBacking) return null

        val startIndex = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = fpFrom,
            bvSize = sizeSort.sizeBits.toInt(),
            isSigned = true,
        ).asExpr(sizeSort)
        val endIndex = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = fpTo,
            bvSize = sizeSort.sizeBits.toInt(),
            isSigned = true,
        ).asExpr(sizeSort)
        val integralBounds = listOf(fpFrom to startIndex, fpTo to endIndex).map { (number, index) ->
            val converted = mkBvToFpExpr(
                sort = fp64Sort,
                roundingMode = fpRoundingModeSortDefaultValue(),
                value = index.cast(),
                signed = true,
            )
            mkFpEqualExpr(number, converted)
        }
        val receiverIsString = state.memory.types.evalTypeEquals(receiver, EtsStringType)
        val boundsAreIntegral = mkAnd(integralBounds)
        val nonNegativeStart = mkBvSignedGreaterOrEqualExpr(startIndex, mkBv(0))
        val orderedBounds = mkBvSignedLessOrEqualExpr(startIndex, endIndex)
        val receiverLength = state.stringLength(receiver)
        val endWithinLength = mkBvSignedLessOrEqualExpr(endIndex, receiverLength)
        val guard = mkAnd(receiverIsString, boundsAreIntegral, nonNegativeStart, orderedBounds, endWithinLength)
        val successor = TsUnknownCallModelSuccessor(
            guard = guard,
            completion = TsUnknownCallModelCompletion.Normal {
                copyStringRange(receiver = receiver, from = startIndex, to = endIndex)
            },
        )

        TsUnknownCallModelExecution(successors = listOf(successor), residualGuard = mkNot(guard))
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
    private const val PRIMITIVE_IS_WHITESPACE_ID = "ts.string.primitive.isWhitespace"
    private const val PRIMITIVE_COPY_NORMALIZED_RANGE_ID = "ts.string.primitive.copyNormalizedRange"
    private const val MATH_FLOOR_MODEL_ID = "ts.math.floor"
    private const val MAX_ASCII_CASE_LENGTH = 16
    private const val ASCII_MAX_CODE_UNIT = 0x7f
}
