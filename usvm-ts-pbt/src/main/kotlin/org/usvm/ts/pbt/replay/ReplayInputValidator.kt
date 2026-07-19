package org.usvm.ts.pbt.replay

import org.usvm.ts.pbt.external.ArtifactContractCodec
import org.usvm.ts.pbt.external.ArtifactValidationReport
import org.usvm.ts.pbt.external.ArtifactValidator
import org.usvm.ts.pbt.external.ExternalTestCorpusCodec
import org.usvm.ts.pbt.external.TargetManifest
import kotlin.io.path.readLines
import kotlin.io.path.readText

internal object ReplayInputValidator {
    fun validate(inputs: ReplayInputs): ValidatedReplayInput {
        val issues = mutableListOf<ReplayInputIssue>()
        collect(ArtifactValidator.validateRawRunDirectory(inputs.rawRunDirectory), issues)
        collect(ArtifactValidator.validateRunConfig(inputs.runConfig), issues)
        collect(ArtifactValidator.validateTargetManifest(inputs.targetManifest), issues)
        collect(ArtifactValidator.validateSourceTargets(inputs.sourceTargets), issues)
        collect(ArtifactValidator.validateMethodIds(inputs.methodIds), issues)

        val raw = inputs.rawRunDirectory.toAbsolutePath().normalize()
        val output = inputs.outputDirectory.toAbsolutePath().normalize()
        if (output == raw || output.startsWith(raw) || raw.startsWith(output)) {
            issues += ReplayInputIssue(
                artifact = "output-directory",
                path = "$",
                code = "overlaps_raw_run",
                message = "output directory must be separate from the immutable raw run directory",
            )
        }
        if (issues.isNotEmpty()) throw ReplayInputException(issues)

        val runConfig = ArtifactContractCodec.decodeRunConfig(inputs.runConfig.readText(), inputs.runConfig.toString())
        val runMetaPath = inputs.rawRunDirectory.resolve("run-meta.json")
        val runMeta = ArtifactContractCodec.decodeRunMeta(runMetaPath.readText(), runMetaPath.toString())
        val corpusPath = inputs.rawRunDirectory.resolve("corpus.etc.jsonl")
        val corpus = ExternalTestCorpusCodec.read(corpusPath)
        val manifest = TargetManifest.decode(inputs.targetManifest.readText(), inputs.targetManifest.toString())
        val sourceTargets = ArtifactContractCodec.decodeSourceTargets(
            inputs.sourceTargets.readText(),
            inputs.sourceTargets.toString(),
        )
        val methodIds = inputs.methodIds.readLines().map(String::trim).filter(String::isNotEmpty)

        if (runConfig.runId != runMeta.runId) {
            issues += crossIssue("run-config", "$.runId", "run_id_mismatch", "runId differs from raw run-meta")
        }
        if (runConfig.adapter != runMeta.producer) {
            issues += crossIssue(
                "run-config",
                "$.adapter",
                "producer_mismatch",
                "adapter identity differs from raw run-meta producer",
            )
        }

        val methodsById = manifest.methods.associateBy { it.methodId }
        val selectedMethods = methodIds.mapNotNull { methodId ->
            methodsById[methodId] ?: run {
                issues += crossIssue(
                    "method-ids",
                    "$[$methodId]",
                    "unknown_method",
                    "method does not exist in target-manifest",
                )
                null
            }
        }

        val sourceByMethod = sourceTargets.groupBy { it.methodId }
        sourceTargets.forEachIndexed { index, target ->
            val method = methodsById[target.methodId]
            if (method == null) {
                issues += crossIssue(
                    "source-targets",
                    "$[${index + 1}].methodId",
                    "unknown_method",
                    "source target method does not exist in target-manifest",
                )
                return@forEachIndexed
            }
            val branch = method.branches.singleOrNull { it.branchId == target.branchId }
            if (branch == null) {
                issues += crossIssue(
                    "source-targets",
                    "$[${index + 1}].branchId",
                    "unknown_branch",
                    "source target branch does not exist in target-manifest method",
                )
                return@forEachIndexed
            }
            if (
                target.stmtIndex != branch.ifStmtIndex ||
                target.successorStmtIndex != branch.successorStmtIndex ||
                target.successorOrdinal != branch.successorOrdinal
            ) {
                issues += crossIssue(
                    "source-targets",
                    "$[${index + 1}]",
                    "branch_shape_mismatch",
                    "EtsIR statement/successor indices differ from target-manifest",
                )
            }
        }

        selectedMethods.forEach { method ->
            val manifestBranches = method.branches.map { it.branchId }.toSet()
            val mappedBranches = sourceByMethod[method.methodId].orEmpty().map { it.branchId }.toSet()
            (manifestBranches - mappedBranches).sorted().forEach { branchId ->
                issues += crossIssue(
                    "source-targets",
                    "$[${method.methodId}]",
                    "missing_denominator_edge",
                    "selected manifest branch '$branchId' has no source-target record",
                )
            }
            (mappedBranches - manifestBranches).sorted().forEach { branchId ->
                issues += crossIssue(
                    "source-targets",
                    "$[${method.methodId}]",
                    "extra_denominator_edge",
                    "source-target branch '$branchId' is absent from the selected manifest method",
                )
            }
        }

        if (issues.isNotEmpty()) throw ReplayInputException(issues)

        val selectedIds = methodIds.toSet()
        return ValidatedReplayInput(
            inputs = inputs,
            runConfig = runConfig,
            runMeta = runMeta,
            corpus = corpus,
            targetManifest = manifest,
            denominatorMethods = selectedMethods,
            denominatorTargets = sourceTargets
                .filter { it.methodId in selectedIds }
                .sortedWith(compareBy({ it.methodId }, { it.branchId })),
        )
    }

    private fun collect(report: ArtifactValidationReport, issues: MutableList<ReplayInputIssue>) {
        report.issues.forEach { issue ->
            issues += ReplayInputIssue(
                artifact = report.artifact,
                path = issue.path,
                code = issue.code,
                message = issue.message,
            )
        }
    }

    private fun crossIssue(artifact: String, path: String, code: String, message: String) = ReplayInputIssue(
        artifact = artifact,
        path = path,
        code = code,
        message = message,
    )
}
