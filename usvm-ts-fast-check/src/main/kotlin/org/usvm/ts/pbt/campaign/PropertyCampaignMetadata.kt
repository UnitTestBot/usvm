package org.usvm.ts.pbt.campaign

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.observation.PropertyObservationSource
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Files read before engine work; imports, automatically discovered configuration and build outputs are excluded. */
@Serializable
enum class PropertyCampaignSourceScope {
    PROPERTY_ENTRY_MODULES,
}

/** One explicitly supplied concrete-runtime configuration file, without its extended configuration closure. */
@Serializable
data class PropertyCampaignTsconfig(
    val path: String,
    val sha256: String,
)

/**
 * Requested execution settings and the selected files observed before any engine work.
 * This is not a full project/build identity: machine options, imports and effective tsconfig are not recorded.
 * Observed Node versions belong to the actual concrete run and witness replay results.
 */
@Serializable
data class PropertyCampaignMetadata(
    val sourceRoot: String,
    val requestedNodeCommand: String,
    val sources: List<PropertyObservationSource>,
    val explicitConcreteTsconfig: PropertyCampaignTsconfig? = null,
    val sourceScope: PropertyCampaignSourceScope = PropertyCampaignSourceScope.PROPERTY_ENTRY_MODULES,
)

internal fun campaignSourceSha256(path: Path): String = MessageDigest.getInstance("SHA-256")
    .digest(Files.readAllBytes(path))
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

private const val BYTE_MASK = 0xff
