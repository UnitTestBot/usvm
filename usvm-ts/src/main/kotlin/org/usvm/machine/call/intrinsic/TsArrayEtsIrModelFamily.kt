package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.usvm.UExpr
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelArtifact
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.call.loadBundledEtsIrUnknownCallModelArtifact
import org.usvm.util.arrayStorageType

/** Built-in Array algorithms implemented by ordinary TypeScript bodies. */
internal object TsArrayEtsIrModelFamily : TsBuiltInUnknownCallModelFamily {
    private const val CLASS_NAME = "ArrayModels"
    private const val RESOURCE_NAME = "/org/usvm/machine/call/models/ArrayModels.ts"

    private val baseArtifact by lazy {
        loadBundledEtsIrUnknownCallModelArtifact(
            resourceName = RESOURCE_NAME,
            sourceFileName = "ArrayModels.ts",
            entryPointClassName = CLASS_NAME,
            entryPointMethodName = "pop",
        )
    }

    private val arrayDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        with(state.ctx) {
            val receiver = inputs.firstOrNull()
            val staticType = call.receiver?.source?.type
            if (staticType == null || receiver?.sort != addressSort) {
                falseExpr
            } else {
                val array = receiver.asExpr(addressSort)
                val receiverType = state.arrayStorageType(array, staticType) as? EtsArrayType
                val searchElement = inputs.getOrNull(1)
                val searchRef = searchElement
                    ?.takeIf { it.sort == addressSort }
                    ?.asExpr(addressSort)
                if (
                    array.hasFakeValueBranch() || receiverType?.dimensions != 1 ||
                    searchRef?.hasFakeValueBranch() == true
                ) {
                    falseExpr
                } else {
                    val elementSort = typeToSort(receiverType.elementType)
                    val excludesMissingSlot = when {
                        searchElement == mkUndefinedValue() &&
                            (
                                call.callee.name in setOf("indexOf", "lastIndexOf") ||
                                    elementSort == fp64Sort || elementSort == boolSort
                                ) -> falseExpr

                        elementSort == fp64Sort && searchElement?.sort == fp64Sort -> {
                            val searchNumber = searchElement.asExpr(fp64Sort)
                            val zero = mkFp64(0.0)

                            mkNot(mkFpEqualExpr(searchNumber, zero))
                        }

                        elementSort == boolSort && searchElement?.sort == boolSort -> {
                            searchElement.asExpr(boolSort)
                        }

                        else -> trueExpr
                    }

                    mkAnd(
                        state.memory.types.evalIsSubtype(array, receiverType),
                        excludesMissingSlot,
                    )
                }
            }
        }
    }

    private val optionalFromIndexAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        call.resolvedInstanceInputs()?.let { inputs ->
            when {
                call.arguments.size == 1 -> inputs + state.ctx.mkFp64(0.0)
                call.arguments.size == 2 && inputs.last() == state.ctx.mkUndefinedValue() -> {
                    inputs.dropLast(1) + state.ctx.mkFp64(0.0)
                }

                call.arguments.size == 2 && inputs.last().sort == state.ctx.fp64Sort -> inputs
                else -> null
            }
        }
    }

    private val optionalLastIndexAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        call.resolvedInstanceInputs()?.let { inputs ->
            when {
                call.arguments.size == 1 -> inputs + state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort)
                call.arguments.size == 2 && inputs.last() == state.ctx.mkUndefinedValue() -> {
                    inputs.dropLast(1) + state.ctx.mkFp64(0.0)
                }

                call.arguments.size == 2 && inputs.last().sort == state.ctx.fp64Sort -> inputs
                else -> null
            }
        }
    }

    override val models: List<TsUnknownCallModel> by lazy {
        listOf(
            sourceModel(
                id = "ts.array.pop",
                methodName = "pop",
            ),
            sourceModel(
                id = "ts.array.indexOf",
                methodName = "indexOf",
                inputAdapter = optionalFromIndexAdapter,
            ),
            sourceModel(
                id = "ts.array.includes",
                methodName = "includes",
                inputAdapter = optionalFromIndexAdapter,
            ),
            sourceModel(
                id = "ts.array.lastIndexOf",
                methodName = "lastIndexOf",
                inputAdapter = optionalLastIndexAdapter,
            ),
        )
    }

    private fun sourceModel(
        id: String,
        methodName: String,
        inputAdapter: TsEtsIrUnknownCallModelInputAdapter = TsEtsIrUnknownCallModelInputAdapter.IDENTITY,
    ): TsUnknownCallModel {
        val target = TsUnknownCallTarget(
            methodName = methodName,
            enclosingClassName = "Array".takeUnless { methodName == "pop" },
            failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
        )

        return TsEtsIrUnknownCallModel(
            id = id,
            target = target,
            artifact = artifact(methodName),
            domainGuard = arrayDomain,
            inputAdapter = inputAdapter,
            requiredModelIds = setOf(MATH_FLOOR_MODEL_ID).takeUnless { methodName == "pop" }.orEmpty(),
        )
    }

    private fun artifact(methodName: String): TsEtsIrUnknownCallModelArtifact {
        val artifact = baseArtifact
        val entryPoint = artifact.file.allClasses
            .single { it.name == CLASS_NAME }
            .methods
            .single { it.name == methodName }

        return artifact.copy(entryPoint = entryPoint)
    }

    private fun TsUnknownCall.resolvedInstanceInputs(): List<UExpr<*>>? {
        val resolvedReceiver = receiver?.resolved ?: return null
        val resolvedArguments = arguments.map { argument -> argument.resolved ?: return null }

        return listOf(resolvedReceiver) + resolvedArguments
    }

    private const val MATH_FLOOR_MODEL_ID = "ts.math.floor"
}
