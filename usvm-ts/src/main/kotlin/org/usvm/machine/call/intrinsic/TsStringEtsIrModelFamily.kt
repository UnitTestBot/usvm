package org.usvm.machine.call.intrinsic

import io.ksmt.expr.KFp64Value
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.evalTypeEquals
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelArtifact
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.call.loadBundledEtsIrUnknownCallModelArtifact
import org.usvm.machine.state.TsState
import org.usvm.sizeSort
import org.usvm.util.copyStringRange
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.stringFromCodeUnit

/** Built-in String algorithms implemented by ordinary TypeScript bodies. */
internal object TsStringEtsIrModelFamily : TsBuiltInUnknownCallModelFamily {
    private const val CLASS_NAME = "StringModels"
    private const val PRIMITIVES_CLASS_NAME = "StringModelPrimitives"
    private const val RESOURCE_NAME = "/org/usvm/machine/call/models/StringModels.ts"

    private val characterArrayType = EtsArrayType(EtsNumberType, dimensions = 1)

    private val baseArtifact by lazy {
        loadBundledEtsIrUnknownCallModelArtifact(
            resourceName = RESOURCE_NAME,
            sourceFileName = "StringModels.ts",
            entryPointClassName = CLASS_NAME,
            entryPointMethodName = "charAt",
        )
    }

