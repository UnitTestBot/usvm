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
 * Metadata records selected files and requested runtime settings, with an explicit limited source scope.
 * Effective machine options and complete project/build identities are not attested here.
 * Existing source and tool identities remain in the property and result provenance; absent identities stay unknown.
 */
@Serializable
data class PropertyCampaignArtifact(
    val property: PropertyDefinition,
    val configuration: PropertyRunConfiguration,
    val validationReserveMillis: Long,
    val result: PropertyCampaignResult,
    val targetRole: WitnessRole = WitnessRole.PROPERTY,
    val metadata: PropertyCampaignMetadata? = null,
    val pbtAllocationMillis: Long? = null,
    val shrinkWitness: Boolean = true,
    val completedInputLimit: Int = 0,
    val relationGuidanceControls: PropertyRelationGuidanceControls? = null,
) {
    init {
        val resultProperties = listOfNotNull(
            result.propertyId,
            result.concrete?.propertyId,
            result.symbolic?.propertyId,
            result.confirmation?.candidate?.propertyId,
        ) + result.symbolicInputReplays.flatMap { replay ->
            listOfNotNull(replay.candidate.propertyId, replay.replay.coverage?.propertyId)
        } + listOfNotNull(result.relationGuidance?.selection?.inference?.propertyId) +
            result.relationGuidance?.attempts.orEmpty().flatMap { attempt ->
                listOfNotNull(attempt.witness?.propertyId, attempt.confirmation?.candidate?.propertyId)
            }
        val sameProperty = resultProperties.all { it == property.id }
        require(sameProperty) { "Campaign evidence belongs to another property" }
        require(completedInputLimit >= 0)
        require(result.symbolicInputReplays.size <= completedInputLimit)
        require(pbtAllocationMillis == null || pbtAllocationMillis in 1 until configuration.timeoutMillis)
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
