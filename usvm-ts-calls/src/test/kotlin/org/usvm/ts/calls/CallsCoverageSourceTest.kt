package org.usvm.ts.calls

import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import java.nio.file.Files
import java.nio.file.Path
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
    }

    @Test
    fun `resumed search rejects changed export domain and frozen models at the same manifest path`() {
        val fixture = fixture()
        fixture.persistSearch()
        val original = fixture.source.manifest
        val project = fixture.source.project
        val function = fixture.source.function
        val changedExport = function.copy(entryPoint = function.entryPoint.copy(exportName = "second"))
        val changedInputs = function.copy(inputs = listOf(PropertyInput(name = "flag", domain = NumberDomain())))
        val mutations = listOf(
            original.copy(projects = listOf(project.copy(functions = listOf(changedExport)))),
            original.copy(projects = listOf(project.copy(functions = listOf(changedInputs)))),
            original.copy(modelSet = original.modelSet.copy(ids = setOf("ts.array.includes"))),
        )

        for (changed in mutations) {
            Files.writeString(fixture.manifestPath, CallsExperimentJson.encodeManifest(changed))

            val failure = assertFailsWith<IllegalArgumentException> { fixture.runCell() }

            assertEquals("Cell identity changed", failure.message)
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
            projects = listOf(fixture.source.project.copy(revision = newRevision)),
        )
        Files.writeString(fixture.manifestPath, CallsExperimentJson.encodeManifest(changed))

        val failure = assertFailsWith<IllegalArgumentException> { fixture.runCell() }

        assertEquals("Cell identity changed", failure.message)
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
            modelSet = fixture.source.manifest.modelSet.copy(ids = setOf("other")),
        )
        Files.writeString(fixture.manifestPath, CallsExperimentJson.encodeManifest(changed))

        val failure = assertFailsWith<IllegalArgumentException> { fixture.source.verifyUnchanged() }

        assertEquals("Coverage source manifest changed", failure.message)
        fixture.assertNoReplayOrCompletion()
    }

    private fun fixture(): Fixture {
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
            solver = "Z3",
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

        return Fixture(source, manifestPath, sourceFile, output)
    }

    private data class Fixture(
        val source: CallsCoverageSource,
        val manifestPath: Path,
        val sourceFile: Path,
        val output: Path,
    ) {
        fun persistSearch() {
            val identity = CallsCoverageCellIdentity(
                schemaVersion = 2,
                toolRevision = TOOL_REVISION,
                nativeFrontendRevision = FRONTEND_REVISION,
                sourceManifest = manifestPath.toRealPath().toString(),
                sourceIdentity = source.identity,
                projectIndex = 0,
                functionIndex = 0,
                profile = CallsExperimentProfile.EMPTY_STOP,
                seed = 17L,
                budgetMillis = 30_000L,
                solverLimitMillis = 1_000L,
                candidateCap = 8,
            )
            Files.writeString(
                output.resolve("identity.json"),
                CallsExperimentJson.json.encodeToUtf8SafeString(identity),
            )
            Files.writeString(
                output.resolve("search.json"),
                CallsExperimentJson.json.encodeToUtf8SafeString(emptySearch),
            )
        }

        fun runCell(
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
                ),
                runtimeToolRevision = TOOL_REVISION,
                runtimeNativeFrontendRevision = FRONTEND_REVISION,
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
        private val emptySearch = CallsCoverageSearchResult(
            status = CallsCoverageSearchStatus.EXHAUSTED,
            candidates = emptyList(),
            selectedStates = 0,
            extractionFailures = emptyList(),
            candidateCapReached = false,
            preparationElapsedMillis = 0,
            searchElapsedMillis = 1,
        )
    }
}
