package org.usvm.machine.call

import org.jacodb.ets.model.EtsType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.machine.state.TsState

/** Identifies the backend that executes a semantic model implementation. */
enum class TsUnknownCallModelImplementationKind {
    INTRINSIC,
}

/** Describes the semantic precision of a model within its declared supported domain. */
enum class TsUnknownCallModelPrecision {
    EXACT,
    PARTIAL,
}

/** Documents the inputs for which a semantic model provides its declared precision. */
data class TsUnknownCallModelSupportedDomain(
    val id: String,
    val description: String,
) {
    init {
        require(id.isNotBlank()) { "Semantic model supported-domain ID must not be blank" }
        require(description.isNotBlank()) { "Semantic model supported-domain description must not be blank" }
    }
}

/** Selects calls that are candidates for one semantic model without depending on its implementation backend. */
fun interface TsUnknownCallModelMatcher {
    fun matches(call: TsUnknownCall): Boolean
}

/** Backend-neutral metadata used to select and audit one semantic model. */
class TsUnknownCallModelDescriptor(
    val id: String,
    val matcher: TsUnknownCallModelMatcher,
    val supportedDomain: TsUnknownCallModelSupportedDomain,
    val precision: TsUnknownCallModelPrecision,
    val implementationKind: TsUnknownCallModelImplementationKind,
) {
    init {
        require(id.isNotBlank()) { "Semantic model ID must not be blank" }
    }
}

/** Describes how a guarded model successor completes the original call. */
sealed interface TsUnknownCallModelCompletion {
    /** Produces a normal result on the selected successor state. */
    class Normal(
        val result: TsState.() -> UExpr<*>,
    ) : TsUnknownCallModelCompletion

    /** Produces an exceptional result and its TypeScript type on the selected successor state. */
    class Exceptional(
        val exception: TsState.() -> Pair<UExpr<*>, EtsType>,
    ) : TsUnknownCallModelCompletion
}

/**
 * One guarded model successor.
 *
 * Successor guards within one execution must be pairwise disjoint. State changes and completion values are evaluated
 * only after the dispatcher has selected the corresponding successor state.
 */
class TsUnknownCallModelSuccessor(
    val guard: UBoolExpr,
    val completion: TsUnknownCallModelCompletion,
    val applyStateChanges: TsState.() -> Unit = {},
)

/**
 * A backend-neutral semantic-model execution plan.
 *
 * [residualGuard] denotes the unsupported part of a partial model's domain. Together, successor guards and the
 * residual guard must partition the current call domain.
 */
class TsUnknownCallModelExecution(
    successors: List<TsUnknownCallModelSuccessor>,
    val residualGuard: UBoolExpr?,
) {
    val successors: List<TsUnknownCallModelSuccessor> = successors.toList()

    init {
        require(this.successors.isNotEmpty()) { "A semantic model must declare at least one guarded successor" }
    }
}

/** The result of selecting and executing a semantic model for one call. */
sealed interface TsUnknownCallModelApplication {
    /** A structured guarded plan produced by the selected model. */
    class Applied(
        val modelId: String,
        val precision: TsUnknownCallModelPrecision,
        val execution: TsUnknownCallModelExecution,
    ) : TsUnknownCallModelApplication {
        init {
            require(modelId.isNotBlank()) { "Applied model ID must not be blank" }
            require(precision != TsUnknownCallModelPrecision.EXACT || execution.residualGuard == null) {
                "Exact semantic model $modelId must not produce a residual guard"
            }
            require(precision != TsUnknownCallModelPrecision.PARTIAL || execution.residualGuard != null) {
                "Partial semantic model $modelId must produce a residual guard"
            }
        }
    }

    /** Indicates that no enabled model matched the call. */
    data object NotApplicable : TsUnknownCallModelApplication
}

/** Selects and executes models without exposing registry or backend details to the dispatcher. */
fun interface TsUnknownCallModelProvider {
    fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelApplication
}
