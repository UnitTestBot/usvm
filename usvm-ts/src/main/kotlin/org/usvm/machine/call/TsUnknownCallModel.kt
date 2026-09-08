package org.usvm.machine.call

import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.machine.state.TsState
import org.usvm.machine.types.TsUnresolvedValue

/** Declaratively identifies the calls handled by one semantic model. */
data class TsUnknownCallTarget(
    val methodName: String,
    val enclosingClassName: String? = null,
    val failureReason: TsUnknownCallFailureReason? = null,
) {
    init {
        require(methodName.isNotBlank()) { "Semantic model target method name must not be blank" }
        require(enclosingClassName == null || enclosingClassName.isNotBlank()) {
            "Semantic model target class name must not be blank"
        }
    }

    internal fun matches(call: TsUnknownCall): Boolean =
        call.callee.name == methodName &&
            (enclosingClassName == null || call.callee.enclosingClass.name == enclosingClassName) &&
            (failureReason == null || call.failureReason == failureReason)

    internal fun overlaps(other: TsUnknownCallTarget): Boolean {
        val classNamesOverlap = enclosingClassName == null ||
            other.enclosingClassName == null ||
            enclosingClassName == other.enclosingClassName
        val failureReasonsOverlap = failureReason == null ||
            other.failureReason == null ||
            failureReason == other.failureReason

        return methodName == other.methodName && classNamesOverlap && failureReasonsOverlap
    }
}

/**
 * A semantic model selected by a stable [id] and a declarative [target].
 *
 * Returning `null` from [apply] means that the call is outside the model's supported input domain. The dispatcher
 * then applies the configured fallback. A non-null execution may additionally contain a guarded residual domain.
 */
interface TsUnknownCallModel {
    val id: String
    val target: TsUnknownCallTarget

    /** EtsIR files that must be visible to the interpreter while this model is enabled. */
    val additionalSceneFiles: List<EtsFile>
        get() = emptyList()

    fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution?
}

/** Describes how a guarded model successor completes the original call. */
sealed interface TsUnknownCallModelCompletion {
    /** Produces a normal result on the selected successor state. */
    class Normal(
        val result: TsState.() -> UExpr<*>,
    ) : TsUnknownCallModelCompletion

    /** Produces a normal fake-wrapped result for a value whose runtime kind is unresolved. */
    class Unresolved(
        val value: TsUnresolvedValue,
    ) : TsUnknownCallModelCompletion

    /** Produces an exceptional result and its TypeScript type on the selected successor state. */
    class Exceptional(
        val exception: TsState.() -> Pair<UExpr<*>, EtsType>,
    ) : TsUnknownCallModelCompletion

    /** Enters a TypeScript model body through the normal EtsIR interpreter. */
    class EtsIrBody(
        val entryPoint: EtsMethod,
        inputs: List<UExpr<*>>,
    ) : TsUnknownCallModelCompletion {
        val inputs: List<UExpr<*>> = inputs.toList()
    }
}

/** One guarded model successor. */
class TsUnknownCallModelSuccessor(
    val guard: UBoolExpr,
    val completion: TsUnknownCallModelCompletion,
    val applyStateChanges: TsState.() -> Unit = {},
)

/**
 * A semantic-model execution plan.
 *
 * [residualGuard] is the input domain not covered by the model. `null` means that the model completely handles every
 * state accepted by [TsUnknownCallModel.apply].
 */
class TsUnknownCallModelExecution(
    successors: List<TsUnknownCallModelSuccessor>,
    val residualGuard: UBoolExpr? = null,
) {
    val successors: List<TsUnknownCallModelSuccessor> = successors.toList()

    init {
        require(this.successors.isNotEmpty()) { "A semantic model must declare at least one guarded successor" }
    }
}

/** The result of model lookup for one call. */
sealed interface TsUnknownCallModelApplication {
    class Applied(
        val modelId: String,
        val execution: TsUnknownCallModelExecution,
    ) : TsUnknownCallModelApplication {
        init {
            require(modelId.isNotBlank()) { "Applied model ID must not be blank" }
        }
    }

    /** No enabled model accepted the call. */
    data object NotApplicable : TsUnknownCallModelApplication
}
