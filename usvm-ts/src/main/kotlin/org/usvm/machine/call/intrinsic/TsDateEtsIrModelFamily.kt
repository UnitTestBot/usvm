package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.UExpr
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelArtifact
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.call.loadBundledEtsIrUnknownCallModelArtifact
import org.usvm.machine.state.TsState

/** Numeric `Date` API family implemented by an ordinary TypeScript source body. */
internal object TsDateEtsIrModelFamily : TsBuiltInUnknownCallModelFamily {
    private const val DATE_CLASS = "Date"
    private const val MAX_CONSTRUCTOR_ARGUMENTS = 7
    private const val RESOURCE = "/org/usvm/machine/call/models/DateModels.ts"

    private val artifact by lazy {
        loadBundledEtsIrUnknownCallModelArtifact(
            resourceName = RESOURCE,
            sourceFileName = "DateModels.ts",
            entryPointClassName = "DateModels",
            entryPointMethodName = "construct",
        )
    }

    override val models: List<TsUnknownCallModel> by lazy {
        buildList {
            add(constructorModel())
            add(utcModel())
            add(nowModel())

            for (methodName in listOf(
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
            )) {
                add(instanceModel(idSuffix = methodName, methodName = methodName, consumedArgs = 0))
            }

            add(instanceModel(idSuffix = "set-date", methodName = "setDate", consumedArgs = 1))
            add(instanceArityModel(
                idSuffix = "set-full-year",
                methodName = "setFullYear",
                entryPointName = "setFullYear",
                minArgs = 1,
                maxArgs = 3,
            ))
            add(instanceArityModel(
                idSuffix = "set-hours",
                methodName = "setHours",
                entryPointName = "setHours",
                minArgs = 1,
                maxArgs = 4,
            ))
            add(instanceModel(
                idSuffix = "set-milliseconds",
                methodName = "setMilliseconds",
                consumedArgs = 1,
            ))
            add(instanceArityModel(
                idSuffix = "set-minutes",
                methodName = "setMinutes",
                entryPointName = "setMinutes",
                minArgs = 1,
                maxArgs = 3,
            ))
            add(instanceArityModel(
                idSuffix = "set-month",
                methodName = "setMonth",
                entryPointName = "setMonth",
                minArgs = 1,
                maxArgs = 2,
            ))
            add(instanceArityModel(
                idSuffix = "set-seconds",
                methodName = "setSeconds",
                entryPointName = "setSeconds",
                minArgs = 1,
                maxArgs = 2,
            ))
            add(instanceModel(idSuffix = "set-time", methodName = "setTime", consumedArgs = 1))
            add(instanceModel(idSuffix = "set-utc-date", methodName = "setUTCDate", consumedArgs = 1))
            add(instanceArityModel(
                idSuffix = "set-utc-full-year",
                methodName = "setUTCFullYear",
                entryPointName = "setUTCFullYear",
                minArgs = 1,
                maxArgs = 3,
            ))
            add(instanceArityModel(
                idSuffix = "set-utc-hours",
                methodName = "setUTCHours",
                entryPointName = "setUTCHours",
                minArgs = 1,
                maxArgs = 4,
            ))
            add(instanceModel(
                idSuffix = "set-utc-milliseconds",
                methodName = "setUTCMilliseconds",
                consumedArgs = 1,
            ))
            add(instanceArityModel(
                idSuffix = "set-utc-minutes",
                methodName = "setUTCMinutes",
                entryPointName = "setUTCMinutes",
                minArgs = 1,
                maxArgs = 3,
            ))
            add(instanceArityModel(
                idSuffix = "set-utc-month",
                methodName = "setUTCMonth",
                entryPointName = "setUTCMonth",
                minArgs = 1,
                maxArgs = 2,
            ))
            add(instanceArityModel(
                idSuffix = "set-utc-seconds",
                methodName = "setUTCSeconds",
                entryPointName = "setUTCSeconds",
                minArgs = 1,
                maxArgs = 2,
            ))
        }
    }

