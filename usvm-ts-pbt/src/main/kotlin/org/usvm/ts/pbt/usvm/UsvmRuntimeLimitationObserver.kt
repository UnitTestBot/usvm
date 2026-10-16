package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsStmt
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallOutcome
import org.usvm.ts.pbt.backend.CapabilityDiagnostic

/** Retains a bounded sample of distinct model boundaries without keeping live engine objects. */
internal class UsvmRuntimeLimitationObserver : TsInterpreterObserver {
    private val limitations = linkedSetOf<CapabilityDiagnostic>()
    private var truncated = false

    val diagnostics: List<CapabilityDiagnostic>
        get() = limitations.toList() + if (truncated) listOf(TRUNCATED_DIAGNOSTIC) else emptyList()

    override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
        val diagnostic = CapabilityDiagnostic(
            code = "usvm.runtime.${event.reason.name.lowercase().replace('_', '-')}",
            message = "${event.reason.name}: ${event.detail}; statement: ${event.statement}",
            path = event.statement.diagnosticPath(),
        )
        record(diagnostic)
    }

    override fun onUnknownCall(event: TsUnknownCallEvent) {
        if (event.outcome == TsUnknownCallOutcome.MODEL_APPLIED) return

        val outcome = event.outcome.name.lowercase().replace('_', '-')
        val diagnostic = CapabilityDiagnostic(
            code = "usvm.unknown-call.$outcome",
            message = "${event.outcome.name}: ${event.callee}; reason=${event.failureReason.name}; " +
                "statement: ${event.callSite}",
            path = event.callSite.diagnosticPath(),
        )
        record(diagnostic)
    }

    private fun record(diagnostic: CapabilityDiagnostic) {
        if (diagnostic in limitations) return

        if (limitations.size < MAX_DIAGNOSTICS) {
            limitations += diagnostic
        } else {
            truncated = true
        }
    }

    private companion object {
        const val MAX_DIAGNOSTICS = 64
        val TRUNCATED_DIAGNOSTIC = CapabilityDiagnostic(
            code = "usvm.runtime.diagnostics-truncated",
            message = "Additional runtime limitations were omitted after $MAX_DIAGNOSTICS distinct diagnostics",
            path = "predicate",
        )
    }
}

private fun EtsStmt.diagnosticPath(): String {
    val origin = location.origin
    if (origin != null) {
        // EtsIR origins use zero-based positions; diagnostics use ordinary editor coordinates.
        return "${origin.fileName}:${origin.startLine + 1}:${origin.startColumn + 1}-" +
            "${origin.endLine + 1}:${origin.endColumn + 1}"
    }

    val method = location.method.signature
    val source = method.enclosingClass.file.fileName
    return "$source#${method.name}[${location.index}]"
}
