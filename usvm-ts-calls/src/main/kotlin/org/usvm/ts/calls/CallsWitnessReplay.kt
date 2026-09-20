package org.usvm.ts.calls

import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.contains
import java.nio.file.Path

internal data class CallsWitnessSelector(
    val projectId: String,
    val functionId: String,
    val targetId: String,
    val profile: CallsExperimentProfile,
    val seed: Long,
)

internal fun preflightCallsWitness(rawInput: Path, selector: CallsWitnessSelector) {
    loadCallsWitness(rawInput = rawInput, selector = selector)
}

private data class LoadedCallsWitness(
    val raw: CallsValidatedRawResults,
    val row: CallsTargetResult,
    val inputs: List<JsConcreteValue>,
)

private fun loadCallsWitness(rawInput: Path, selector: CallsWitnessSelector): LoadedCallsWitness {
    val raw = CallsRawResultsReader.read(rawInput)
    val row = raw.results.singleOrNull { result -> result.matches(selector) }
        ?: error("Raw results must contain exactly one row for $selector")
    val inputs = row.inputs
    if (inputs == null && row.inputExtracted) {
        error(
            "The selected row is from a historical raw artifact that did not store its extracted witness; " +
                "single-witness replay is unavailable",
        )
    }

    return LoadedCallsWitness(
        raw = raw,
        row = row,
        inputs = requireNotNull(inputs) {
            "The selected row has no extracted witness; single-witness replay is unavailable"
        },
    )
}

internal class CallsWitnessReplayer(
    private val targetReplayer: CallsTargetReplayer,
    private val runtimeToolRevision: String = CallsBuildIdentity.toolRevision,
    private val verifyProjectCheckout: (Path, String) -> Unit = ::verifyCallsGitCheckout,
) {
    fun replay(
        manifest: CallsExperimentManifest,
        manifestDirectory: Path,
        rawInput: Path,
        selector: CallsWitnessSelector,
    ): CallsSourceReplayResult {
        val loaded = loadCallsWitness(rawInput = rawInput, selector = selector)
        val row = loaded.row
        val inputs = loaded.inputs

        verifyExperimentIdentity(manifest = manifest, metadata = loaded.raw.metadata)
        require(runtimeToolRevision == manifest.toolRevision) {
            "Manifest tool revision ${manifest.toolRevision} does not match running build $runtimeToolRevision; " +
                "single-witness replay requires the same clean tool revision"
        }

        val project = manifest.projects.single { project -> project.projectId == selector.projectId }
        require(row.revision == project.revision && row.development == project.development) {
            "Selected raw row project identity does not match the frozen manifest"
        }
        val function = project.functions.single { function -> function.functionId == selector.functionId }
        val target = function.targets.single { target -> target.targetId == selector.targetId }
        require(row.siteId == target.siteId) {
            "Selected raw row target identity does not match the frozen manifest"
        }
        require(inputs.size == function.inputs.size) {
            "Stored witness has ${inputs.size} values, expected ${function.inputs.size}"
        }
        inputs.zip(function.inputs).forEach { (value, input) ->
            require(value in input.domain) {
                "Stored witness value for ${input.name} is outside the frozen input domain"
            }
        }

        val sourceRoot = manifestDirectory.resolve(project.sourceRoot).normalize().toRealPath()
        verifyProjectCheckout(sourceRoot, project.revision)

        return targetReplayer.replay(
            sourceRoots = listOf(sourceRoot),
            entryPoint = function.entryPoint,
            inputs = inputs,
            target = target,
            timeoutMillis = manifest.perTargetBudgetMillis,
        )
    }

    private fun verifyExperimentIdentity(
        manifest: CallsExperimentManifest,
        metadata: CallsRunMetadata,
    ) {
        require(metadata.experimentId == manifest.experimentId) {
            "Raw experiment ID does not match the frozen manifest"
        }
        require(metadata.toolRevision == manifest.toolRevision) {
            "Raw tool revision does not match the frozen manifest"
        }
        require(metadata.nativeFrontendRevision == manifest.nativeFrontendRevision) {
            "Raw native frontend revision does not match the frozen manifest"
        }
        require(metadata.modelSet == manifest.modelSet) {
            "Raw model-set identity does not match the frozen manifest"
        }
        require(metadata.profiles == CallsExperimentProfile.entries) {
            "Raw profiles do not match the frozen experiment contract"
        }
        require(metadata.seeds == manifest.seeds) {
            "Raw seeds do not match the frozen manifest"
        }
        val manifestTargets = manifest.projects.flatMap { project ->
            project.functions.flatMap { function ->
                function.targets.map { target ->
                    CallsRunTargetIdentity(
                        projectId = project.projectId,
                        revision = project.revision,
                        development = project.development,
                        functionId = function.functionId,
                        targetId = target.targetId,
                        siteId = target.siteId,
                    )
                }
            }
        }
        require(metadata.commonEligibleTargets == manifestTargets.size && metadata.targets == manifestTargets) {
            "Raw target matrix does not match the frozen manifest"
        }
    }
}

private fun CallsTargetResult.matches(selector: CallsWitnessSelector): Boolean =
    projectId == selector.projectId &&
        functionId == selector.functionId &&
        targetId == selector.targetId &&
        profile == selector.profile &&
        seed == selector.seed