    private fun constructorModel(): TsUnknownCallModel = model(
        idSuffix = "constructor",
        methodName = CONSTRUCTOR_NAME,
        entryPointName = "construct",
        domainGuard = instanceGuard(minArgs = 0, numericArgs = MAX_CONSTRUCTOR_ARGUMENTS),
        inputAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
            with(state.ctx) {
                val receiver = call.receiver?.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null
                val nowMilliseconds = dateNowMilliseconds
                if (call.arguments.isEmpty() && nowMilliseconds == null) {
                    return@TsEtsIrUnknownCallModelInputAdapter null
                }
                val arguments = call.resolvedArguments(maxArgs = MAX_CONSTRUCTOR_ARGUMENTS)?.toMutableList()
                    ?: return@TsEtsIrUnknownCallModelInputAdapter null
                while (arguments.size < MAX_CONSTRUCTOR_ARGUMENTS) {
                    arguments += mkFp64(0.0)
                }
                val argumentCount = mkFp64(call.arguments.size.toDouble())
                val clock = mkFp64(nowMilliseconds ?: 0.0)

                listOf(
                    receiver,
                    argumentCount,
                    clock,
                ) + arguments
            }
        },
    )

    private fun nowModel(): TsUnknownCallModel = model(
        idSuffix = "now",
        methodName = "now",
        entryPointName = "now",
        domainGuard = numericGuard(minArgs = 0, numericArgs = 0),
        inputAdapter = TsEtsIrUnknownCallModelInputAdapter { state, _ ->
            with(state.ctx) {
                listOf(mkFp64(dateNowMilliseconds ?: return@TsEtsIrUnknownCallModelInputAdapter null))
            }
        },
    )

    private fun utcModel(): TsUnknownCallModel = model(
        idSuffix = "utc",
        methodName = "UTC",
        entryPointName = "utc",
        domainGuard = TsEtsIrUnknownCallModelDomainGuard { state, call, _ ->
            if (call.hasNumericOrUndefinedArguments(maxArgs = MAX_CONSTRUCTOR_ARGUMENTS, state = state)) {
                state.ctx.trueExpr
            } else {
                state.ctx.falseExpr
            }
        },
        inputAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
            with(state.ctx) {
                val arguments = call.arguments.take(MAX_CONSTRUCTOR_ARGUMENTS).map { argument ->
                    when (val resolved = argument.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null) {
                        mkUndefinedValue() -> mkFp64NaN()
                        else -> resolved
                    }
                }.toMutableList()
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
        domainGuard = instanceGuard(minArgs = 0, numericArgs = consumedArgs),
        inputAdapter = TsEtsIrUnknownCallModelInputAdapter { _, call ->
            val receiver = call.receiver?.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null
            val arguments = call.resolvedArguments(consumedArgs)
                ?: return@TsEtsIrUnknownCallModelInputAdapter null
            listOf(receiver) + arguments
        },
    )

    private fun instanceArityModel(
        idSuffix: String,
        methodName: String,
        entryPointName: String,
        minArgs: Int,
        maxArgs: Int,
    ): TsUnknownCallModel = model(
        idSuffix = idSuffix,
        methodName = methodName,
        entryPointName = entryPointName,
        domainGuard = instanceGuard(minArgs = minArgs, numericArgs = maxArgs),
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
    )

    private fun instanceGuard(
        minArgs: Int,
        numericArgs: Int,
    ) = TsEtsIrUnknownCallModelDomainGuard { state, call, _ ->
        with(state.ctx) {
            val receiver = call.receiver
            val receiverValue = receiver?.resolved
            if (
                call.callee.enclosingClass.name != DATE_CLASS ||
                receiverValue?.sort != addressSort ||
                receiverValue.asExpr(addressSort).hasFakeValueBranch()
            ) {
                falseExpr
            } else if (!call.hasNumericArguments(minArgs = minArgs, numericArgs = numericArgs, state = state)) {
                falseExpr
            } else {
                trueExpr
            }
        }
    }

    private fun numericGuard(
        minArgs: Int,
        numericArgs: Int,
    ) = TsEtsIrUnknownCallModelDomainGuard { state, call, _ ->
        if (call.hasNumericArguments(minArgs = minArgs, numericArgs = numericArgs, state = state)) {
            state.ctx.trueExpr
        } else {
            state.ctx.falseExpr
        }
    }

    private fun arityAdapter(maxArgs: Int) = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        with(state.ctx) {
            val receiver = call.receiver?.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null
            val arguments = call.resolvedArguments(maxArgs)?.toMutableList()
                ?: return@TsEtsIrUnknownCallModelInputAdapter null
            while (arguments.size < maxArgs) {
                arguments += mkFp64(0.0)
            }

            listOf(receiver, mkFp64(call.arguments.size.toDouble())) + arguments
        }
    }

    private fun TsUnknownCall.resolvedArguments(maxArgs: Int): List<UExpr<*>>? =
        arguments.take(maxArgs).map { argument ->
            argument.resolved ?: return null
        }

    private fun TsUnknownCall.hasNumericArguments(
        minArgs: Int,
        numericArgs: Int,
        state: TsState,
    ): Boolean {
        if (arguments.size < minArgs) {
            return false
        }

        return arguments.take(numericArgs).all { argument -> argument.resolved?.sort == state.ctx.fp64Sort }
    }

    private fun TsUnknownCall.hasNumericOrUndefinedArguments(
        maxArgs: Int,
        state: TsState,
    ): Boolean = with(state.ctx) {
        arguments.take(maxArgs).all { argument ->
            val resolved = argument.resolved
            resolved?.sort == fp64Sort || resolved == mkUndefinedValue()
        }
    }

    private fun TsEtsIrUnknownCallModelArtifact.withEntryPoint(methodName: String): TsEtsIrUnknownCallModelArtifact {
        val modelClass = file.allClasses.single { it.name == "DateModels" }
        val method = modelClass.methods.single { it.name == methodName }
        return copy(entryPoint = method)
    }
}
