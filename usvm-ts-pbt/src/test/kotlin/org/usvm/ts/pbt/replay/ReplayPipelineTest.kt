package org.usvm.ts.pbt.replay

import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.eqq
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.param
import org.jacodb.ets.dsl.program
import org.jacodb.ets.dsl.toBlockCfg
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.toEtsBlockCfg
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.coverage.CoverageTracker
import org.usvm.ts.pbt.external.ArtifactContractCodec
import org.usvm.ts.pbt.external.ArtifactProducer
import org.usvm.ts.pbt.external.ArtifactRunConfig
import org.usvm.ts.pbt.external.ArtifactSourceRange
import org.usvm.ts.pbt.external.ExternalCorpusInputProvider
import org.usvm.ts.pbt.external.ExternalTestCase
import org.usvm.ts.pbt.external.ExternalTestCorpus
import org.usvm.ts.pbt.external.ExternalTestCorpusCodec
import org.usvm.ts.pbt.external.ExternalValue
import org.usvm.ts.pbt.external.NativeCoverageArtifact
import org.usvm.ts.pbt.external.RawRunMeta
import org.usvm.ts.pbt.external.SourceCallableOrigin
import org.usvm.ts.pbt.external.SourceTargetRecord
import org.usvm.ts.pbt.external.TargetManifest
import org.usvm.ts.pbt.external.stableBranchId
import org.usvm.ts.pbt.external.stableMethodId
import org.usvm.ts.pbt.hybrid.PbtPhase
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ReplayPipelineTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `producer label cannot change concrete coverage and mapping counts stay frozen`() {
        val method = branchingMethod()
        val targets = TargetManifest.fromMethods(listOf(method)).methods.single().branches.map { it.branchId }
        val cases = primitiveCases(method, targets)
        val first = fixture("producer-a", "tool-a@1.0", method, cases, listOf("exact", "unmapped"))
        val second = fixture("producer-b", "tool-b@9.4", method, cases, listOf("exact", "unmapped"))

        val firstResult = ReplayPipeline(FakeClock(100)).run(first.inputs, BranchByCaseExecutor(targets))
        val secondResult = ReplayPipeline(FakeClock(100)).run(second.inputs, BranchByCaseExecutor(targets))

        assertEquals(fixedConfirmed(firstResult), fixedConfirmed(secondResult))
        assertEquals(targets.toSet(), fixedConfirmed(firstResult))
        assertEquals(1, firstResult.mappingReport.exact)
        assertEquals(1, firstResult.mappingReport.unmapped)
        assertEquals(2, firstResult.mappingReport.denominatorEdgeCount)
        assertTrue(firstResult.residualTargets.isEmpty())
        assertTrue(secondResult.residualTargets.isEmpty())
    }

    @Test
    fun `cases use generated time then case id and discoveries use incremental fake clock`() {
        val method = branchingMethod()
        val methodId = stableMethodId(method)
        val targets = TargetManifest.fromMethods(listOf(method)).methods.single().branches.map { it.branchId }
        val cases = listOf(
            case("z", methodId, generatedAtMs = 20),
            case("b", methodId, generatedAtMs = 10),
            case("a", methodId, generatedAtMs = 10),
        )
        val fixture = fixture("ordering", "ordering@1", method, cases)
        val clock = FakeClock(100)
        val executor = RecordingExecutor(
            clock = clock,
            covered = mapOf("a" to setOf(targets[0]), "b" to setOf(targets[1])),
            durations = mapOf("a" to 5L, "b" to 5L, "z" to 5L),
        )

        val result = ReplayPipeline(clock).run(fixture.inputs, executor)

        assertEquals(listOf("a", "b", "z"), executor.order)
        assertEquals(
            listOf("a" to 105L, "b" to 110L),
            result.replayReport.filter { it.outcome == ReplayOutcome.CONFIRMED }
                .map { it.caseId to checkNotNull(it.discoveredAtMs) },
        )
        assertEquals(0.98925, result.deadlineReport.coverageAuc, 1e-12)
        assertTrue(result.validationReport.valid)
    }

    @Test
    fun `late replay is diagnostic only and excluded from fixed coverage residual and AUC`() {
        val method = branchingMethod()
        val methodId = stableMethodId(method)
        val targets = TargetManifest.fromMethods(listOf(method)).methods.single().branches.map { it.branchId }
        val cases = listOf(case("first", methodId, 100), case("late", methodId, 200))
        val fixture = fixture("late", "late-tool@1", method, cases, rawTotalMs = 9_000)
        val clock = FakeClock(9_000)
        val executor = RecordingExecutor(
            clock = clock,
            covered = mapOf("first" to setOf(targets[0]), "late" to setOf(targets[1])),
            durations = mapOf("first" to 500L, "late" to 1_000L),
        )

        val result = ReplayPipeline(clock).run(fixture.inputs, executor)

        assertEquals(setOf(targets[0]), fixedConfirmed(result))
        assertEquals(listOf(targets[1]), result.residualTargets.map { it.branchId })
        assertEquals(1, result.deadlineReport.fixedBudgetConfirmedEdgeCount)
        assertEquals(2, result.deadlineReport.diagnosticConfirmedEdgeCount)
        assertEquals(1, result.deadlineReport.lateConfirmedEdgeCount)
        assertEquals(1, result.deadlineReport.lateCaseOutcomeCount)
        assertEquals(500, result.deadlineReport.overBudgetMs)
        assertEquals(0.5, result.deadlineReport.fixedBudgetCoverage, 1e-12)
        assertEquals(0.025, result.deadlineReport.coverageAuc, 1e-12)
        val late = result.replayReport.single { it.outcome == ReplayOutcome.CONFIRMED_LATE }
        assertFalse(late.fixedBudgetEligible)
        assertEquals(10_500, late.discoveredAtMs)
    }

    @Test
    fun `reject reasons preserve funnel invariants and malformed raw run never replays`() {
        val method = branchingMethod()
        val methodId = stableMethodId(method)
        val targets = TargetManifest.fromMethods(listOf(method)).methods.single().branches.map { it.branchId }
        val cases = listOf(
            case("outside", "other.ts::%dflt::other/0", 1),
            case("executor-reject", methodId, 2),
            case("ok", methodId, 3),
        )
        val fixture = fixture("rejects", "reject-tool@1", method, cases)
        val clock = FakeClock(100)
        val executor = RecordingExecutor(
            clock = clock,
            covered = mapOf("ok" to setOf(targets[0])),
            rejected = setOf("executor-reject"),
        )

        val result = ReplayPipeline(clock).run(fixture.inputs, executor)

        assertEquals(3, result.deadlineReport.importedCaseCount)
        assertEquals(2, result.deadlineReport.rejectedCaseCount)
        assertEquals(1, result.deadlineReport.replayExecutedCaseCount)
        assertEquals(1, result.deadlineReport.confirmedCaseCount)
        assertTrue(result.deadlineReport.invariants.confirmedSubsetReplayExecuted)
        assertTrue(result.deadlineReport.invariants.replayExecutedSubsetImportedMinusRejected)
        assertTrue(result.deadlineReport.invariants.residualEqualsDenominatorMinusConfirmed)
        assertTrue(result.replayReport.any { it.reasonCode == ReplayReasonCode.METHOD_OUTSIDE_DENOMINATOR })
        assertTrue(result.replayReport.any { it.reasonCode == ReplayReasonCode.EXECUTOR_REJECTED })
        assertEquals(listOf("executor-reject", "ok"), executor.order)

        val invalid = fixture("invalid-raw", "bad-tool@1", method, primitiveCases(method, targets))
        invalid.rawRun.resolve("adapter-owned-extra.json").writeText("{}")
        val failure = assertThrows(ReplayInputException::class.java) {
            ReplayPipeline(FakeClock(100)).run(invalid.inputs, BranchByCaseExecutor(targets))
        }
        assertTrue(failure.issues.any { it.code == "unexpected_artifact" })
    }

    @Test
    fun `dynamic probe is terminal in final capability report and validator rejects unknown`() {
        val method = branchingMethod()
        val targets = TargetManifest.fromMethods(listOf(method)).methods.single().branches.map { it.branchId }
        val fixture = fixture("capabilities", "cap-tool@1", method, emptyList(), listOf("oneToMany", "ambiguous"))
        val provider = ReplayCapabilityProvider { target ->
            if (target.branchId == targets[0]) {
                ReplayCapabilityAssessment(
                    staticLabel = StaticCapabilityLabel.NEEDS_DYNAMIC_PROBE,
                    staticReasonCode = "static_scan_inconclusive",
                    dynamicProbeOutcome = DynamicProbeOutcome.SUPPORTED,
                    dynamicProbeReasonCode = "probe_replayed_fixture",
                    terminalStatus = TerminalCapabilityStatus.SUPPORTED,
                    terminalReasonCode = "probe_replayed_fixture",
                )
            } else {
                ReplayCapabilityAssessment(
                    staticLabel = StaticCapabilityLabel.UNSUPPORTED,
                    staticReasonCode = "iterator_not_modeled",
                    terminalStatus = TerminalCapabilityStatus.UNSUPPORTED,
                    terminalReasonCode = "iterator_not_modeled",
                )
            }
        }

        val result = ReplayPipeline(FakeClock(100)).run(fixture.inputs, BranchByCaseExecutor(targets), provider)

        assertEquals(1, result.mappingReport.oneToMany)
        assertEquals(1, result.mappingReport.ambiguous)
        assertTrue(result.capabilityReport.all { it.terminalStatus != "unknown" })
        assertEquals(DynamicProbeOutcome.SUPPORTED, result.capabilityReport.first().dynamicProbeOutcome)
        assertEquals(2, result.residualTargets.size)

        val nonTerminal = ReplayCapabilityProvider {
            ReplayCapabilityAssessment(
                staticLabel = StaticCapabilityLabel.NEEDS_DYNAMIC_PROBE,
                staticReasonCode = "probe_required",
            )
        }
        val failure = assertThrows(ReplayInputException::class.java) {
            ReplayPipeline(FakeClock(100)).run(
                fixture("non-terminal", "cap-tool@1", method, emptyList()).inputs,
                BranchByCaseExecutor(targets),
                nonTerminal,
            )
        }
        assertTrue(failure.issues.any { it.code == "capability_non_terminal" })

        val capabilityPath = fixture.output.resolve(CAPABILITY_REPORT_FILE)
        capabilityPath.writeText(
            capabilityPath.readText().replaceFirst(
                "\"terminalStatus\":\"supported\"",
                "\"terminalStatus\":\"unknown\"",
            ),
        )
        val validation = ReplayArtifactValidator.validateOutputDirectory(fixture.output)
        assertFalse(validation.valid)
        assertTrue(validation.issues.any { it.code == "capability_non_terminal" })
    }

    @Test
    fun `production EtsIR executor matches old in-process replay on primitive golden corpus`() {
        val method = branchingMethod()
        val manifest = TargetManifest.fromMethods(listOf(method))
        val targets = manifest.methods.single().branches.map { it.branchId }
        val cases = primitiveCases(method, targets)
        val fixture = fixture("parity", "golden@1", method, cases)
        val scene = EtsScene(emptyList())

        val result = ReplayPipeline(FakeClock(100)).run(
            fixture.inputs,
            EtsIrReplayCaseExecutor(scene, listOf(method)),
        )

        val oldCoverage = CoverageTracker(listOf(method))
        PbtPhase(
            scene = scene,
            method = method,
            coverage = oldCoverage,
            inputProviders = listOf(
                ExternalCorpusInputProvider.fromCorpus(ExternalTestCorpus(producer = "golden@1", cases = cases)),
            ),
            internalGeneration = false,
            shrink = false,
        ).run()
        val oldConfirmed = oldCoverage.allBranches.filter(oldCoverage::isCovered).map {
            stableBranchId(method, it.ifStmt, it.successor)
        }.toSet()

        assertTrue(result.deadlineReport.productionExecutor)
        assertEquals("etsir-concrete", result.deadlineReport.executorId)
        assertEquals(oldConfirmed, fixedConfirmed(result))
        assertEquals(targets.toSet(), oldConfirmed)
    }

    @Test
    fun `production decoder rejects lossy structured values with a stable reason`() {
        val method = branchingMethod()
        val rejected = EtsIrReplayCaseExecutor(EtsScene(emptyList()), listOf(method)).execute(
            ExternalTestCase(
                id = "hole",
                methodId = stableMethodId(method),
                generatedAtMs = 0,
                seed = "17",
                arguments = listOf(
                    ExternalValue(
                        kind = "array",
                        elements = listOf(ExternalValue("hole")),
                    ),
                ),
            ),
        )

        assertTrue(rejected is ReplayCaseExecution.Rejected)
        assertEquals(
            ReplayReasonCode.INPUT_UNREPRESENTABLE,
            (rejected as ReplayCaseExecution.Rejected).reasonCode,
        )
    }

    @Test
    fun `CLI makes fixture executor explicit and validates its five outputs`() {
        val method = branchingMethod()
        val methodId = stableMethodId(method)
        val target = TargetManifest.fromMethods(listOf(method)).methods.single().branches.first().branchId
        val case = case(
            id = "fixture-case",
            methodId = methodId,
            generatedAtMs = 10,
            metadata = mapOf("fixtureCoveredBranchIds" to target),
        )
        val fixture = fixture("cli", "cli-tool@1", method, listOf(case))
        val baseArgs = cliArgs(fixture)

        val deniedError = StringBuilder()
        assertEquals(64, ReplayCli.run(baseArgs, StringBuilder(), deniedError, providers = emptyList()))
        assertTrue(deniedError.contains("test-only"))

        val stdout = StringBuilder()
        assertEquals(
            0,
            ReplayCli.run(baseArgs + "--allow-fixture-executor", stdout, StringBuilder(), providers = emptyList()),
        )
        assertTrue(stdout.contains("\"productionExecutor\":false"))
        assertEquals(
            0,
            ReplayCli.run(
                arrayOf("validate-output", "--out-dir", fixture.output.toString()),
                StringBuilder(),
                StringBuilder(),
                providers = emptyList(),
            ),
        )
    }

    @Test
    fun `deadline grace formula has fixed one and five second clamps`() {
        assertEquals(1_000, ReplayDeadlinePolicy.fromBudget(2_000).graceMs)
        assertEquals(2_000, ReplayDeadlinePolicy.fromBudget(20_000).graceMs)
        assertEquals(5_000, ReplayDeadlinePolicy.fromBudget(100_000).graceMs)
        assertEquals(18_000, ReplayDeadlinePolicy.fromBudget(20_000).explorationDeadlineMs)
    }

    private fun fixedConfirmed(result: ReplayPipelineResult): Set<String> = result.replayReport
        .filter { it.outcome == ReplayOutcome.CONFIRMED }
        .mapNotNull { it.branchId }
        .toSet()

    private fun primitiveCases(method: EtsMethod, targets: List<String>): List<ExternalTestCase> = listOf(
        case("positive", stableMethodId(method), 10, 7.0, mapOf("expected" to targets[0])),
        case("negative", stableMethodId(method), 20, 0.0, mapOf("expected" to targets[1])),
    )

    private fun case(
        id: String,
        methodId: String,
        generatedAtMs: Long,
        number: Double = 0.0,
        metadata: Map<String, String> = emptyMap(),
    ): ExternalTestCase = ExternalTestCase(
        id = id,
        methodId = methodId,
        generatedAtMs = generatedAtMs,
        seed = "17",
        arguments = listOf(ExternalValue("number", value = number.toString())),
        metadata = metadata,
    )

    private fun fixture(
        name: String,
        producerLabel: String,
        method: EtsMethod,
        cases: List<ExternalTestCase>,
        mappingStatuses: List<String> = listOf("exact", "exact"),
        rawTotalMs: Long = 100,
        budgetMs: Long = 10_000,
    ): Fixture {
        val root = tempDir.resolve(name).createDirectories()
        val raw = root.resolve("raw").createDirectories()
        val output = root.resolve("replay-output")
        val separator = producerLabel.lastIndexOf('@')
        require(separator > 0)
        val producer = ArtifactProducer(
            name = producerLabel.substring(0, separator),
            version = producerLabel.substring(separator + 1),
            commit = "fixture-commit",
        )
        ExternalTestCorpusCodec.write(
            raw.resolve("corpus.etc.jsonl"),
            ExternalTestCorpus(producer = producerLabel, cases = cases),
        )
        raw.resolve("native-coverage.json").writeText(
            ArtifactContractCodec.encodeNativeCoverage(
                NativeCoverageArtifact(producer = producer, claims = emptyList()),
            ),
        )
        val runId = "run-$name"
        raw.resolve("run-meta.json").writeText(
            ArtifactContractCodec.encodeRunMeta(
                RawRunMeta(
                    runId = runId,
                    producer = producer,
                    startupMs = minOf(10, rawTotalMs),
                    generationMs = minOf(20, (rawTotalMs - minOf(10, rawTotalMs)).coerceAtLeast(0)),
                    exportMs = 0,
                    totalMs = rawTotalMs,
                    commits = mapOf("usvm" to "fixture-usvm"),
                    exitStatus = "success",
                    timedOut = false,
                    logTruncated = false,
                    overBudgetMs = (rawTotalMs - budgetMs).coerceAtLeast(0),
                ),
            ),
        )
        raw.resolve("stderr.log").writeText("")

        val policy = ReplayDeadlinePolicy.fromBudget(budgetMs)
        val runConfig = root.resolve("run-config.json")
        runConfig.writeText(
            ArtifactContractCodec.encodeRunConfig(
                ArtifactRunConfig(
                    runId = runId,
                    adapter = producer,
                    seed = 17,
                    budgetMs = budgetMs,
                    exportReplayGraceMs = policy.graceMs,
                    explorationDeadlineMs = policy.explorationDeadlineMs,
                    hardResultDeadlineMs = policy.hardResultDeadlineMs,
                    cacheMode = "cold",
                    versions = mapOf("jdk" to "23"),
                    commits = mapOf("usvm" to "fixture-usvm"),
                ),
            ),
        )

        val manifest = TargetManifest.fromMethods(listOf(method))
        val targetManifest = root.resolve("target-manifest.json")
        targetManifest.writeText(TargetManifest.encode(manifest))
        val targetMethod = manifest.methods.single()
        require(mappingStatuses.size == targetMethod.branches.size)
        val sourceRecords = targetMethod.branches.mapIndexed { index, branch ->
            SourceTargetRecord(
                methodId = targetMethod.methodId,
                branchId = branch.branchId,
                stmtIndex = branch.ifStmtIndex,
                successorStmtIndex = branch.successorStmtIndex,
                successorOrdinal = branch.successorOrdinal,
                tsSourceRange = ArtifactSourceRange(
                    fileName = "src/f.ts",
                    startOffset = 0,
                    endOffset = 1,
                    startLine = 0,
                    startColumn = 0,
                    endLine = 0,
                    endColumn = 1,
                ),
                sourceOrigin = SourceCallableOrigin("src/f.ts", "f", "free"),
                mappingStatus = mappingStatuses[index],
            )
        }
        val sourceTargets = root.resolve("source-targets.jsonl")
        sourceTargets.writeText(ArtifactContractCodec.encodeSourceTargets(sourceRecords))
        val methodIds = root.resolve("method-ids.txt")
        methodIds.writeText(targetMethod.methodId + "\n")
        return Fixture(
            rawRun = raw,
            output = output,
            inputs = ReplayInputs(raw, runConfig, targetManifest, sourceTargets, methodIds, output),
        )
    }

    private fun cliArgs(fixture: Fixture): Array<String> = arrayOf(
        "run",
        "--raw-run",
        fixture.inputs.rawRunDirectory.toString(),
        "--run-config",
        fixture.inputs.runConfig.toString(),
        "--target-manifest",
        fixture.inputs.targetManifest.toString(),
        "--source-targets",
        fixture.inputs.sourceTargets.toString(),
        "--method-ids",
        fixture.inputs.methodIds.toString(),
        "--out-dir",
        fixture.output.toString(),
        "--executor",
        FixtureMetadataReplayExecutor.ID,
    )

    private fun branchingMethod(): EtsMethod {
        val program = program {
            val x = local("x")
            assign(x, param(0))
            ifStmt(eqq(x, const(7.0))) {
                ret(const(1.0))
            }
            ret(const(0.0))
        }
        return EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = EtsClassSignature(
                    name = "%dflt",
                    file = EtsFileSignature(projectName = "p", fileName = "src/f.ts"),
                ),
                name = "f",
                parameters = listOf(EtsMethodParameter(0, "x", EtsUnknownType)),
                returnType = EtsUnknownType,
            ),
        ).also { method ->
            method.body.cfg = program.toBlockCfg().toEtsBlockCfg(method)
        }
    }

    private data class Fixture(
        val rawRun: Path,
        val output: Path,
        val inputs: ReplayInputs,
    )

    private class FakeClock(var current: Long) : ReplayClock {
        override fun elapsedMs(): Long = current
        fun advance(milliseconds: Long) {
            current += milliseconds
        }
    }

    private class BranchByCaseExecutor(private val targets: List<String>) : ReplayCaseExecutor {
        override val id: String = "branch-by-case-fixture"
        override val isProduction: Boolean = false

        override fun execute(case: ExternalTestCase): ReplayCaseExecution {
            val branch = when (case.id) {
                "positive" -> targets[0]
                "negative" -> targets[1]
                else -> case.metadata["expected"]
            }
            return ReplayCaseExecution.Executed(
                coveredBranchIds = branch?.let(::setOf).orEmpty(),
                reasonCode = ReplayReasonCode.REPLAY_RETURNED,
            )
        }
    }

    private class RecordingExecutor(
        private val clock: FakeClock,
        private val covered: Map<String, Set<String>> = emptyMap(),
        private val durations: Map<String, Long> = emptyMap(),
        private val rejected: Set<String> = emptySet(),
    ) : ReplayCaseExecutor {
        override val id: String = "recording-fixture"
        override val isProduction: Boolean = false
        val order = mutableListOf<String>()

        override fun execute(case: ExternalTestCase): ReplayCaseExecution {
            order += case.id
            clock.advance(durations[case.id] ?: 0)
            if (case.id in rejected) {
                return ReplayCaseExecution.Rejected(
                    ReplayReasonCode.EXECUTOR_REJECTED,
                    "fixture reject",
                )
            }
            return ReplayCaseExecution.Executed(
                coveredBranchIds = covered[case.id].orEmpty(),
                reasonCode = ReplayReasonCode.REPLAY_RETURNED,
            )
        }
    }
}