    private val optionalIndexAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        call.resolvedInstanceInputs()?.let { inputs ->
            when {
                call.arguments.isEmpty() -> inputs + state.ctx.mkFp64(0.0)
                call.arguments.size == 1 && inputs.last() == state.ctx.mkUndefinedValue() -> {
                    inputs.dropLast(1) + state.ctx.mkFp64(0.0)
                }

                call.arguments.size == 1 && inputs.last().sort == state.ctx.fp64Sort -> inputs
                else -> null
            }
        }
    }

    private val optionalPositionAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        call.resolvedInstanceInputs()?.let { inputs ->
            when {
                call.arguments.size == 1 && inputs.last().sort == state.ctx.addressSort -> {
                    inputs + state.ctx.mkFp64(0.0)
                }

                call.arguments.size == 2 && inputs[1].sort == state.ctx.addressSort &&
                    inputs.last() == state.ctx.mkUndefinedValue() -> {
                    inputs.dropLast(1) + state.ctx.mkFp64(0.0)
                }

                call.arguments.size == 2 && inputs[1].sort == state.ctx.addressSort &&
                    inputs.last().sort == state.ctx.fp64Sort -> inputs

                else -> null
            }
        }
    }

    private val optionalEndPositionAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        call.resolvedInstanceInputs()?.let { inputs ->
            when {
                call.arguments.size == 1 && inputs.last().sort == state.ctx.addressSort -> {
                    inputs + state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort)
                }

                call.arguments.size == 2 && inputs[1].sort == state.ctx.addressSort &&
                    inputs.last() == state.ctx.mkUndefinedValue() -> {
                    inputs.dropLast(1) + state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort)
                }

                call.arguments.size == 2 && inputs[1].sort == state.ctx.addressSort &&
                    inputs.last().sort == state.ctx.fp64Sort -> inputs

                else -> null
            }
        }
    }

    private val sliceAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs() ?: return@TsEtsIrUnknownCallModelInputAdapter null
        val receiver = inputs.firstOrNull()?.takeIf { it.sort == state.ctx.addressSort }
            ?: return@TsEtsIrUnknownCallModelInputAdapter null
        val arguments = inputs.drop(1)
        if (arguments.size > 2) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }

        fun numericOrDefault(index: Int, default: UExpr<*>): UExpr<*> {
            val value = arguments.getOrNull(index) ?: return default
            return when {
                value == state.ctx.mkUndefinedValue() -> default
                value.sort == state.ctx.fp64Sort -> value
                else -> return default
            }
        }

        val start = numericOrDefault(index = 0, default = state.ctx.mkFp64(0.0))
        val end = numericOrDefault(
            index = 1,
            default = state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort),
        )
        if (arguments.any { value -> value != state.ctx.mkUndefinedValue() && value.sort != state.ctx.fp64Sort }) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }

        listOf(receiver, start, end)
    }

    private val noArgumentsAdapter = TsEtsIrUnknownCallModelInputAdapter { _, call ->
        if (call.arguments.isNotEmpty()) {
            null
        } else {
            call.resolvedInstanceInputs()
        }
    }

    private val receiverDomain = TsEtsIrUnknownCallModelDomainGuard { state, _, inputs ->
        with(state.ctx) {
            val receiver = inputs.firstOrNull()
            if (receiver !is UConcreteHeapRef || receiver.hasFakeValueBranch()) {
                falseExpr
            } else {
                state.memory.types.evalTypeEquals(receiver, EtsStringType)
            }
        }
    }

    private val receiverAndSearchDomain = TsEtsIrUnknownCallModelDomainGuard { state, _, inputs ->
        with(state.ctx) {
            val receiver = inputs.getOrNull(0)
            val searchString = inputs.getOrNull(1)
            val receiverIsConstant = receiver is UConcreteHeapRef && !receiver.hasFakeValueBranch()
            val searchStringIsConstant = searchString is UConcreteHeapRef && !searchString.hasFakeValueBranch()

            if (!receiverIsConstant || !searchStringIsConstant) {
                falseExpr
            } else {
                mkAnd(
                    state.memory.types.evalTypeEquals(receiver, EtsStringType),
                    state.memory.types.evalTypeEquals(searchString, EtsStringType),
                )
            }
        }
    }

    private val asciiReceiverDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        with(state.ctx) {
            val receiverGuard = receiverDomain.evaluate(state, call, inputs)
            val receiver = inputs.firstOrNull() as? UConcreteHeapRef
                ?: return@TsEtsIrUnknownCallModelDomainGuard falseExpr
            val characters = state.memory.read(mkFieldLValue(addressSort, receiver, "value"))
                .asExpr(addressSort)
            val length = state.memory.read(mkArrayLengthLValue(characters, characterArrayType))
            val characterGuards = (0 until MAX_ASCII_CASE_LENGTH).map { index ->
                val symbolicIndex = mkBv(index)
                val codeUnit = state.memory.read(
                    mkArrayIndexLValue(
                        sort = bv16Sort,
                        ref = characters,
                        index = symbolicIndex,
                        type = characterArrayType,
                    )
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
                inputAdapter = noArgumentsAdapter,
                domainGuard = receiverDomain,
            ),
            sourceModel(
                id = "ts.string.trimStart",
                methodName = "trimStart",
                inputAdapter = noArgumentsAdapter,
                domainGuard = receiverDomain,
            ),
            sourceModel(
                id = "ts.string.trimEnd",
                methodName = "trimEnd",
                inputAdapter = noArgumentsAdapter,
                domainGuard = receiverDomain,
            ),
            sourceModel(
                id = "ts.string.toUpperCase",
                methodName = "toUpperCase",
                inputAdapter = noArgumentsAdapter,
                domainGuard = asciiReceiverDomain,
            ),
            sourceModel(
                id = "ts.string.toLowerCase",
                methodName = "toLowerCase",
                inputAdapter = noArgumentsAdapter,
                domainGuard = asciiReceiverDomain,
            ),
            primitiveModel(
                methodName = "length",
                arity = 1,
                implementation = ::stringLength,
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
                methodName = "copyRange",
                arity = 3,
                implementation = ::stringCopyRange,
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
            add(MATH_FLOOR_MODEL_ID)
            add(PRIMITIVE_LENGTH_ID)
            add(PRIMITIVE_CODE_UNIT_AT_ID)
            if (methodName in setOf("charAt", "toUpperCase", "toLowerCase")) {
                add(PRIMITIVE_FROM_CODE_UNIT_ID)
            }
            if (methodName in setOf("slice", "substring", "trim", "trimStart", "trimEnd")) {
                add(PRIMITIVE_COPY_RANGE_ID)
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
    ): TsUnknownCallModel = StringPrimitiveModel(
        methodName = methodName,
        arity = arity,
        implementation = implementation,
    )

    private fun stringLength(
        state: TsState,
        inputs: List<UExpr<*>>,
    ): TsUnknownCallModelExecution? = with(state.ctx) {
        val receiver = inputs.singleOrNull()?.takeIf { it.sort == addressSort }?.asExpr(addressSort) ?: return null
        if (receiver.hasFakeValueBranch()) {
            return null
        }

        val characters = state.memory.read(mkFieldLValue(addressSort, receiver, "value"))
        val length = state.memory.read(mkArrayLengthLValue(characters, characterArrayType))
        val receiverIsString = state.memory.types.evalTypeEquals(receiver, EtsStringType)
        val result = mkBvToFpExpr(
            sort = fp64Sort,
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = length.cast(),
            signed = true,
        )

        singleSuccessor(
            state = state,
            guard = receiverIsString,
            result = result,
        )
    }

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
        val characters = state.memory.read(mkFieldLValue(addressSort, receiver, "value"))
        val codeUnit = state.memory.read(
            mkArrayIndexLValue(
                sort = bv16Sort,
                ref = characters,
                index = index,
                type = characterArrayType,
            )
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

    private fun stringCopyRange(
        state: TsState,
        inputs: List<UExpr<*>>,
    ): TsUnknownCallModelExecution? = with(state.ctx) {
        val receiver = inputs.getOrNull(0)?.takeIf { it.sort == addressSort }?.asExpr(addressSort) ?: return null
        val start = inputs.getOrNull(1)?.takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort) ?: return null
        val end = inputs.getOrNull(2)?.takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort) ?: return null
        if (receiver.hasFakeValueBranch()) {
            return null
        }

        val from = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = start,
            bvSize = sizeSort.sizeBits.toInt(),
            isSigned = true,
        ).asExpr(sizeSort)
        val to = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = end,
            bvSize = sizeSort.sizeBits.toInt(),
            isSigned = true,
        ).asExpr(sizeSort)
        val length = mkBvSubExpr(to, from)
        val receiverIsString = state.memory.types.evalTypeEquals(receiver, EtsStringType)
        val successor = TsUnknownCallModelSuccessor(
            guard = receiverIsString,
            completion = TsUnknownCallModelCompletion.Normal {
                copyStringRange(receiver = receiver, from = from, length = length)
            },
        )

        TsUnknownCallModelExecution(
            successors = listOf(successor),
            residualGuard = receiverIsString.takeUnless { it == trueExpr }?.let(::mkNot),
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

    private fun TsUnknownCall.resolvedInstanceInputs(): List<UExpr<*>>? {
        val resolvedReceiver = receiver?.resolved ?: return null
        val resolvedArguments = arguments.map { argument -> argument.resolved ?: return null }

        return listOf(resolvedReceiver) + resolvedArguments
    }

    private class StringPrimitiveModel(
        methodName: String,
        private val arity: Int,
        private val implementation: (TsState, List<UExpr<*>>) -> TsUnknownCallModelExecution?,
    ) : TsUnknownCallModel {
        override val id: String = "ts.string.primitive.$methodName"
        override val target = TsUnknownCallTarget(
            methodName = methodName,
            enclosingClassName = PRIMITIVES_CLASS_NAME,
            failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
        )

        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
            if (call.receiver != null || call.arguments.size != arity) {
                return null
            }

            val inputs = call.arguments.map { argument -> argument.resolved ?: return null }
            return implementation(state, inputs)
        }
    }

    private const val PRIMITIVE_LENGTH_ID = "ts.string.primitive.length"
    private const val PRIMITIVE_CODE_UNIT_AT_ID = "ts.string.primitive.codeUnitAt"
    private const val PRIMITIVE_FROM_CODE_UNIT_ID = "ts.string.primitive.fromCodeUnit"
    private const val PRIMITIVE_COPY_RANGE_ID = "ts.string.primitive.copyRange"
    private const val MATH_FLOOR_MODEL_ID = "ts.math.floor"
    private const val MAX_ASCII_CASE_LENGTH = 16
    private const val ASCII_MAX_CODE_UNIT = 0x7f
}
