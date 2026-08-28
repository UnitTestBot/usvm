package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDomain

/** One request sent from Kotlin to the private fast-check adapter process. */
@Serializable
data class FastCheckProjectionRequest(
    val seed: Int,
    val numSamples: Int,
    val domains: List<PropertyDomain>,
)

/** Validated samples returned by fast-check in positional input order. */
data class FastCheckProjectionResponse(
    val samples: List<List<JsConcreteValue>>,
)

@Serializable
internal data class FastCheckProjectionWireResponse(
    val status: String,
    val samples: List<List<JsConcreteValue>> = emptyList(),
    val diagnostics: List<FastCheckProtocolDiagnostic> = emptyList(),
)

@Serializable
internal data class FastCheckProtocolDiagnostic(
    val kind: BackendErrorKind,
    val code: String,
    val message: String,
    val path: String? = null,
)

/** Typed failure reported by the fast-check process or its Kotlin protocol boundary. */
class FastCheckProjectionException(
    val code: String,
    message: String,
    val path: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
