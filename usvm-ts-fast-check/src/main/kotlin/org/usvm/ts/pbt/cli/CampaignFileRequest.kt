package org.usvm.ts.pbt.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.campaign.PropertyCampaignMode
import org.usvm.ts.pbt.campaign.PropertyCampaignRunner
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** One frozen experiment cell using the public campaign, with no service-loaded registry. */
@Serializable
internal data class CampaignFileRequest(
    val sourceRoot: String,
    val property: PropertyDefinition,
    val mode: PropertyCampaignMode,
    val configuration: PropertyRunConfiguration,
    val pbtAllocationMillis: Long? = null,
    val validationReserveMillis: Long = PropertyCampaignRunner.DEFAULT_VALIDATION_RESERVE_MILLIS,
    val completedInputLimit: Int = 0,
    val shrinkWitness: Boolean = false,
    val nodeExecutable: String = "node",
    val externalDeadlineEpochMillis: Long? = null,
    val publicationReserveMillis: Long = 0,
)

internal fun runCampaignFile(
    args: Array<String>,
    output: Appendable = System.out,
    errors: Appendable = System.err,
): Int = try {
    require(args.size == 2) { "campaign-file requires a request JSON and an artifact destination" }
    val requestFile = Path.of(args[0]).toRealPath()
    val request = PropertyManifestJson.json.decodeFromString<CampaignFileRequest>(Files.readString(requestFile))
    val root = requestFile.parent.resolve(request.sourceRoot).normalize().toRealPath()
    val artifact = PropertyCampaignRunner(
        sourceRoot = root,
        nodeExecutable = request.nodeExecutable,
        machineOptions = UMachineOptions(
            solverType = SolverType.YICES,
            randomSeed = request.configuration.seed?.toLong() ?: 0,
        ),
    ).runArtifact(
        property = request.property,
        mode = request.mode,
        configuration = request.configuration,
        validationReserveMillis = request.validationReserveMillis,
        pbtAllocationMillis = request.pbtAllocationMillis,
        shrinkWitness = request.shrinkWitness,
        completedInputLimit = request.completedInputLimit,
        externalDeadlineEpochMillis = request.externalDeadlineEpochMillis,
        publicationReserveMillis = request.publicationReserveMillis,
    )

    val json = PropertyManifestJson.json.encodeToUtf8SafeString(artifact)
    try {
        Files.writeString(Path.of(args[1]), json)
    } finally {
        output.appendLine(json)
    }

    campaignExitCode(artifact.result)
} catch (error: IllegalArgumentException) {
    errors.appendLine(error.message)
    2
} catch (error: IOException) {
    errors.appendLine(error.message)
    2
}
