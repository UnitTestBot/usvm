package org.usvm.ts.calls

import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CallsCoverageExperimentTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `two symbolic branches replay to distinct original source if arms`() {
        val fixture = fixture(
            source = """
                export function classify(flag: boolean): boolean {
                  if (flag) {
                    return true;
                  } else {
                    return false;
                  }
                }
            """.trimIndent(),
            exportName = "classify",
            inputs = listOf(PropertyInput(name = "flag", domain = BooleanDomain)),
        )
        val emitted = mutableListOf<CallsCoverageCandidate>()

        val search = CurrentTsCallsCoverageEngine(testSourceEngine()).search(
            request = coverageRequest(fixture, onCandidate = emitted::add),
        )

        assertEquals(CallsCoverageSearchStatus.EXHAUSTED, search.status, search.toString())
        assertEquals(search.candidates, emitted)
        assertEquals(2, search.candidates.map { it.inputs }.distinct().size, search.toString())
        assertTrue(search.candidates.all { candidate -> candidate.emittedAtMillis <= search.searchElapsedMillis })
        assertTrue(search.executedSteps > 0)
        assertTrue(search.stepsWithinBudget in 1..search.executedSteps)
        assertTrue(search.candidates.all { candidate -> candidate.emittedAtStep in 1..search.executedSteps })
        assertTrue(search.machineSetupElapsedMillis >= 0)
        assertTrue(search.machineTeardownElapsedMillis >= 0)

        val (universe, replayed) = OriginalTypeScriptCoverageReplayer(
            sourceRoot = fixture.sourceRoot,
            function = fixture.function,
        ).use { replayer ->
            val universe = replayer.probeUniverse(timeoutMillis = 20_000L)
            val replayed = search.candidates.map { candidate ->
                replayer.replay(inputs = candidate.inputs, timeoutMillis = 20_000L)
            }
            universe to replayed
        }
        assertEquals(2, replayed.flatMap { it.coveredIfArmKeys }.toSet().size, replayed.toString())
        assertTrue(replayed.all { replay -> replay.coveredStatementKeys.isNotEmpty() })
        assertTrue(replayed.all { replay -> replay.coveredStatementKeys.size < universe.supportedStatementKeys.size })
        assertTrue(universe.supportedStatementKeys.containsAll(replayed.flatMap { it.coveredStatementKeys }))
        assertEquals(universe.supportedStatementKeys, replayed.flatMap { it.coveredStatementKeys }.toSet())
        assertTrue(universe.probeElapsedMillis >= 0)
    }

    @Test
    fun `coverage universe has a denominator without invoking the entry function`() {
        val fixture = fixture(
            source = """
                export function neverRun(flag: boolean): boolean {
                  if (flag) throw new Error('The probe must not invoke this function');
                  return false;
                }
            """.trimIndent(),
            exportName = "neverRun",
            inputs = listOf(PropertyInput(name = "flag", domain = BooleanDomain)),
        )

        val universe = OriginalTypeScriptCoverageReplayer(
            sourceRoot = fixture.sourceRoot,
            function = fixture.function,
        ).use { replayer -> replayer.probeUniverse(timeoutMillis = 20_000L) }

        assertTrue(universe.supportedStatementKeys.isNotEmpty())
        assertTrue(universe.supportedStatementKeys.size >= 4, universe.toString())
        assertEquals(setOf("Fixture.ts"), universe.sourceFiles)
    }

    @Test
    fun `coverage universe includes the complete local runtime source closure`() {
        val fixture = fixture(
            source = """
                import { choose } from './Helper';
                export function classify(flag: boolean): number { return choose(flag); }
            """.trimIndent(),
            exportName = "classify",
            inputs = listOf(PropertyInput(name = "flag", domain = BooleanDomain)),
            additionalSources = mapOf(
                "Helper.ts" to """
                    export function choose(flag: boolean): number {
                      if (flag) return 1;
                      return 2;
                    }
                """.trimIndent(),
            ),
        )

        val universe = OriginalTypeScriptCoverageReplayer(
            sourceRoot = fixture.sourceRoot,
            function = fixture.function,
        ).use { replayer -> replayer.probeUniverse(timeoutMillis = 20_000L) }

        assertEquals(setOf("Fixture.ts", "Helper.ts"), universe.sourceFiles)
        assertTrue(universe.supportedStatementKeys.any { key -> key.startsWith("Helper.ts:") })
    }

    @Test
    fun `coverage replay preserves an isolated UTF-16 surrogate`() {
        val fixture = fixture(
            source = "export function lengthOf(value: string): number { return value.length; }",
            exportName = "lengthOf",
            inputs = listOf(PropertyInput(name = "value", domain = StringDomain(maxLength = 1))),
        )

        val replay = OriginalTypeScriptCoverageReplayer(
            sourceRoot = fixture.sourceRoot,
            function = fixture.function,
        ).use { replayer ->
            replayer.replay(
                inputs = listOf(JsConcreteValue.String("\uD800")),
                timeoutMillis = 20_000L,
            )
        }

        assertEquals(CallsCoverageCompletion.RETURNED, replay.completion)
        assertTrue(replay.coveredStatementKeys.isNotEmpty())
    }

    @Test
    fun `coverage replay accepts an entry function with no inputs`() {
        val fixture = fixture(
            source = "export function constant(): number { return 42; }",
            exportName = "constant",
            inputs = emptyList(),
        )

        val replay = OriginalTypeScriptCoverageReplayer(
            sourceRoot = fixture.sourceRoot,
            function = fixture.function,
        ).use { replayer ->
            replayer.replay(inputs = emptyList(), timeoutMillis = 20_000L)
        }

        assertEquals(CallsCoverageCompletion.RETURNED, replay.completion)
        assertTrue(replay.coveredStatementKeys.isNotEmpty())
    }

    private fun coverageRequest(
        fixture: CoverageFixture,
        onCandidate: (CallsCoverageCandidate) -> Unit,
    ) = CallsCoverageSearchRequest(
        sourceRoot = fixture.sourceRoot,
        project = fixture.project,
        function = fixture.function,
        profile = CallsExperimentProfile.EMPTY_STOP,
        frozenModelIds = emptySet(),
        expectedNativeFrontendRevision = "bundled:test",
        seed = 17,
        budget = 10.seconds,
        solverQueryLimit = 2.seconds,
        candidateCap = 8,
        onCandidate = onCandidate,
    )

    private fun testSourceEngine() = CurrentTsCallsSymbolicEngine(
        environment = emptyMap<String, String>()::get,
        bundledNativeFrontendRevision = "bundled:test",
    )

    private fun fixture(
        source: String,
        exportName: String,
        inputs: List<PropertyInput>,
        additionalSources: Map<String, String> = emptyMap(),
    ): CoverageFixture {
        val sourceRoot = Files.createDirectory(directory.resolve(exportName))
        Files.writeString(sourceRoot.resolve("Fixture.ts"), source)
        additionalSources.forEach { (name, contents) -> Files.writeString(sourceRoot.resolve(name), contents) }
        runGit(sourceRoot, "init")
        runGit(sourceRoot, "config", "user.name", "USVM Tests")
        runGit(sourceRoot, "config", "user.email", "usvm@example.test")
        runGit(sourceRoot, "add", ".")
        runGit(sourceRoot, "commit", "-m", "fixture")
        val revision = runGit(sourceRoot, "rev-parse", "HEAD").trim()
        val function = CallsFunctionCase(
            functionId = exportName,
            sourceFile = "Fixture.ts",
            entryPoint = TypeScriptEntryPoint(module = "Fixture.ts", exportName = exportName),
            inputs = inputs,
            targets = emptyList(),
        )
        val project = CallsProjectCase(
            projectId = exportName,
            revision = revision,
            sourceRoot = ".",
            development = true,
            functions = listOf(function),
        )

        return CoverageFixture(sourceRoot = sourceRoot, project = project, function = function)
    }

    private fun runGit(directory: Path, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git", "-C", directory.toString()) + arguments)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "Git ${arguments.joinToString()} failed: $output" }
        return output
    }

    private data class CoverageFixture(
        val sourceRoot: Path,
        val project: CallsProjectCase,
        val function: CallsFunctionCase,
    )
}
