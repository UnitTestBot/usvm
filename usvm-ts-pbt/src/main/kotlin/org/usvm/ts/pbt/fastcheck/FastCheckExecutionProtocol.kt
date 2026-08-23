package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.model.JsConcreteValue

internal const val FAST_CHECK_EXECUTION_PROTOCOL_VERSION = 1

@Serializable
internal data class FastCheckExecutionRequest(
    val protocolVersion: Int = FAST_CHECK_EXECUTION_PROTOCOL_VERSION,
    val manifest: PropertyManifest,
    val sourceRoots: List<String>,
    val seed: Int? = null,
    val replayPath: String? = null,
    val numRuns: Int,
    val timeoutMillis: Long,
    val examples: List<List<JsConcreteValue>> = emptyList(),
)

@Serializable
internal data class FastCheckExecutionWireResponse(
    val protocolVersion: Int,
    val status: String,
    val result: PropertyRunResult? = null,
    val diagnostics: List<FastCheckProtocolDiagnostic> = emptyList(),
)

/** Infrastructure categories that remain distinct from a falsified property result. */
enum class BackendErrorKind {
    INVALID_REQUEST,
    ENTRY_POINT,
    PROCESS_FAILURE,
    PROTOCOL_ERROR,
    TIMEOUT,
}

/** Typed failure at the concrete backend boundary. */
class PbtBackendException(
    val kind: BackendErrorKind,
    val code: String,
    message: String,
    val propertyId: String? = null,
    val path: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
