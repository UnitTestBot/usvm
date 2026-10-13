package org.usvm.ts.calls

import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallsCoverageSourceTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `unchanged source resumes the exact persisted search without rerunning it`() {
        val fixture = fixture()
        fixture.persistSearch()

        fixture.runCell()

        val result = CallsExperimentJson.json.decodeFromString<CallsCoverageCellResult>(
            Files.readString(fixture.output.resolve("result.json")),
        )
        assertEquals(emptySearch, result.search)
        assertEquals(fixture.source.identity, result.identity.sourceIdentity)
        assertEquals(3, result.identity.schemaVersion)
        assertEquals("YICES", result.identity.solver)
    }

    @Test
    fun `coverage cell forwards the explicit manifest solver to search`() {
        val fixture = fixture(solver = "Z3")
        val solvers = mutableListOf<SolverType>()

        fixture.runCell(searchFunction = { request ->
            solvers += request.solverType
            emptySearch
        })

        val result = CallsExperimentJson.json.decodeFromString<CallsCoverageCellResult>(
            Files.readString(fixture.output.resolve("result.json")),
        )
        assertEquals(listOf(SolverType.Z3), solvers)
        assertEquals("Z3", result.identity.solver)
    }

    @Test
    fun `resumed search rejects a persisted identity from a different solver`() {
        val fixture = fixture()
        fixture.persistSearch(solver = "Z3")

        val failure = assertFailsWith<IllegalArgumentException> { fixture.runCell() }

        assertEquals("Cell identity changed", failure.message)
        fixture.assertNoReplayOrCompletion()
    }

    @Test
    fun `resumed search rejects the prior cell schema`() {
        val fixture = fixture()
        fixture.persistSearch(schemaVersion = 2)

        val failure = assertFailsWith<IllegalArgumentException> { fixture.runCell() }

        assertEquals("Cell identity changed", failure.message)
        fixture.assertNoReplayOrCompletion()
    }

    @Test
    fun `build mismatch rejects search and universe collection before callbacks`() {
        val fixture = fixture()
        var searched = false
        var probed = false
        val otherRevision = "2222222222222222222222222222222222222222"

        val searchFailure = assertFailsWith<IllegalArgumentException> {
            fixture.runCell(runtimeToolRevision = otherRevision, searchFunction = {
                searched = true
                emptySearch
            })
        }
        val universeFailure = assertFailsWith<IllegalArgumentException> {
            fixture.runUniverse(runtimeToolRevision = otherRevision, probeFunction = {
                probed = true
                sampleUniverse
            })
        }

        assertEquals("Coverage manifest tool revision differs from the running build", searchFailure.message)
        assertEquals(searchFailure.message, universeFailure.message)
        assertFalse(searched)
        assertFalse(probed)
        assertFalse(Files.exists(fixture.output.resolve("identity.json")))
        assertFalse(Files.exists(directory.resolve("universe-output/universe.json")))
    }

    @Test
    fun `frontend mismatch rejects search and universe collection before callbacks`() {
        val fixture = fixture()
        var searched = false
        var probed = false

        val searchFailure = assertFailsWith<IllegalArgumentException> {
            fixture.runCell(runtimeNativeFrontendRevision = "bundled:other", searchFunction = {
                searched = true
                emptySearch
            })
        }
        val universeFailure = assertFailsWith<IllegalArgumentException> {
            fixture.runUniverse(runtimeNativeFrontendRevision = "bundled:other", probeFunction = {
                probed = true
                sampleUniverse
            })
        }

        assertEquals("Coverage manifest frontend revision differs from the running build", searchFailure.message)
        assertEquals(searchFailure.message, universeFailure.message)
        assertFalse(searched)
        assertFalse(probed)
        assertFalse(Files.exists(fixture.output.resolve("identity.json")))
        assertFalse(Files.exists(directory.resolve("universe-output/universe.json")))
    }

    @Test
    fun `only completion-matching native replays contribute checkpoint coverage`() {
        val fixture = fixture()
        val universe = OriginalTypeScriptCoverageReplayer(
            sourceRoot = fixture.source.sourceRoot,
            function = fixture.source.function,
        ).use { replayer -> replayer.probeUniverse(timeoutMillis = 20_000L) }
        fixture.writeUniverse(universe)

        for (completion in CallsCoverageCompletion.entries) {
            val candidate = CallsCoverageCandidate(
                emittedAtMillis = 1L,
                emittedAtStep = 1L,
                inputs = listOf(JsConcreteValue.Boolean(true)),
                newSymbolicStatements = 1,
                completion = completion,
            )
            val cell = fixture.copy(output = Files.createDirectory(directory.resolve(completion.name)))
            cell.persistSearch(search = emptySearch.copy(candidates = listOf(candidate), selectedStates = 1))

            cell.runCell()

            val result = CallsExperimentJson.json.decodeFromString<CallsCoverageCellResult>(
                Files.readString(cell.output.resolve("result.json")),
            )
            val event = CallsExperimentJson.json.decodeFromString<CallsCoverageReplayEvent>(
                Files.readAllLines(cell.output.resolve("replays.jsonl")).single(),
            )
            val replay = requireNotNull(event.replay)
            val checkpoint = result.checkpoints.single()
            val isAccepted = completion == CallsCoverageCompletion.RETURNED
            val acceptedCount = if (isAccepted) 1 else 0

            assertEquals(expected = 0, actual = result.replayErrors)
            assertEquals(expected = 1 - acceptedCount, actual = result.completionMismatches)
            assertEquals(expected = CallsCoverageCompletion.RETURNED, actual = replay.completion)
            assertTrue(replay.coveredStatementKeys.isNotEmpty())
            assertEquals(expected = 1, actual = checkpoint.emittedCandidates)
            assertEquals(expected = 1, actual = checkpoint.replayedCandidates)
            if (isAccepted) {
                assertTrue(checkpoint.coveredStatements > 0)
            } else {
                assertEquals(expected = 0, actual = checkpoint.coveredStatements)
                assertEquals(expected = 0, actual = checkpoint.coveredIfArms)
            }
        }
    }

    @Test
    fun `resumed search rejects changed export domain and frozen models at the same manifest path`() {
        val fixture = fixture()
        fixture.persistSearch()
        val original = fixture.source.manifest
        val project = fixture.source.project
        val function = fixture.source.function
        val mutations = listOf(
            original.copy(
                projects = listOf(
                    project.copy(
                        functions = listOf(
                            function.copy(entryPoint = function.entryPoint.copy(exportName = "second")),
                        )
                    )
                )
            ),
            original.copy(
                projects = listOf(
                    project.copy(
                        functions = listOf(
                            function.copy(inputs = listOf(PropertyInput(name = "flag", domain = NumberDomain()))),
                        )
                    )
                )
            ),
            original.copy(
                projects = listOf(
                    project.copy(
                        functions = listOf(
                            function.copy(
                                symbolicCoverageClass = CallsSymbolicCoverageClass(
                                    sourceFile = "Fixture.ts",
                                    className = "DifferentClass",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            original.copy(modelSet = original.modelSet.copy(ids = setOf("ts.array.includes"))),
        )

        for (changed in mutations) {
            Files.writeString(fixture.manifestPath, CallsExperimentJson.encodeManifest(changed))

            val failure = assertFailsWith<IllegalArgumentException> { fixture.runCell() }

            assertEquals("Coverage universe identity differs", failure.message)
            fixture.assertNoReplayOrCompletion()
        }
    }

    @Test
    fun `resumed search rejects a changed checkout revision even with an updated manifest`() {
        val fixture = fixture()
        fixture.persistSearch()
        Files.writeString(fixture.sourceFile, "export function first(flag: boolean): boolean { return !flag; }")
        runCallsGit(fixture.source.sourceRoot, "add", "Fixture.ts")
        runCallsGit(fixture.source.sourceRoot, "commit", "-m", "Change source")
        val newRevision = runCallsGit(fixture.source.sourceRoot, "rev-parse", "HEAD").trim()
        val changed = fixture.source.manifest.copy(
            projects = listOf(fixture.source.project.copy(revision = newRevision))
        )
        Files.writeString(fixture.manifestPath, CallsExperimentJson.encodeManifest(changed))

        val failure = assertFailsWith<IllegalArgumentException> { fixture.runCell() }

        assertEquals("Coverage universe identity differs", failure.message)
        fixture.assertNoReplayOrCompletion()
    }

    @Test
    fun `resumed search rejects tracked source edits before executing target code`() {
        val fixture = fixture()
        fixture.persistSearch()
        Files.writeString(fixture.sourceFile, "export function first(): never { throw new Error('changed source'); }")

        assertFailsWith<IllegalArgumentException> { fixture.runCell() }

        fixture.assertNoReplayOrCompletion()
    }

    @Test
    fun `untracked source cannot silently change a resumed program`() {
        val fixture = fixture()
        fixture.persistSearch()
        Files.writeString(fixture.source.sourceRoot.resolve("Added.ts"), "export const added = true;")

        val failure = assertFailsWith<IllegalArgumentException> { fixture.runCell() }

        assertTrue(failure.message.orEmpty().contains("must be clean"))
        fixture.assertNoReplayOrCompletion()
    }

    @Test
    fun `source edit during search aborts before persisting search or replay`() {
        val fixture = fixture()

        assertFailsWith<IllegalArgumentException> {
            fixture.runCell(searchFunction = {
                Files.writeString(fixture.sourceFile, "export function first(flag: boolean): boolean { return !flag; }")
                emptySearch
            })
        }

        assertFalse(Files.exists(fixture.output.resolve("search.json")))
        fixture.assertNoReplayOrCompletion()
    }

    @Test
    fun `manifest edit between search and replay is detected by the frozen source guard`() {
        val fixture = fixture()
        fixture.persistSearch()
        val changed = fixture.source.manifest.copy(
            modelSet = fixture.source.manifest.modelSet.copy(ids = setOf("other"))
        )
        Files.writeString(fixture.manifestPath, CallsExperimentJson.encodeManifest(changed))

        val failure = assertFailsWith<IllegalArgumentException> { fixture.source.verifyUnchanged() }

        assertEquals("Coverage source manifest changed", failure.message)
        fixture.assertNoReplayOrCompletion()
    }

    @Test
    fun `universe probe records the same frozen source identity used by the cell`() {
        val fixture = fixture()

        val output = fixture.runUniverse(probeFunction = { sampleUniverse })
        val record = CallsExperimentJson.json.decodeFromString<CallsCoverageUniverseRecord>(
            Files.readString(output.resolve("universe.json")),
        )

        assertEquals(fixture.source.identity, record.identity.sourceIdentity)
        assertEquals(sampleUniverse, record.universe)
    }

    @Test
    fun `dirty source is rejected before a universe probe executes user code`() {
        val fixture = fixture()
        Files.writeString(fixture.sourceFile, "export function first(): never { throw new Error('changed source'); }")
        var probed = false

        assertFailsWith<IllegalArgumentException> {
            fixture.runUniverse(probeFunction = {
                probed = true
                sampleUniverse
            })
        }

        assertFalse(probed)
        assertFalse(Files.exists(directory.resolve("universe-output/universe.json")))
    }

    @Test
    fun `source edits during universe collection prevent publication`() {
        val fixture = fixture()

        assertFailsWith<IllegalArgumentException> {
            fixture.runUniverse(probeFunction = {
                Files.writeString(fixture.sourceFile, "export function first(flag: boolean): boolean { return !flag; }")
                sampleUniverse
            })
        }

        assertFalse(Files.exists(directory.resolve("universe-output/universe.json")))
    }

    private fun fixture(solver: String = "YICES"): Fixture {
        val sourceRoot = Files.createDirectory(directory.resolve("source"))
        val sourceFile = sourceRoot.resolve("Fixture.ts")
        Files.writeString(sourceFile, "export function first(flag: boolean): boolean { return flag; }")
        runCallsGit(sourceRoot, "init")
        runCallsGit(sourceRoot, "config", "user.name", "USVM Tests")
        runCallsGit(sourceRoot, "config", "user.email", "usvm@example.test")
        runCallsGit(sourceRoot, "add", ".")
        runCallsGit(sourceRoot, "commit", "-m", "Create fixture")
        val revision = runCallsGit(sourceRoot, "rev-parse", "HEAD").trim()
        val function = CallsFunctionCase(
            functionId = "first",
            sourceFile = "Fixture.ts",
            entryPoint = TypeScriptEntryPoint(module = "Fixture.ts", exportName = "first"),
            inputs = listOf(PropertyInput(name = "flag", domain = BooleanDomain)),
            targets = emptyList(),
        )
        val project = CallsProjectCase(
            projectId = "fixture",
            revision = revision,
            sourceRoot = "source",
            development = true,
            functions = listOf(function),
        )
        val manifest = CallsExperimentManifest(
            schemaVersion = 1,
            experimentId = "coverage-identity",
            toolRevision = TOOL_REVISION,
            nativeFrontendRevision = FRONTEND_REVISION,
            solver = solver,
            searchPolicy = CALLS_PATH_SELECTION_STRATEGY.name,
            modelSet = CallsModelSetIdentity(ids = emptySet(), toolRevision = TOOL_REVISION),
            seeds = listOf(17L),
            perTargetBudgetMillis = 30_000L,
            projects = listOf(project),
        )
        val manifestPath = directory.resolve("manifest.json")
        Files.writeString(manifestPath, CallsExperimentJson.encodeManifest(manifest))
        val source = CallsCoverageSource.load(manifestPath, projectIndex = 0, functionIndex = 0)
        val output = Files.createDirectory(directory.resolve("output"))

        return Fixture(source, manifestPath, sourceFile, output).also { it.writeUniverse() }
    }

    private data class Fixture(
        val source: CallsCoverageSource,
        val manifestPath: Path,
        val sourceFile: Path,
        val output: Path,
    ) {
        private val universePath: Path = manifestPath.resolveSibling("universe.json")

        fun writeUniverse(universe: CallsCoverageUniverse = sampleUniverse) {
            val identity = CallsCoverageUniverseIdentity(
                toolRevision = TOOL_REVISION,
                nativeFrontendRevision = FRONTEND_REVISION,
                sourceManifest = manifestPath.toRealPath().toString(),
                sourceIdentity = source.identity,
                projectIndex = 0,
                functionIndex = 0,
                sourceFile = source.function.sourceFile,
            )
            val record = CallsCoverageUniverseRecord(identity = identity, universe = universe)
            Files.writeString(universePath, CallsExperimentJson.json.encodeToUtf8SafeString(record))
        }

        fun runUniverse(
            runtimeToolRevision: String = TOOL_REVISION,
            runtimeNativeFrontendRevision: String = FRONTEND_REVISION,
            probeFunction: (CallsCoverageSource) -> CallsCoverageUniverse,
        ): Path {
            val output = manifestPath.resolveSibling("universe-output")
            runCoverageUniverse(
                args = listOf(manifestPath.toString(), "0", "0", output.toString()),
                runtimeToolRevision = runtimeToolRevision,
                runtimeNativeFrontendRevision = runtimeNativeFrontendRevision,
                probeFunction = probeFunction,
            )

            return output
        }

        fun persistSearch(
            schemaVersion: Int = 3,
            solver: String = source.manifest.solver,
            search: CallsCoverageSearchResult = emptySearch,
        ) {
            val identity = CallsCoverageCellIdentity(
                schemaVersion = schemaVersion,
                toolRevision = TOOL_REVISION,
                nativeFrontendRevision = FRONTEND_REVISION,
                sourceManifest = manifestPath.toRealPath().toString(),
                sourceIdentity = source.identity,
                solver = solver,
                projectIndex = 0,
                functionIndex = 0,
                profile = CallsExperimentProfile.EMPTY_STOP,
                seed = 17L,
                budgetMillis = 30_000L,
                solverLimitMillis = 1_000L,
                candidateCap = 8,
                coverageUniverseSha256 = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(universePath))
                    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) },
            )
            Files.writeString(
                output.resolve("identity.json"),
                CallsExperimentJson.json.encodeToUtf8SafeString(identity)
            )
            Files.writeString(
                output.resolve("search.json"),
                CallsExperimentJson.json.encodeToUtf8SafeString(search)
            )
        }

        fun runCell(
            runtimeToolRevision: String = TOOL_REVISION,
            runtimeNativeFrontendRevision: String = FRONTEND_REVISION,
            searchFunction: (CallsCoverageSearchRequest) -> CallsCoverageSearchResult = {
                error("Must resume persisted search")
            },
        ) {
            runCoverageCell(
                args = listOf(
                    manifestPath.toString(),
                    "0",
                    "0",
                    "EMPTY_STOP",
                    "17",
                    output.toString(),
                    "30000",
                    "1000",
                    "8",
                    universePath.toString(),
                ),
                runtimeToolRevision = runtimeToolRevision,
                runtimeNativeFrontendRevision = runtimeNativeFrontendRevision,
                searchFunction = searchFunction,
            )
        }

        fun assertNoReplayOrCompletion() {
            assertFalse(Files.exists(output.resolve("replays.jsonl")))
            assertFalse(Files.exists(output.resolve("result.json")))
        }
    }

    companion object {
        private const val TOOL_REVISION = "1111111111111111111111111111111111111111"
        private const val FRONTEND_REVISION = "bundled:test"
        private val sampleUniverse = CallsCoverageUniverse(
            supportedStatementKeys = setOf("Fixture.ts:statement:1:0-1:75"),
            sourceFiles = setOf("Fixture.ts"),
            diagnostics = emptyList(),
            probeElapsedMillis = 0,
        )
        private val emptySearch = CallsCoverageSearchResult(
            status = CallsCoverageSearchStatus.EXHAUSTED,
            candidates = emptyList(),
            selectedStates = 0,
            executedSteps = 0,
            stepsWithinBudget = 0,
            extractionFailures = emptyList(),
            candidateCapReached = false,
            preparationElapsedMillis = 0,
            machineSetupElapsedMillis = 0,
            searchElapsedMillis = 1,
            machineTeardownElapsedMillis = 0,
        )
    }
}
