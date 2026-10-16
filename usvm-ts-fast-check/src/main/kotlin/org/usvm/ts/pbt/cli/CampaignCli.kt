package org.usvm.ts.pbt.cli

import org.usvm.ts.pbt.FastCheckDiagnosticCode
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.campaign.PropertyCampaignResult
import org.usvm.ts.pbt.campaign.PropertyCampaignRunner
import org.usvm.ts.pbt.campaign.PropertyCampaignStop
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import java.io.IOException
import java.nio.file.Files

internal fun executeCampaign(
    options: CliOptions,
    property: PropertyDefinition,
    configuration: PropertyRunConfiguration,
    output: Appendable,
): Int {
    val mode = requireNotNull(options.mode)
    val destination = requireNotNull(options.artifactFile)
    val runner = PropertyCampaignRunner(sourceRoot = options.sourceRoots.single())
    val reserve = options.validationReserveMillis
    val artifact = runner.runArtifact(
        property = property,
        mode = mode,
        configuration = configuration,
        validationReserveMillis = reserve ?: PropertyCampaignRunner.DEFAULT_VALIDATION_RESERVE_MILLIS,
        pbtAllocationMillis = options.pbtAllocationMillis,
        shrinkWitness = options.shrinkWitness,
    )

    val json = PropertyManifestJson.json.encodeToUtf8SafeString(artifact)
    output.appendLine(json)

    try {
        Files.writeString(destination, json)
    } catch (error: IOException) {
        throw CliUsageException(
            code = FastCheckDiagnosticCode.CLI_CAMPAIGN_ARTIFACT_WRITE_FAILED,
            message = "Cannot write campaign artifact to $destination: " + error.message,
            path = "artifact",
            cause = error,
        )
    }

    return campaignExitCode(artifact.result)
}

/** A demonstrated violation remains a finding even when secondary work cannot finish. */
internal fun campaignExitCode(result: PropertyCampaignResult): Int {
    if (result.confirmedViolation) return EXIT_CONFIRMED_VIOLATION

    val concreteCompleted = result.concrete?.status != PropertyRunStatus.FAILURE
    val symbolicCompleted = result.symbolic == null ||
        result.symbolic.status == UsvmPropertySearchStatus.NO_VIOLATION_REACHED
    val completed = result.stop == PropertyCampaignStop.COMPLETED && result.diagnostic == null &&
        concreteCompleted && symbolicCompleted && result.confirmation == null

    return if (completed) EXIT_SUCCESS else EXIT_INCOMPLETE
}

private const val EXIT_SUCCESS = 0
private const val EXIT_CONFIRMED_VIOLATION = 1
private const val EXIT_INCOMPLETE = 2
