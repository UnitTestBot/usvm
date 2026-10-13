package org.usvm.machine.call.intrinsic

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.UNullRef
import org.usvm.api.typeStreamOf
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelArtifact
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.call.isBuiltinDateGlobalType
import org.usvm.machine.call.loadBundledEtsIrUnknownCallModelArtifact
import org.usvm.machine.expr.dateModelTimestampLValue
import org.usvm.machine.expr.mkIsNullOrUndefined
import org.usvm.machine.state.TsState
import org.usvm.types.singleOrNull
import org.usvm.util.boolToFp

/** Numeric `Date` API family implemented by an ordinary TypeScript source body. */
internal object TsDateEtsIrModelFamily : TsBuiltInUnknownCallModelFamily {
    private const val DATE_CLASS = "Date"
    private const val MAX_CONSTRUCTOR_ARGUMENTS = 7
    private const val RESOURCE = "/org/usvm/machine/call/models/DateModels.ts"
    private val builtinDateSignature = EtsClassSignature.UNKNOWN.copy(name = DATE_CLASS)
    private val builtinDateType = EtsClassType(signature = builtinDateSignature)
    private val getterNames = listOf(
        "getDate",
        "getDay",
        "getFullYear",
        "getHours",
        "getMilliseconds",
        "getMinutes",
        "getMonth",
        "getSeconds",
        "getTime",
        "getTimezoneOffset",
        "getUTCDate",
        "getUTCDay",
        "getUTCFullYear",
        "getUTCHours",
        "getUTCMilliseconds",
        "getUTCMinutes",
        "getUTCMonth",
        "getUTCSeconds",
        "toISOString",
        "valueOf",
    )

    /** Normalize only model lookup; residual calls and observation retain the frontend signature. */
    internal fun canonicalModelCall(state: TsState, call: TsUnknownCall): TsUnknownCall? {
        if (call.callee.enclosingClass.name == DATE_CLASS) return null

        val receiverValue = call.receiver ?: return null
        val canonicalCall = call.copy(callee = call.callee.copy(enclosingClass = builtinDateSignature))
        val builtinStaticOwner = canonicalCall.hasBuiltinDateStaticOwner()
        if (!builtinStaticOwner && !isDateReceiver(state, receiverValue.resolved)) return null

        return canonicalCall
    }

    internal fun isDateReceiver(state: TsState, receiver: UExpr<*>?): Boolean = with(state.ctx) {
        if (receiver?.sort != addressSort) return@with false

        val isNullish = receiver is UNullRef || receiver == mkTsNullValue()
        if (isNullish) return@with false

        val runtimeType = state.memory.typeStreamOf(receiver.asExpr(addressSort)).singleOrNull()
        runtimeType == builtinDateType
    }

    private val artifact by lazy {
        loadBundledEtsIrUnknownCallModelArtifact(
            resourceName = RESOURCE,
            sourceFileName = "DateModels.ts",
            entryPointClassName = "DateModels",
            entryPointMethodName = "construct",
        )
    }

    internal fun ownsStorageDefinitionField(field: EtsFieldSignature): Boolean =
        field.name == "timestamp" && field.enclosingClass.name == "DateValue" &&
            field.enclosingClass.file == artifact.file.signature

