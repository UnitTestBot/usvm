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
import org.usvm.api.initializeArray
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
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue

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
                call.arguments.size == 1 && inputs.last() == state.ctx.mkUndefinedValue() ->
                    inputs.dropLast(1) + state.ctx.mkFp64(0.0)

                call.arguments.size == 1 && inputs.last().sort == state.ctx.fp64Sort -> inputs
                else -> null
            }
        }
    }

    private val optionalPositionAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        call.resolvedInstanceInputs()?.let { inputs ->
            when {
                call.arguments.size == 1 && inputs.last().sort == state.ctx.addressSort ->
                    inputs + state.ctx.mkFp64(0.0)

                call.arguments.size == 2 && inputs[1].sort == state.ctx.addressSort &&
                    inputs.last() == state.ctx.mkUndefinedValue() ->
                    inputs.dropLast(1) + state.ctx.mkFp64(0.0)

                call.arguments.size == 2 && inputs[1].sort == state.ctx.addressSort &&
                    inputs.last().sort == state.ctx.fp64Sort -> inputs

                else -> null
            }
        }
    }

    private val optionalEndPositionAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        call.resolvedInstanceInputs()?.let { inputs ->
            when {
                call.arguments.size == 1 && inputs.last().sort == state.ctx.addressSort ->
                    inputs + state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort)

                call.arguments.size == 2 && inputs[1].sort == state.ctx.addressSort &&
                    inputs.last() == state.ctx.mkUndefinedValue() ->
                    inputs.dropLast(1) + state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort)

                call.arguments.size == 2 && inputs[1].sort == state.ctx.addressSort &&
                    inputs.last().sort == state.ctx.fp64Sort -> inputs

                else -> null
            }
        }
    }

    private val receiverDomain = TsEtsIrUnknownCallModelDomainGuard { state, _, inputs ->
        with(state.ctx) {
            val receiver = inputs.firstOrNull()
            // Symbolic String parameters do not initialize the backing character array yet.
            if (receiver !is UConcreteHeapRef || getStringConstantValue(receiver) == null) {
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
            if (
                receiver !is UConcreteHeapRef || searchString !is UConcreteHeapRef ||
                getStringConstantValue(receiver) == null || getStringConstantValue(searchString) == null
            ) {
                falseExpr
            } else {
                mkAnd(
                    state.memory.types.evalTypeEquals(receiver, EtsStringType),
                    state.memory.types.evalTypeEquals(searchString, EtsStringType),
                )
            }
        }
    }

    private val receiverAndConcreteIndexDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        if (inputs.getOrNull(1) !is KFp64Value) {
            state.ctx.falseExpr
        } else {
            receiverDomain.evaluate(state, call, inputs)
        }
    }

    override val models: List<TsUnknownCallModel> by lazy {
        listOf(
            sourceModel(
                id = "ts.string.charAt",
                methodName = "charAt",
                inputAdapter = optionalIndexAdapter,
                domainGuard = receiverAndConcreteIndexDomain,
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

        TsUnknownCallModelExecution(
            successors = listOf(
                TsUnknownCallModelSuccessor(
                    guard = trueExpr,
                    completion = TsUnknownCallModelCompletion.Normal {
                        if (code is KFp64Value) {
                            return@Normal mkInitializedStringConstant(code.value.toInt().toChar().toString())
                        }

                        val codeUnit = ctx.mkFpToBvExpr(
                            roundingMode = ctx.fpRoundingModeSortDefaultValue(),
                            value = code,
                            bvSize = ctx.bv16Sort.sizeBits.toInt(),
                            isSigned = false,
                        ).asExpr(ctx.bv16Sort)
                        val result = memory.allocConcrete(EtsStringType)
                        val characters = memory.allocConcrete(characterArrayType.elementType)
                        memory.initializeArray(
                            arrayHeapRef = characters,
                            type = ctx.arrayDescriptorOf(characterArrayType),
                            sort = ctx.bv16Sort,
                            sizeSort = ctx.sizeSort,
                            contents = sequenceOf(codeUnit),
                        )
                        memory.write(
                            mkFieldLValue(ctx.addressSort, result, "value"),
                            characters,
                            guard = ctx.trueExpr,
                        )

                        result
                    },
                )
            ),
        )
    }

    private fun singleSuccessor(
        state: TsState,
        guard: org.usvm.UBoolExpr,
        result: UExpr<*>,
    ): TsUnknownCallModelExecution = TsUnknownCallModelExecution(
        successors = listOf(
            TsUnknownCallModelSuccessor(
                guard = guard,
                completion = TsUnknownCallModelCompletion.Normal { result },
            )
        ),
        residualGuard = guard.takeUnless { it == state.ctx.trueExpr }?.let(state.ctx::mkNot),
    )

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
}
