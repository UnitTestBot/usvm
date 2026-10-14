package org.usvm.ts.calls

import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.JsNumber
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CallsFrozenProjectResolutionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `unchanged real Stats class prepares and replays through a local source alias`() {
        checkStatsResolution(importSpecifier = "@local/Stats", usePackageExport = false)
    }

    @Test
    fun `unchanged real Stats class prepares and replays through a pinned source package export`() {
        checkStatsResolution(importSpecifier = "@fixture/stats/Stats", usePackageExport = true)
    }

    @Test
    fun `frozen module config exports and dependency bytes reject changed preparation inputs`() {
        val harness = statsFixture(importSpecifier = "@fixture/stats/Stats", usePackageExport = true)
        val closure = checkedCallsSourceClosure(
            root = directory,
            entry = directory.resolve(harness.functions.first().sourceFile),
        )
        val resolution = requireNotNull(closure.resolution)
        val preparedDirectory = directory.resolve("prepared")
        val preparedSource = preparedDirectory.resolve("source")
        val instrumentation = CallsClassSourceInstrumentation.create(
            sourceRoot = directory,
            sourceFile = harness.surface.sourceFile,
            className = harness.surface.exportName,
        )
        copyCallsSourceClosure(root = directory, destination = preparedSource, closure = closure)
        writeCallsFrozenTsconfig(root = preparedSource, resolution = resolution)
        val harnessFile = harness.functions.first().sourceFile
        val record = CallsClassPreparedRecord(
            classCase = CallsClassCase(
                classId = "stats",
                projectId = harness.project.projectId,
                revision = harness.project.revision,
                sourceRoot = directory.toString(),
                sourceSha256 = resolution.inputSha256,
                surface = harness.surface,
            ),
            originalSourceRoot = directory.toString(),
            scenarios = harness.scenarios,
            functions = harness.functions,
            observations = harness.observations,
            harnessFile = harnessFile,
            harnessSha256 = callsClassSourceSha256(Files.readAllBytes(directory.resolve(harnessFile))),
            instrumentation = instrumentation,
            resolution = resolution,
        )
        record.harness(preparedDirectory)
        verifyCallsFrozenResolution(root = directory, resolution = resolution)

        for (relative in listOf("tsconfig.json", "package.json", "src/Stats.ts", "package-lock.json")) {
            val file = preparedSource.resolve(relative)
            val original = Files.readString(file)
            Files.writeString(file, "$original\n ")

            val error = assertFailsWith<IllegalArgumentException> { record.harness(preparedDirectory) }
            assertTrue(error.message.orEmpty().contains(relative), error.message)

            Files.writeString(file, original)
        }
        Files.writeString(preparedSource.resolve(CALLS_FROZEN_TSCONFIG), "{}")

        assertFailsWith<IllegalArgumentException> { record.harness(preparedDirectory) }
    }

    @Test
    fun `persisted source-only record rejects newly introduced config or package metadata`() {
        val sourceFile = "Receiver.ts"
        Files.writeString(directory.resolve(sourceFile), "export class Receiver { read(): number { return 1; } }")
        runCallsGit(directory, "init", "--quiet")
        runCallsGit(directory, "add", "--", sourceFile)
        runCallsGit(
            directory,
            "-c", "user.name=Calls Test", "-c", "user.email=calls-test@example.invalid",
            "-c", "commit.gpgsign=false", "commit", "--quiet", "-m", "Freeze source fixture",
        )
        val revision = runCallsGit(directory, "rev-parse", "HEAD").trim()
        val surface = CallsClassSurface(
            sourceFile = sourceFile,
            exportName = "Receiver",
            constructorInputs = emptyList(),
            members = listOf(
                CallsClassMember(
                    id = "read",
                    name = "read",
                    kind = CallsClassMemberKind.METHOD,
                    inputs = emptyList(),
                    resultDomain = NumberDomain(),
                ),
            ),
            stateFields = emptyMap(),
        )
        val project = CallsProjectCase(
            projectId = "source-only",
            revision = revision,
            sourceRoot = directory.toString(),
            development = true,
            functions = emptyList(),
        )
        val harness = prepareCallsClassHarness(sourceRoot = directory, project = project, surface = surface)
        val instrumentation = CallsClassSourceInstrumentation.create(
            sourceRoot = directory,
            sourceFile = sourceFile,
            className = surface.exportName,
        )
        val harnessFile = harness.functions.first().sourceFile
        val record = CallsClassPreparedRecord(
            classCase = CallsClassCase(
                classId = "receiver",
                projectId = project.projectId,
                revision = revision,
                sourceRoot = directory.toString(),
                sourceSha256 = mapOf(
                    sourceFile to callsClassSourceSha256(Files.readAllBytes(directory.resolve(sourceFile)))
                ),
                surface = surface,
            ),
            originalSourceRoot = directory.toString(),
            scenarios = harness.scenarios,
            functions = harness.functions,
            observations = harness.observations,
            harnessFile = harnessFile,
            harnessSha256 = callsClassSourceSha256(Files.readAllBytes(directory.resolve(harnessFile))),
            instrumentation = instrumentation,
        )
        record.verifyOriginal(checkResolution = true)

        for ((name, metadata) in mapOf(
            "tsconfig.json" to "{\"compilerOptions\":{\"strict\":true}}",
            "package.json" to "{\"name\":\"new-package\",\"type\":\"module\"}",
        )) {
            Files.writeString(directory.resolve(name), metadata)

            val error = assertFailsWith<IllegalArgumentException> { record.verifyOriginal(checkResolution = true) }
            assertTrue(error.message.orEmpty().contains("Frozen project module resolution changed"), error.message)

            Files.delete(directory.resolve(name))
        }
    }

    private fun checkStatsResolution(importSpecifier: String, usePackageExport: Boolean) {
        val harness = statsFixture(importSpecifier = importSpecifier, usePackageExport = usePackageExport)
        val source = directory.resolve(harness.functions.first().sourceFile)
        val closure = checkedCallsSourceClosure(root = directory, entry = source)
        val instrumentation = CallsClassSourceInstrumentation.create(
            sourceRoot = directory,
            sourceFile = harness.surface.sourceFile,
            className = harness.surface.exportName,
        )
        instrumentation.validateSurface(harness.surface)

        val sourceProject = loadCallsSourceProject(sourceRoot = directory, source = source)
        val loaded = assertIs<CallsSourceProject.Loaded>(sourceProject)
        val index = harness.scenarios.indexOfFirst { it.memberIds == listOf("formatTime") }
        val options = JsConcreteValue.Object(
            fields = mapOf(
                "partial" to JsConcreteValue.Boolean(value = false),
                "alwaysIncludeMinute" to JsConcreteValue.Boolean(value = false),
            ),
        )
        val inputs = listOf(JsConcreteValue.number(value = 65_430.0), options)
        val original = OriginalTypeScriptClassReplayer(
            sourceRoot = directory,
            function = harness.functions[index],
            observationFields = harness.observations[index],
        ).use { it.replay(inputs = inputs, timeoutMillis = 20_000) }
        val instrumented = OriginalTypeScriptClassReplayer(
            sourceRoot = directory,
            function = harness.functions[index],
            observationFields = harness.observations[index],
            sourceOverride = instrumentation.instrumentedSource,
            overrideSourceFile = harness.surface.sourceFile,
            counterGlobalName = instrumentation.hitGlobalName,
        ).use { it.replay(inputs = inputs, timeoutMillis = 20_000) }

        assertEquals(setOf(harness.functions.first().sourceFile, "src/Stats.ts"), closure.files.toSet())
        assertEquals(closure.files.toSet(), loaded.sourcePaths.values.toSet())
        assertTrue(loaded.scene.projectFiles.flatMap { it.classes }.any { it.name == "Stats" })
        assertTrue(original.observation.representable, original.observation.diagnostic)
        assertEquals(JsConcreteValue.String("1:05"), original.observation.values["result_0"])
        assertEquals(original.observation, instrumented.observation)
        assertTrue(instrumentation.universe.checkedCoveredIds(instrumented.coveredStatementKeys).isNotEmpty())
        assertEquals("src/Stats.ts", instrumentation.universe.sourceFile)
    }

    private fun statsFixture(importSpecifier: String, usePackageExport: Boolean): CallsClassPreparedHarness {
        writeStatsSources(usePackageExport = usePackageExport)
        val project = CallsProjectCase(
            projectId = "source-linked-stats",
            revision = "d1d482e2d67f1d21cf3d13d7b32283e84eba7331",
            sourceRoot = directory.toString(),
            development = true,
            functions = emptyList(),
        )
        val harness = prepareCallsClassHarness(sourceRoot = directory, project = project, surface = statsSurface())
        // Only the generated consumer changes. The real class retains every byte and public member.
        val harnessFile = directory.resolve(harness.functions.first().sourceFile)
        Files.writeString(harnessFile, Files.readString(harnessFile).replace("./src/Stats.ts", importSpecifier))
        return harness
    }

    private fun writeStatsSources(usePackageExport: Boolean) {
        Files.createDirectories(directory.resolve("src"))
        val resource = requireNotNull(javaClass.getResourceAsStream("/project-resolution/Stats.ts"))
        resource.use { Files.copy(it, directory.resolve("src/Stats.ts")) }
        val exports = if (usePackageExport) {
            ",\"exports\":{\"./Stats\":{\"types\":\"./missing.d.ts\"," +
                "\"default\":\"./src/Stats.ts\"}}"
        } else {
            ""
        }
        val packageJson = "{\"name\":\"@fixture/stats\",\"version\":\"1.0.0\",\"type\":\"module\"$exports}"
        Files.writeString(directory.resolve("package.json"), packageJson)
        val packageLockJson = "{\"lockfileVersion\":3,\"packages\":{\"\":{\"name\":\"@fixture/stats\"," +
            "\"version\":\"1.0.0\"}}}"
        Files.writeString(directory.resolve("package-lock.json"), packageLockJson)
        val paths = if (usePackageExport) "" else ",\"paths\":{\"@local/*\":[\"src/*\"]}"
        val tsconfigJson = "{\"compilerOptions\":{\"target\":\"ES2022\",\"module\":\"ESNext\"," +
            "\"moduleResolution\":\"Bundler\"$paths}}"
        Files.writeString(directory.resolve("tsconfig.json"), tsconfigJson)
    }

    private fun statsSurface(): CallsClassSurface {
        val flag = OptionalDomain(value = BooleanDomain, nil = JsConcreteValue.Undefined)
        val milliseconds = NumberDomain(
            min = JsNumber.fromDouble(0.0),
            max = JsNumber.fromDouble(100_000.0),
            allowNaN = false,
        )
        val timeParts = CallsClassMember(
            id = "timeParts",
            name = "timeParts",
            kind = CallsClassMemberKind.METHOD,
            inputs = listOf(
                PropertyInput(name = "time", domain = milliseconds),
                PropertyInput(name = "options", domain = ObjectDomain(mapOf("alwaysIncludeMinute" to flag))),
            ),
            resultDomain = ObjectDomain(
                fields = mapOf(
                    "secFirst" to StringDomain(maxLength = 20),
                    "secRest" to StringDomain(maxLength = 20),
                    "decimals" to StringDomain(maxLength = 20),
                ),
            ),
            isStatic = true,
        )
        val formatTime = CallsClassMember(
            id = "formatTime",
            name = "formatTime",
            kind = CallsClassMemberKind.METHOD,
            inputs = listOf(
                PropertyInput(name = "time", domain = OptionalDomain(value = milliseconds, nil = JsConcreteValue.Null)),
                PropertyInput(
                    name = "options",
                    domain = OptionalDomain(
                        value = ObjectDomain(mapOf("partial" to flag, "alwaysIncludeMinute" to flag)),
                        nil = JsConcreteValue.Undefined,
                    ),
                ),
            ),
            resultDomain = StringDomain(maxLength = 30),
            isStatic = true,
        )
        return CallsClassSurface(
            sourceFile = "src/Stats.ts",
            exportName = "Stats",
            constructorInputs = emptyList(),
            members = listOf(timeParts, formatTime),
            stateFields = emptyMap(),
        )
    }
}