    override val models: List<TsUnknownCallModel> by lazy {
        buildList {
            add(constructorModel())
            add(utcModel())
            add(nowModel())

            for (methodName in getterNames) {
                add(instanceModel(idSuffix = methodName, methodName = methodName, consumedArgs = 0))
            }

            add(instanceModel(idSuffix = "set-date", methodName = "setDate", consumedArgs = 1))
            add(
                instanceArityModel(
                    idSuffix = "set-full-year",
                    methodName = "setFullYear",
                    entryPointName = "setFullYear",
                    maxArgs = 3,
                )
            )
            add(
                instanceArityModel(
                    idSuffix = "set-hours",
                    methodName = "setHours",
                    entryPointName = "setHours",
                    maxArgs = 4,
                )
            )
            add(
                instanceModel(
                    idSuffix = "set-milliseconds",
                    methodName = "setMilliseconds",
                    consumedArgs = 1,
                )
            )
            add(
                instanceArityModel(
                    idSuffix = "set-minutes",
                    methodName = "setMinutes",
                    entryPointName = "setMinutes",
                    maxArgs = 3,
                )
            )
            add(
                instanceArityModel(
                    idSuffix = "set-month",
                    methodName = "setMonth",
                    entryPointName = "setMonth",
                    maxArgs = 2,
                )
            )
            add(
                instanceArityModel(
                    idSuffix = "set-seconds",
                    methodName = "setSeconds",
                    entryPointName = "setSeconds",
                    maxArgs = 2,
                )
            )
            add(instanceModel(idSuffix = "set-time", methodName = "setTime", consumedArgs = 1))
            add(instanceModel(idSuffix = "set-utc-date", methodName = "setUTCDate", consumedArgs = 1))
            add(
                instanceArityModel(
                    idSuffix = "set-utc-full-year",
                    methodName = "setUTCFullYear",
                    entryPointName = "setUTCFullYear",
                    maxArgs = 3,
                )
            )
            add(
                instanceArityModel(
                    idSuffix = "set-utc-hours",
                    methodName = "setUTCHours",
                    entryPointName = "setUTCHours",
                    maxArgs = 4,
                )
            )
            add(
                instanceModel(
                    idSuffix = "set-utc-milliseconds",
                    methodName = "setUTCMilliseconds",
                    consumedArgs = 1,
                )
            )
            add(
                instanceArityModel(
                    idSuffix = "set-utc-minutes",
                    methodName = "setUTCMinutes",
                    entryPointName = "setUTCMinutes",
                    maxArgs = 3,
                )
            )
            add(
                instanceArityModel(
                    idSuffix = "set-utc-month",
                    methodName = "setUTCMonth",
                    entryPointName = "setUTCMonth",
                    maxArgs = 2,
                )
            )
            add(
                instanceArityModel(
                    idSuffix = "set-utc-seconds",
                    methodName = "setUTCSeconds",
                    entryPointName = "setUTCSeconds",
                    maxArgs = 2,
                )
            )
        }
    }

