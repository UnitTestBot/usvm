package org.usvm.ts.calls

import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
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
    fun `coverage search honors either explicitly configured solver`() {
        val fixture = fixture(
            source = """
                export function classify(flag: boolean): number {
                  if (flag) return 1;
                  return 2;
                }
            """.trimIndent(),
            exportName = "classify",
            inputs = listOf(PropertyInput(name = "flag", domain = BooleanDomain)),
        )
        for (solverType in listOf(SolverType.YICES, SolverType.Z3)) {
            val emitted = mutableListOf<CallsCoverageCandidate>()

            val search = CurrentTsCallsCoverageEngine(testSourceEngine()).search(
                request = coverageRequest(fixture, onCandidate = emitted::add, solverType = solverType),
            )

            assertEquals(CallsCoverageSearchStatus.EXHAUSTED, search.status, search.toString())
            assertEquals(2, search.candidates.size)
            assertEquals(search.candidates, emitted)
        }
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
    fun `coverage replay records array input before mutation`() {
        val fixture = fixture(
            source = "export function replace(values: number[]): number { values[0] = 2; return values[0]; }",
            exportName = "replace",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(element = NumberDomain(), minLength = 1, maxLength = 1),
                ),
            ),
        )
        val inputs = listOf(JsConcreteValue.Array(listOf(JsConcreteValue.number(1.0))))

        val replay = OriginalTypeScriptCoverageReplayer(
            sourceRoot = fixture.sourceRoot,
            function = fixture.function,
        ).use { replayer -> replayer.replay(inputs = inputs, timeoutMillis = 20_000L) }

        assertEquals(CallsCoverageCompletion.RETURNED, replay.completion)
        assertTrue(replay.coveredStatementKeys.isNotEmpty())
    }

    @Test
    fun `coverage observation does not invoke inherited protocol serializers`() {
        val fixture = fixture(
            source = """
                export function replace(values: number[]): number {
                  Object.defineProperty(Object.prototype, 'toJSON', {
                    configurable: true,
                    value() { throw new Error('Object serializer must not run'); },
                  });
                  Object.defineProperty(Array.prototype, 'toJSON', {
                    configurable: true,
                    value() { throw new Error('Array serializer must not run'); },
                  });
                  values[0] = 2;
                  return values[0];
                }
            """.trimIndent(),
            exportName = "replace",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(element = NumberDomain(), minLength = 1, maxLength = 1),
                ),
            ),
        )
        val inputs = listOf(JsConcreteValue.Array(listOf(JsConcreteValue.number(1.0))))

        val replay = OriginalTypeScriptCoverageReplayer(
            sourceRoot = fixture.sourceRoot,
            function = fixture.function,
        ).use { replayer -> replayer.replay(inputs = inputs, timeoutMillis = 20_000L) }

        assertEquals(CallsCoverageCompletion.RETURNED, replay.completion)
        assertTrue(replay.coveredStatementKeys.isNotEmpty(), replay.toString())
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

    @Test
    fun `coverage search reports the concrete runtime limitation reason`() {
        val fixture = fixture(
            source = """
                export function writesNamedProperty(value: number): boolean {
                  const values = [1];
                  values[0.5] = value;
                  return true;
                }
            """.trimIndent(),
            exportName = "writesNamedProperty",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
        )

        val search = CurrentTsCallsCoverageEngine(testSourceEngine()).search(
            request = coverageRequest(fixture, onCandidate = {}),
        )

        assertTrue(search.runtimeLimited, search.toString())
        assertTrue("ARRAY_NAMED_PROPERTY_WRITE" in search.runtimeLimitationReasons, search.toString())
    }

    @Test
    fun `coverage replay compares the object before the source mutates it`() {
        val fixture = fixture(
            source = """
                export function increment(input: { score: number }): number {
                  input.score += 1;
                  return input.score;
                }
            """.trimIndent(),
            exportName = "increment",
            inputs = listOf(
                PropertyInput(
                    name = "input",
                    domain = ObjectDomain(mapOf("score" to NumberDomain())),
                ),
            ),
        )
        val inputs = listOf(JsConcreteValue.Object(mapOf("score" to JsConcreteValue.number(3.0))))

        val replay = OriginalTypeScriptCoverageReplayer(
            sourceRoot = fixture.sourceRoot,
            function = fixture.function,
        ).use { replayer -> replayer.replay(inputs = inputs, timeoutMillis = 20_000L) }

        assertEquals(CallsCoverageCompletion.RETURNED, replay.completion)
        assertTrue(replay.coveredStatementKeys.isNotEmpty())
    }

    @Test
    fun `class novelty retains private helper candidates in every profile`() {
        val fixture = fixture(
            source = """
                export class Covered {
                  static run(flag: boolean): number { return Covered.choose(flag); }
                  private static choose(flag: boolean): number {
                    if (flag) { return 1; }
                    return 2;
                  }
                }
                export function harness(flag: boolean): number { Covered.run(flag); return 0; }
            """.trimIndent(),
            exportName = "harness",
            inputs = listOf(PropertyInput(name = "flag", domain = BooleanDomain)),
        )
        val engine = CurrentTsCallsCoverageEngine(testSourceEngine())
        val request = coverageRequest(fixture)
        val classFunction = fixture.function.copy(
            symbolicCoverageClass = CallsSymbolicCoverageClass(sourceFile = "Fixture.ts", className = "Covered"),
        )

        val entrySearch = engine.search(request)
        val classSearches = CallsExperimentProfile.entries.associateWith { profile ->
            engine.search(request.copy(function = classFunction, profile = profile))
        }

        assertEquals(expected = 1, actual = entrySearch.candidates.size, message = entrySearch.toString())
        for ((profile, search) in classSearches) {
            assertEquals(
                expected = CallsCoverageSearchStatus.EXHAUSTED,
                actual = search.status,
                message = "$profile: $search",
            )
            assertEquals(expected = 2, actual = search.candidates.size, message = "$profile: $search")
            assertEquals(
                expected = setOf(listOf(JsConcreteValue.Boolean(true)), listOf(JsConcreteValue.Boolean(false))),
                actual = search.candidates.map { candidate -> candidate.inputs }.toSet(),
            )
            assertTrue(search.candidates.all { candidate -> candidate.newSymbolicStatements > 0 })
        }

        val replays = OriginalTypeScriptCoverageReplayer(
            sourceRoot = fixture.sourceRoot,
            function = classFunction,
        ).use { replayer ->
            classSearches.getValue(CallsExperimentProfile.EMPTY_STOP).candidates.map { candidate ->
                replayer.replay(inputs = candidate.inputs, timeoutMillis = 20_000L)
            }
        }

        assertEquals(expected = 2, actual = replays.flatMap { replay -> replay.coveredIfArmKeys }.toSet().size)
        assertTrue(replays.all { replay -> replay.completion == CallsCoverageCompletion.RETURNED })
    }

    @Test
    fun `class novelty retains constructor branches through a constant wrapper`() {
        val fixture = fixture(
            source = """
                export class Covered {
                  value: number;
                  constructor(flag: boolean) {
                    if (flag) { this.value = 1; } else { this.value = 2; }
                  }
                }
                export function harness(flag: boolean): number { const value = new Covered(flag); return 0; }
            """.trimIndent(),
            exportName = "harness",
            inputs = listOf(PropertyInput(name = "flag", domain = BooleanDomain)),
            symbolicCoverageClass = CallsSymbolicCoverageClass(sourceFile = "Fixture.ts", className = "Covered"),
        )

        val search = CurrentTsCallsCoverageEngine(testSourceEngine()).search(coverageRequest(fixture))

        assertEquals(
            expected = CallsCoverageSearchStatus.EXHAUSTED,
            actual = search.status,
            message = search.toString(),
        )
        assertEquals(expected = 2, actual = search.candidates.size, message = search.toString())
    }

    @Test
    fun `class scope distinguishes source paths and ignores foreign class branches`() {
        val fixture = fixture(
            source = """
                import { Target as Chosen } from './chosen/Target';
                import { Target as Foreign } from './foreign/Target';
                export function harness(flag: boolean): number { Chosen.run(); Foreign.run(flag); return 0; }
            """.trimIndent(),
            exportName = "harness",
            inputs = listOf(PropertyInput(name = "flag", domain = BooleanDomain)),
            additionalSources = mapOf(
                "chosen/Target.ts" to "export class Target { static run(): number { return 7; } }",
                "foreign/Target.ts" to """
                    export class Target {
                      static run(flag: boolean): number { if (flag) { return 1; } return 2; }
                    }
                """.trimIndent(),
            ),
            symbolicCoverageClass = CallsSymbolicCoverageClass(sourceFile = "chosen/Target.ts", className = "Target"),
        )

        val search = CurrentTsCallsCoverageEngine(testSourceEngine()).search(coverageRequest(fixture))

        assertEquals(
            expected = CallsCoverageSearchStatus.EXHAUSTED,
            actual = search.status,
            message = search.toString(),
        )
        assertEquals(expected = 1, actual = search.candidates.size, message = search.toString())
    }

    @Test
    fun `wrapper-only coverage does not create class candidates`() {
        val fixture = fixture(
            source = """
                export class Covered { static unused(): number { return 7; } }
                export function harness(flag: boolean): number { if (flag) { return 1; } return 2; }
            """.trimIndent(),
            exportName = "harness",
            inputs = listOf(PropertyInput(name = "flag", domain = BooleanDomain)),
            symbolicCoverageClass = CallsSymbolicCoverageClass(sourceFile = "Fixture.ts", className = "Covered"),
        )
        val emitted = mutableListOf<CallsCoverageCandidate>()

        val search = CurrentTsCallsCoverageEngine(testSourceEngine()).search(
            coverageRequest(fixture, onCandidate = emitted::add),
        )

        assertEquals(
            expected = CallsCoverageSearchStatus.EXHAUSTED,
            actual = search.status,
            message = search.toString(),
        )
        assertEquals(expected = emptyList(), actual = search.candidates)
        assertEquals(expected = emptyList(), actual = emitted)
        assertTrue(search.executedSteps > 0)
    }

    @Test
    fun `unmapped coverage classes are rejected before symbolic search`() {
        val fixture = fixture(
            source = """
                export class Covered { static run(): number { return 7; } }
                export function harness(): number { return Covered.run(); }
            """.trimIndent(),
            exportName = "harness",
            inputs = emptyList(),
        )
        val engine = CurrentTsCallsCoverageEngine(testSourceEngine())
        val targets = listOf(
            CallsSymbolicCoverageClass(sourceFile = "Fixture.ts", className = "Missing"),
            CallsSymbolicCoverageClass(sourceFile = "Missing.ts", className = "Covered"),
        )

        for (target in targets) {
            val request = coverageRequest(fixture).copy(
                function = fixture.function.copy(symbolicCoverageClass = target),
            )

            val search = engine.search(request)

            assertEquals(
                expected = CallsCoverageSearchStatus.UNMAPPED,
                actual = search.status,
                message = search.toString(),
            )
            assertEquals(expected = 0L, actual = search.executedSteps)
            assertEquals(expected = emptyList(), actual = search.candidates)
        }
    }

    private fun coverageRequest(
        fixture: CoverageFixture,
        onCandidate: (CallsCoverageCandidate) -> Unit = {},
        solverType: SolverType = SolverType.YICES,
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
        solverType = solverType,
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
        symbolicCoverageClass: CallsSymbolicCoverageClass? = null,
    ): CoverageFixture {
        val sourceRoot = Files.createDirectory(directory.resolve(exportName))
        Files.writeString(sourceRoot.resolve("Fixture.ts"), source)
        additionalSources.forEach { (name, contents) ->
            val file = sourceRoot.resolve(name)
            Files.createDirectories(file.parent)
            Files.writeString(file, contents)
        }
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
            symbolicCoverageClass = symbolicCoverageClass,
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
