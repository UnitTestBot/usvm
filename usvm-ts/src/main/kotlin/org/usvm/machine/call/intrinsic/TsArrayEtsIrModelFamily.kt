package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.usvm.UConcreteHeapRef
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelArtifact
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.call.loadBundledEtsIrUnknownCallModelArtifact
import org.usvm.machine.call.resolvedInstanceInputs
import org.usvm.util.arrayStorageType
import org.usvm.util.isUnmodifiedDenseInputArray

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
                    ?: return@TsEtsIrUnknownCallModelDomainGuard falseExpr
                val searchElement = inputs.getOrNull(1)
                val searchRef = searchElement
                    ?.takeIf { it.sort == addressSort }
                    ?.asExpr(addressSort)
                val hasUnsupportedArrayShape = array.hasFakeValueBranch() || receiverType.dimensions != 1 ||
                    searchRef?.hasFakeValueBranch() == true
                if (hasUnsupportedArrayShape) {
                    falseExpr
                } else {
                    val elementSort = typeToSort(receiverType.elementType)
                    val hasNoMissingSlots = array is UConcreteHeapRef &&
                        state.isUnmodifiedDenseInputArray(array, receiverType)
                    val excludesMissingSlot = when {
                        searchElement == mkUndefinedValue() &&
                            (
                                call.callee.name in setOf("indexOf", "lastIndexOf") ||
                                    elementSort == fp64Sort || elementSort == boolSort
                                ) -> mkBool(hasNoMissingSlots)

                        elementSort == fp64Sort && searchElement?.sort == fp64Sort -> {
                            val searchNumber = searchElement.asExpr(fp64Sort)
                            val zero = mkFp64(0.0)

                            mkOr(mkBool(hasNoMissingSlots), mkNot(mkFpEqualExpr(searchNumber, zero)))
                        }

                        elementSort == boolSort && searchElement?.sort == boolSort -> {
                            mkOr(mkBool(hasNoMissingSlots), searchElement.asExpr(boolSort))
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

    private val receiverOnlyAdapter = TsEtsIrUnknownCallModelInputAdapter { _, call ->
        call.resolvedInstanceInputs(maxArgumentCount = 0)
    }

    private val optionalFromIndexAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs(maxArgumentCount = 2)
            ?: return@TsEtsIrUnknownCallModelInputAdapter null
        if (inputs.size < 2) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }

        val fromIndex = inputs.getOrNull(2)
        when {
            fromIndex == null || fromIndex == state.ctx.mkUndefinedValue() -> inputs.take(2) + state.ctx.mkFp64(0.0)
            fromIndex.sort == state.ctx.fp64Sort -> inputs
            else -> null
        }
    }

    private val optionalLastIndexAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs(maxArgumentCount = 2)
            ?: return@TsEtsIrUnknownCallModelInputAdapter null
        if (inputs.size < 2) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }

        val fromIndex = inputs.getOrNull(2)
        when {
            fromIndex == null -> inputs + state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort)
            fromIndex == state.ctx.mkUndefinedValue() -> inputs.take(2) + state.ctx.mkFp64(0.0)
            fromIndex.sort == state.ctx.fp64Sort -> inputs
            else -> null
        }
    }

    override val models: List<TsUnknownCallModel> by lazy {
        listOf(
            sourceModel(
                id = "ts.array.pop",
                methodName = "pop",
                inputAdapter = receiverOnlyAdapter,
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
        inputAdapter: TsEtsIrUnknownCallModelInputAdapter,
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

    private const val MATH_FLOOR_MODEL_ID = "ts.math.floor"
}
