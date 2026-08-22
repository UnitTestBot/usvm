package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDomain

const val FAST_CHECK_PROTOCOL_VERSION = 1

@Serializable
data class FastCheckProjectionRequest(
    val protocolVersion: Int = FAST_CHECK_PROTOCOL_VERSION,
    val requestId: String,
    val operation: String = "sample",
    val seed: Int,
    val numSamples: Int,
    val domains: List<PropertyDomain>,
)

data class FastCheckProjectionResponse(
    val protocolVersion: Int,
    val requestId: String,
    val samples: List<List<JsConcreteValue>>,
)

@Serializable
internal data class FastCheckProjectionWireResponse(
    val protocolVersion: Int,
    val requestId: String? = null,
    val status: String,
    val samples: List<List<JsConcreteValue>> = emptyList(),
    val diagnostics: List<FastCheckProtocolDiagnostic> = emptyList(),
)

@Serializable
internal data class FastCheckProtocolDiagnostic(
    val code: String,
    val message: String,
    val path: String? = null,
)

class FastCheckProjectionException(
    val code: String,
    message: String,
    val path: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
