package org.usvm.ts.pbt.backend

import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.usvm.UsvmPropertySearchResult
import org.usvm.ts.pbt.usvm.UsvmPropertyViolationTarget
import java.security.MessageDigest

/** Pins the complete registration, including ordered domains, generator, precondition, and original oracle. */
fun PropertyDefinition.witnessManifestSha256(): String {
    val bytes = PropertyManifestJson.json.encodeToUtf8SafeString(toManifest()).toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and BYTE_MASK)
    }
}

private const val BYTE_MASK = 0xff

fun PropertyDefinition.captureWitness(
    inputs: List<JsConcreteValue>?,
    target: WitnessTarget,
    sources: List<PropertyObservationSource>,
    provenance: String,
): ExternalWitnessCandidate = ExternalWitnessCandidate(
    propertyId = id,
    target = target,
    inputs = inputs,
    manifestSha256 = witnessManifestSha256(),
    manifest = toManifest(),
    sources = sources,
    provenance = provenance,
)

/** Failed extraction remains an unrepresentable candidate; symbolic reach alone never becomes a fault. */
fun UsvmPropertySearchResult.captureWitness(
    property: PropertyDefinition,
    sources: List<PropertyObservationSource>,
): ExternalWitnessCandidate {
    require(propertyId == property.id)
    requireNotNull(target) { "Capture requires a reached symbolic target" }

    return property.captureWitness(
        inputs = inputs,
        target = WitnessTarget(
            id = property.predicate.exportName,
            expectedOutcome = when (target) {
                UsvmPropertyViolationTarget.PREDICATE_FALSE -> WitnessOracleOutcome.FALSE
                UsvmPropertyViolationTarget.UNEXPECTED_EXCEPTION,
                UsvmPropertyViolationTarget.ASSERTION_FAILURE,
                -> WitnessOracleOutcome.THREW
            },
        ),
        sources = sources,
        provenance = "usvm:$status:$target",
    )
}
