package org.usvm.ts.pbt.manifest

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.validation.requireValid
import org.usvm.ts.pbt.validation.validatePropertyDefinition
import org.usvm.ts.pbt.validation.validatePropertyManifest

/**
 * Transport representation of a validated [PropertyDefinition].
 *
 * The manifest is the boundary shared with replaceable concrete PBT adapters and later symbolic projections.
 */
@Serializable
data class PropertyManifest(
    val propertyId: String,
    val inputs: List<PropertyInput>,
    val predicate: TypeScriptEntryPoint,
    val precondition: TypeScriptEntryPoint? = null,
)

fun PropertyDefinition.toManifest(): PropertyManifest {
    requireValid(validatePropertyDefinition(this))
    return PropertyManifest(
        propertyId = id.value,
        inputs = inputs,
        predicate = predicate,
        precondition = precondition,
    )
}

/** Strict JSON codec for property manifests. */
object PropertyManifestJson {
    val json = Json {
        classDiscriminator = "kind"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        useAlternativeNames = false
    }

    fun encode(manifest: PropertyManifest): String {
        requireValid(validatePropertyManifest(manifest))
        return json.encodeToString(manifest)
    }

    fun decode(value: String): PropertyManifest = json.decodeFromString<PropertyManifest>(value)
        .also { requireValid(validatePropertyManifest(it)) }
}
