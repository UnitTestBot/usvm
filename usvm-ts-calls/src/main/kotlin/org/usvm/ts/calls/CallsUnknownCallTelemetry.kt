package org.usvm.ts.calls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNamespaceSignature
import org.jacodb.ets.model.EtsStmt
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
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

@Serializable
@SerialName("runtime-limitation")
internal data class CallsRuntimeLimitationRecord(
    val cell: CallsExperimentCellIdentity,
    val eventIndex: Int,
    val callSite: CallsUnknownCallSite,
    val reason: String,
    val detail: String,
) : CallsRawRecord

internal fun callsRuntimeLimitationEventSink(
    cell: CallsExperimentCellIdentity,
    appendAndFlush: (CallsRuntimeLimitationRecord) -> Unit,
): (TsRuntimeFeatureLimitationEvent) -> Unit {
    var eventIndex = 0
    return { event ->
        eventIndex++
        val record = CallsRuntimeLimitationRecord(
            cell = cell,
            eventIndex = eventIndex,
            callSite = event.statement.toCallsUnknownCallSite(),
            reason = event.reason.name,
            detail = event.detail,
        )
        appendAndFlush(record)
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
    val decision = event.decision

    return CallsUnknownCallRecord(
        cell = this,
        eventIndex = eventIndex,
        callSite = event.callSite.toCallsUnknownCallSite(),
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

private fun EtsStmt.toCallsUnknownCallSite(): CallsUnknownCallSite {
    val stmtLocation = location
    val origin = stmtLocation.origin
    val containingMethod = stmtLocation.method.signature

    return CallsUnknownCallSite(
        sourcePath = containingMethod.enclosingClass.file.fileName,
        statementIndex = stmtLocation.index,
        startOffset = origin?.startOffset,
        endOffset = origin?.endOffset,
        start = origin?.let { CallsSourcePosition(line = it.startLine, column = it.startColumn) },
        end = origin?.let { CallsSourcePosition(line = it.endLine, column = it.endColumn) },
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
