package org.usvm.ts.pbt.campaign

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.WitnessRole
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import java.nio.file.Files
import java.nio.file.Path

/**
 * The declared property and direct-run controls with the results actually produced.
 * Constructor settings such as machine options, runtime paths and tsconfig are not attested here.
 * Existing source and tool identities remain in the property and result provenance; absent identities stay unknown.
 */
@Serializable
data class PropertyCampaignArtifact(
    val property: PropertyDefinition,
    val configuration: PropertyRunConfiguration,
    val validationReserveMillis: Long,
    val result: PropertyCampaignResult,
    val targetRole: WitnessRole = WitnessRole.PROPERTY,
) {
    init {
        val resultProperties = listOfNotNull(
            result.propertyId,
            result.concrete?.propertyId,
            result.symbolic?.propertyId,
            result.confirmation?.candidate?.propertyId,
        )
        val sameProperty = resultProperties.all { it == property.id }
        require(sameProperty) { "Campaign evidence belongs to another property" }
        require(validationReserveMillis > 0) { "Validation reserve must be positive" }
        require(targetRole == WitnessRole.PROPERTY) { "Direct controls search the original property" }
    }

    /** Writes this completed snapshot; failure leaves the caller's artifact available for another destination. */
    fun write(path: Path) {
        val json = PropertyManifestJson.json.encodeToUtf8SafeString(this)
        Files.writeString(path, json)
    }

    companion object {
        fun read(path: Path): PropertyCampaignArtifact =
            PropertyManifestJson.json.decodeFromString(Files.readString(path))
    }
}