    private fun constructorModel(): TsUnknownCallModel = model(
        idSuffix = "constructor",
        methodName = CONSTRUCTOR_NAME,
        entryPointName = "construct",
        domainGuard = instanceGuard(maxArgs = MAX_CONSTRUCTOR_ARGUMENTS, allowDateCopy = true),
        inputAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
            with(state.ctx) {
                val receiver = call.receiver?.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null
                val nowMilliseconds = dateNowMilliseconds
                if (call.arguments.isEmpty() && nowMilliseconds == null) {
                    return@TsEtsIrUnknownCallModelInputAdapter null
                }

                val arguments = call.numericArguments(
                    maxArgs = MAX_CONSTRUCTOR_ARGUMENTS,
                    state = state,
                    allowDateCopy = true,
                )?.map { it.value }?.toMutableList()
                    ?: return@TsEtsIrUnknownCallModelInputAdapter null
                while (arguments.size < MAX_CONSTRUCTOR_ARGUMENTS) {
                    arguments += mkFp64(0.0)
                }
                val providedArgumentCount = mkFp64(call.arguments.size.toDouble())
                val fallbackClock = mkFp64(nowMilliseconds ?: 0.0)

                buildList {
                    add(receiver)
                    add(providedArgumentCount)
                    add(fallbackClock)
                    addAll(arguments)
                }
            }
        },
    )

    private fun nowModel(): TsUnknownCallModel = model(
        idSuffix = "now",
        methodName = "now",
        entryPointName = "now",
        domainGuard = staticOwnerGuard(maxArgs = 0),
        inputAdapter = TsEtsIrUnknownCallModelInputAdapter { state, _ ->
            with(state.ctx) {
                val nowMilliseconds = dateNowMilliseconds ?: return@TsEtsIrUnknownCallModelInputAdapter null
                listOf(mkFp64(nowMilliseconds))
            }
        },
    )

    private fun utcModel(): TsUnknownCallModel = model(
        idSuffix = "utc",
        methodName = "UTC",
        entryPointName = "utc",
        domainGuard = staticOwnerGuard(maxArgs = MAX_CONSTRUCTOR_ARGUMENTS),
        inputAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
            with(state.ctx) {
                val arguments = call.numericArguments(maxArgs = MAX_CONSTRUCTOR_ARGUMENTS, state = state)
                    ?.map { it.value }
                    ?.toMutableList()
                    ?: return@TsEtsIrUnknownCallModelInputAdapter null
                while (arguments.size < MAX_CONSTRUCTOR_ARGUMENTS) {
                    arguments += mkFp64(0.0)
                }

                listOf(mkFp64(call.arguments.size.toDouble())) + arguments
            }
        },
    )

    private fun instanceModel(
        idSuffix: String,
        methodName: String,
        consumedArgs: Int,
    ): TsUnknownCallModel = model(
        idSuffix = idSuffix,
        methodName = methodName,
        entryPointName = methodName,
        domainGuard = instanceGuard(maxArgs = consumedArgs),
        inputAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
            val receiver = call.receiver?.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null
            val arguments = call.numericArguments(maxArgs = consumedArgs, state = state)
                ?.map { it.value }
                ?.toMutableList()
                ?: return@TsEtsIrUnknownCallModelInputAdapter null
            while (arguments.size < consumedArgs) {
                arguments += state.ctx.mkFp64NaN()
            }

            listOf(receiver) + arguments
        },
    )

    private fun instanceArityModel(
        idSuffix: String,
        methodName: String,
        entryPointName: String,
        maxArgs: Int,
    ): TsUnknownCallModel = model(
        idSuffix = idSuffix,
        methodName = methodName,
        entryPointName = entryPointName,
        domainGuard = instanceGuard(maxArgs = maxArgs),
        inputAdapter = arityAdapter(maxArgs = maxArgs),
    )

    private fun model(
        idSuffix: String,
        methodName: String,
        entryPointName: String,
        domainGuard: TsEtsIrUnknownCallModelDomainGuard,
        inputAdapter: TsEtsIrUnknownCallModelInputAdapter,
    ) = TsEtsIrUnknownCallModel(
        id = "ts.date.$idSuffix",
        target = TsUnknownCallTarget(
            methodName = methodName,
            enclosingClassName = DATE_CLASS,
        ),
        artifact = artifact.withEntryPoint(entryPointName),
        domainGuard = domainGuard,
        inputAdapter = inputAdapter,
        requiredModelIds = setOf("ts.math.floor"),
    )

    private fun instanceGuard(
        maxArgs: Int,
        allowDateCopy: Boolean = false,
    ) = TsEtsIrUnknownCallModelDomainGuard { state, call, _ ->
        with(state.ctx) {
            val receiverValue = call.receiver?.resolved ?: return@TsEtsIrUnknownCallModelDomainGuard falseExpr
            val unsupportedReceiver = call.callee.enclosingClass != builtinDateSignature ||
                receiverValue.sort != addressSort ||
                receiverValue.asExpr(addressSort).hasFakeValueBranch()
            if (unsupportedReceiver) {
                return@TsEtsIrUnknownCallModelDomainGuard falseExpr
            }

            val arguments = call.numericArguments(maxArgs = maxArgs, state = state, allowDateCopy = allowDateCopy)
                ?: return@TsEtsIrUnknownCallModelDomainGuard falseExpr

            mkAnd(mkBool(isDateReceiver(state = state, receiver = receiverValue)), mkAnd(arguments.map { it.guard }))
        }
    }

    private fun staticOwnerGuard(maxArgs: Int) = TsEtsIrUnknownCallModelDomainGuard { state, call, _ ->
        with(state.ctx) {
            val arguments = call.numericArguments(maxArgs = maxArgs, state = state)
                ?: return@TsEtsIrUnknownCallModelDomainGuard falseExpr

            mkAnd(mkBool(call.hasBuiltinDateStaticOwner()), mkAnd(arguments.map { it.guard }))
        }
    }

    private fun TsUnknownCall.hasBuiltinDateStaticOwner(): Boolean {
        if (callee.enclosingClass != builtinDateSignature) return false

        val owner = receiver?.source as? EtsLocal ?: return false
        return owner.name == DATE_CLASS && owner.type.isBuiltinDateGlobalType()
    }

    private fun arityAdapter(maxArgs: Int) = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        with(state.ctx) {
            val receiver = call.receiver?.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null
            val arguments = call.numericArguments(maxArgs = maxArgs, state = state)?.map { it.value }?.toMutableList()
                ?: return@TsEtsIrUnknownCallModelInputAdapter null
            if (arguments.isEmpty()) {
                // The mandatory first parameter is undefined when omitted; later parameters keep their defaults.
                arguments += mkFp64NaN()
            }

            val providedArgumentCount = arguments.size
            while (arguments.size < maxArgs) {
                arguments += mkFp64(0.0)
            }

            listOf(receiver, mkFp64(providedArgumentCount.toDouble())) + arguments
        }
    }

    private data class NumericDateArgument(val value: UExpr<KFp64Sort>, val guard: UBoolExpr)

    private fun TsUnknownCall.numericArguments(
        maxArgs: Int,
        state: TsState,
        allowDateCopy: Boolean = false,
    ): List<NumericDateArgument>? = arguments.take(maxArgs).map { argument ->
        val resolved = argument.resolved ?: return null
        numericArgument(state = state, value = resolved, allowDateCopy = allowDateCopy && arguments.size == 1)
    }

    private fun numericArgument(
        state: TsState,
        value: UExpr<*>,
        allowDateCopy: Boolean,
    ): NumericDateArgument = with(state.ctx) {
        when {
            value.isFakeObject() -> {
                val type = value.getFakeType(state.memory)
                val reference = numericArgument(state, value.extractRef(state.memory), allowDateCopy = allowDateCopy)
                val booleanNumber = boolToFp(value.extractBool(state.memory))
                val result = mkIte(
                    type.fpTypeExpr,
                    value.extractFp(state.memory),
                    mkIte(type.boolTypeExpr, booleanNumber, reference.value),
                )
                val guard = mkOr(type.fpTypeExpr, type.boolTypeExpr, mkAnd(type.refTypeExpr, reference.guard))

                NumericDateArgument(value = result, guard = guard)
            }

            value is UIteExpr<*> -> {
                val whenTrue = numericArgument(state, value.trueBranch, allowDateCopy = allowDateCopy)
                val whenFalse = numericArgument(state, value.falseBranch, allowDateCopy = allowDateCopy)

                NumericDateArgument(
                    value = mkIte(value.condition, whenTrue.value, whenFalse.value),
                    guard = mkIte(value.condition, whenTrue.guard, whenFalse.guard),
                )
            }

            value.sort == fp64Sort -> NumericDateArgument(value = value.asExpr(fp64Sort), guard = trueExpr)
            value.sort == boolSort -> {
                val booleanNumber = boolToFp(value.asExpr(boolSort))

                NumericDateArgument(value = booleanNumber, guard = trueExpr)
            }

            value.sort == addressSort -> referenceArgument(
                state = state,
                reference = value.asExpr(addressSort),
                allowDateCopy = allowDateCopy,
            )

            else -> NumericDateArgument(value = mkFp64NaN(), guard = falseExpr)
        }
    }

    private fun referenceArgument(
        state: TsState,
        reference: UHeapRef,
        allowDateCopy: Boolean,
    ): NumericDateArgument = with(state.ctx) {
        val isNull = mkHeapRefEq(reference, mkTsNullValue())
        val isNullish = mkIsNullOrUndefined(reference)
        val copiesDate = allowDateCopy && isDateReceiver(state = state, receiver = reference)
        val timestamp = if (copiesDate) {
            state.memory.read(dateModelTimestampLValue(reference))
        } else {
            mkFp64NaN()
        }
        val value = mkIte(isNull, mkFp64(0.0), mkIte(isNullish, mkFp64NaN(), timestamp))

        NumericDateArgument(value = value, guard = mkOr(isNullish, mkBool(copiesDate)))
    }

    private fun TsEtsIrUnknownCallModelArtifact.withEntryPoint(methodName: String): TsEtsIrUnknownCallModelArtifact {
        val modelClass = file.allClasses.single { it.name == "DateModels" }
        val method = modelClass.methods.single { it.name == methodName }
        return copy(entryPoint = method)
    }
}
