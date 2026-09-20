package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelArtifact
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
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
                // Array storage has no presence bit yet, so indexOf(undefined) cannot distinguish a hole.
                val searchesForUndefined = call.callee.name == "indexOf" &&
                    inputs.getOrNull(1) == mkUndefinedValue()
                if (array.hasFakeValueBranch() || receiverType?.dimensions != 1 || searchesForUndefined) {
                    falseExpr
                } else {
                    state.memory.types.evalIsSubtype(array, receiverType)
                }
            }
        }
    }

    private val optionalFromIndexAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call, inputs ->
        when {
            call.arguments.size == 1 -> inputs + state.ctx.mkFp64(0.0)
            call.arguments.size == 2 && inputs.last() == state.ctx.mkUndefinedValue() ->
                inputs.dropLast(1) + state.ctx.mkFp64(0.0)

            call.arguments.size == 2 && inputs.last().sort == state.ctx.fp64Sort -> inputs
            else -> null
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
        )
    }

    private fun sourceModel(
        id: String,
        methodName: String,
        inputAdapter: TsEtsIrUnknownCallModelInputAdapter = TsEtsIrUnknownCallModelInputAdapter.IDENTITY,
    ): TsUnknownCallModel = TsEtsIrUnknownCallModel(
        id = id,
        target = TsUnknownCallTarget(
            methodName = methodName,
            enclosingClassName = "Array".takeUnless { methodName == "pop" },
            failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
        ),
        artifact = artifact(methodName),
        domainGuard = arrayDomain,
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
}
