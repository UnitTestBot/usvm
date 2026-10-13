package org.usvm.ts.calls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNamespaceSignature
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallDecision
import org.usvm.machine.call.TsUnknownCallEvent

@Serializable
internal data class CallsExperimentCellIdentity(
    val experimentId: String,
    val projectId: String,
    val revision: String,
    val development: Boolean,
    val functionId: String,
    val targetId: String,
    val siteId: String,
    val targetMode: CallsSourceTargetMode,
    val profile: CallsExperimentProfile,
    val seed: Long,
)

@Serializable
internal data class CallsUnknownCallSite(
    val sourcePath: String,
    val statementIndex: Int,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
    val start: CallsSourcePosition? = null,
    val end: CallsSourcePosition? = null,
)

@Serializable
internal data class CallsUnknownCallCallee(
    val calleeId: String,
    val sourcePath: String,
    val enclosingClass: String,
    val name: String,
    val parameterTypes: List<String>,
    val returnType: String,
)

@Serializable
internal enum class CallsUnknownCallDecisionKind {
    MODEL_APPLIED,
    RESIDUAL_FALLBACK,
}

@Serializable
@SerialName("unknown-call")
internal data class CallsUnknownCallRecord(
    val cell: CallsExperimentCellIdentity,
    val eventIndex: Int,
    val callSite: CallsUnknownCallSite,
    val callee: CallsUnknownCallCallee,
    val failureReason: String,
    val decision: CallsUnknownCallDecisionKind,
    val outcome: String,
    val modelId: String? = null,
    val residualPolicy: String? = null,
) : CallsRawRecord {
    init {
        require(eventIndex >= 1) { "Unknown-call event index must be positive" }
        require((decision == CallsUnknownCallDecisionKind.MODEL_APPLIED) == (modelId != null)) {
            "Exactly a model-applied decision must carry a model ID"
        }
        require((decision == CallsUnknownCallDecisionKind.RESIDUAL_FALLBACK) == (residualPolicy != null)) {
            "Exactly a residual-fallback decision must carry a residual policy"
        }
    }
}

internal fun CallsSymbolicSearchRequest.cellIdentity(experimentId: String): CallsExperimentCellIdentity =
    CallsExperimentCellIdentity(
        experimentId = experimentId,
        projectId = project.projectId,
        revision = project.revision,
        development = project.development,
        functionId = function.functionId,
        targetId = target.targetId,
        siteId = target.siteId,
        targetMode = target.mode,
        profile = profile,
        seed = seed,
    )

internal fun CallsExperimentCellIdentity.unknownCallRecord(
    event: TsUnknownCallEvent,
    eventIndex: Int,
): CallsUnknownCallRecord {
    val containingMethod = event.callSite.location.method.signature
    val origin = event.callSite.location.origin
    val decision = event.decision

    return CallsUnknownCallRecord(
        cell = this,
        eventIndex = eventIndex,
        callSite = CallsUnknownCallSite(
            sourcePath = containingMethod.enclosingClass.file.fileName,
            statementIndex = event.callSite.location.index,
            startOffset = origin?.startOffset,
            endOffset = origin?.endOffset,
            start = origin?.let { span -> CallsSourcePosition(line = span.startLine, column = span.startColumn) },
            end = origin?.let { span -> CallsSourcePosition(line = span.endLine, column = span.endColumn) },
        ),
        callee = event.callee.toCallsUnknownCallCallee(),
        failureReason = event.failureReason.name,
        decision = when (decision) {
            is TsUnknownCallDecision.ModelApplied -> CallsUnknownCallDecisionKind.MODEL_APPLIED
            is TsUnknownCallDecision.ResidualFallback -> CallsUnknownCallDecisionKind.RESIDUAL_FALLBACK
        },
        outcome = event.outcome.name,
        modelId = (decision as? TsUnknownCallDecision.ModelApplied)?.modelId,
        residualPolicy = (decision as? TsUnknownCallDecision.ResidualFallback)?.policy?.serializedName,
    )
}

internal fun callsUnknownCallEventSink(
    cell: CallsExperimentCellIdentity,
    appendAndFlush: (CallsUnknownCallRecord) -> Unit,
): (TsUnknownCallEvent) -> Unit {
    var eventIndex = 0

    return { event ->
        eventIndex++
        appendAndFlush(cell.unknownCallRecord(event = event, eventIndex = eventIndex))
    }
}

private fun EtsMethodSignature.toCallsUnknownCallCallee(): CallsUnknownCallCallee {
    val parameterTypes = parameters.map { parameter -> parameter.type.toString() }
    val namespace = enclosingClass.namespace?.qualifiedName()
    val className = listOfNotNull(namespace, enclosingClass.name).joinToString(separator = "::")
    val sourcePath = enclosingClass.file.fileName
    val returnType = returnType.toString()
    val signature = "$className:$name(${parameterTypes.joinToString(separator = ",")})->$returnType"

    return CallsUnknownCallCallee(
        calleeId = "$sourcePath:$signature",
        sourcePath = sourcePath,
        enclosingClass = className,
        name = name,
        parameterTypes = parameterTypes,
        returnType = returnType,
    )
}

private fun EtsNamespaceSignature.qualifiedName(): String =
    listOfNotNull(namespace?.qualifiedName(), name).joinToString(separator = "::")

private val TsResidualCallPolicy.serializedName: String
    get() = name
