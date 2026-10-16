package org.usvm.ts.pbt.cli

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.campaign.PropertyCampaignArtifact
import org.usvm.ts.pbt.campaign.PropertyCampaignMode
import org.usvm.ts.pbt.campaign.PropertyCampaignPhase
import org.usvm.ts.pbt.campaign.PropertyCampaignStop
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.registry.PropertyRegistry
import org.usvm.ts.pbt.registry.PropertyRegistryProvider
import org.usvm.ts.pbt.testResourcesRoot
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampaignCliTest {
    @TempDir
    lateinit var directory: Path

    private val sourceRoot = testResourcesRoot().resolve("properties/campaign")

    @Test
    fun `all direct modes use the original oracle and save their actual confirmed violation`() {
        for (mode in PropertyCampaignMode.entries.filter { it != PropertyCampaignMode.COVERAGE_FEEDBACK }) {
            val destination = directory.resolve("$mode.json")
            val example = if (mode == PropertyCampaignMode.SEQUENTIAL) 20.0 else 42.0
            val coverage = if (mode == PropertyCampaignMode.PBT_ONLY) {
                arrayOf("--coverage", "--coverage-scope", "property-entry-points")
            } else {
                emptyArray()
            }
            val run = runCli(
                arguments(mode, destination) + arrayOf("--examples", writeExample(example).toString()) + coverage,
            )

            val artifact = readArtifact(run, destination)

            assertEquals(1, run.exitCode)
            assertEquals("", run.errors)
            assertEquals(mode, artifact.result.mode)
            assertEquals(354, artifact.configuration.seed)
            assertEquals(1, artifact.configuration.numRuns)
            assertTrue(artifact.result.confirmedViolation)
            if (mode == PropertyCampaignMode.PBT_ONLY) {
                assertNull(artifact.result.symbolic)
                assertTrue(assertNotNull(artifact.result.concrete?.coverage).files.isNotEmpty())
            } else {
                assertEquals(listOf(JsConcreteValue.number(42.0)), artifact.result.symbolic?.inputs)
                assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, artifact.result.confirmation?.status)
            }
            if (mode == PropertyCampaignMode.SYMBOLIC_ONLY) {
                assertNull(artifact.result.concrete)
            }
            if (mode == PropertyCampaignMode.SEQUENTIAL) {
                assertEquals(PropertyRunStatus.SUCCESS, artifact.result.concrete?.status)
            }

            for (stop in listOf(PropertyCampaignStop.DEADLINE, PropertyCampaignStop.EXECUTION_FAILURE)) {
                val interruptedAfterFinding = artifact.result.copy(
                    stop = stop,
                    stoppedAt = PropertyCampaignPhase.CONFIRMATION,
                )

                assertEquals(1, campaignExitCode(interruptedAfterFinding))
            }
        }
    }

    @Test
    fun `guided CLI generates once and prints and saves the same completed native feedback artifact`() {
        val property = property().copy(
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = -100, max = 100))),
            predicate = TypeScriptEntryPoint(module = "GuidedProperties.ts", exportName = "branchHolds"),
            precondition = null,
        )
        val destination = directory.resolve("guided.json")

        val run = runCli(
            arguments(PropertyCampaignMode.COVERAGE_FEEDBACK, destination) + arrayOf(
                "--pbt-allocation-ms", "15000", "--examples", writeExample(value = 1.0).toString(), "--no-shrinking",
            ),
            properties = listOf(property),
        )
        val artifact = readArtifact(run, destination)

        assertEquals(0, run.exitCode)
        assertEquals("", run.errors)
        assertEquals(1, artifact.result.concrete?.numRuns)
        val observations = assertNotNull(artifact.result.concrete?.observations)
        assertEquals(1, observations.invocations.size)
        assertEquals(15_000L, artifact.pbtAllocationMillis)
        assertFalse(artifact.shrinkWitness)
        val trace = assertNotNull(artifact.result.symbolic?.edgeGuidance)
        assertTrue(trace.replays.isNotEmpty())
        assertTrue(trace.remainingGoals.isEmpty())
    }

    @Test
    fun `completed direct controls with no finding return zero`() {
        val admittedSingleton = property().copy(
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = 20, max = 20))),
        )

        for (mode in PropertyCampaignMode.entries) {
            val destination = directory.resolve("$mode.json")

            val allocation = if (mode == PropertyCampaignMode.COVERAGE_FEEDBACK) {
                arrayOf("--pbt-allocation-ms", "15000")
            } else {
                emptyArray()
            }
            val run = runCli(arguments(mode, destination) + allocation, properties = listOf(admittedSingleton))
            val artifact = readArtifact(run, destination)

            assertEquals(0, run.exitCode)
            assertEquals("", run.errors)
            assertFalse(artifact.result.confirmedViolation)
            assertEquals(PropertyCampaignStop.COMPLETED, artifact.result.stop)
            val incompletePbt = artifact.result.copy(concrete = null, diagnostic = "PBT allocation expired")
            assertEquals(2, campaignExitCode(incompletePbt))
        }
    }

    @Test
    fun `unsupported symbolic control remains incomplete and is saved`() {
        val asynchronous = property().copy(
            predicate = TypeScriptEntryPoint(
                module = "DirectControls.ts",
                exportName = "asyncHolds",
                executionKind = ExecutionKind.ASYNC,
            ),
        )
        val destination = directory.resolve("unsupported.json")

        val run = runCli(
            arguments(PropertyCampaignMode.SYMBOLIC_ONLY, destination),
            properties = listOf(asynchronous),
        )
        val artifact = readArtifact(run, destination)

        assertEquals(2, run.exitCode)
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, artifact.result.symbolic?.status)
        assertFalse(artifact.result.confirmedViolation)
        assertNull(artifact.result.concrete)
    }

    @Test
    fun `expired total deadline is not reported as no finding`() {
        val destination = directory.resolve("timeout.json")

        val run = runCli(
            arguments(PropertyCampaignMode.SYMBOLIC_ONLY, destination) +
                arrayOf("--timeout-ms", "1", "--validation-reserve-ms", "1"),
        )
        val artifact = readArtifact(run, destination)

        assertEquals(2, run.exitCode)
        assertEquals(PropertyCampaignStop.DEADLINE, artifact.result.stop)
        assertNull(artifact.result.symbolic)
        assertFalse(artifact.result.confirmedViolation)
    }

    @Test
    fun `one deadline allocation retains PBT when the search reserve is already exhausted`() {
        val destination = directory.resolve("sequential-timeout.json")

        val run = runCli(
            arguments(PropertyCampaignMode.SEQUENTIAL, destination) + arrayOf(
                "--examples", writeExample(value = 20.0).toString(),
                "--timeout-ms", "60000",
                "--validation-reserve-ms", "60000",
            ),
        )
        val artifact = readArtifact(run, destination)

        assertEquals(2, run.exitCode)
        assertEquals(PropertyRunStatus.SUCCESS, artifact.result.concrete?.status)
        assertEquals(PropertyCampaignStop.DEADLINE, artifact.result.stop)
        assertEquals(PropertyCampaignPhase.SYMBOLIC, artifact.result.stoppedAt)
        assertEquals(60_000, artifact.configuration.timeoutMillis)
        assertEquals(60_000, artifact.validationReserveMillis)
        assertNull(artifact.result.symbolic)
    }

    @Test
    fun `native execution failure is saved separately from a property violation`() {
        val invalidExport = property().copy(
            predicate = TypeScriptEntryPoint(module = "DirectControls.ts", exportName = "missing"),
        )
        val destination = directory.resolve("execution-failure.json")

        val run = runCli(arguments(PropertyCampaignMode.PBT_ONLY, destination), properties = listOf(invalidExport))
        val artifact = readArtifact(run, destination)

        assertEquals(2, run.exitCode)
        assertEquals(PropertyCampaignStop.EXECUTION_FAILURE, artifact.result.stop)
        assertNotNull(artifact.result.diagnostic)
        assertFalse(artifact.result.confirmedViolation)
    }

    @Test
    fun `an artifact write failure retains the demonstrated violation on stdout`() {
        val run = runCli(
            arguments(PropertyCampaignMode.PBT_ONLY, directory) +
                arrayOf("--examples", writeExample(value = 42.0).toString()),
        )

        val artifact = PropertyManifestJson.json.decodeFromString<PropertyCampaignArtifact>(run.output)

        assertEquals(2, run.exitCode)
        assertEquals("cli.campaign.artifact.write-failed", diagnosticCode(run))
        assertTrue(artifact.result.confirmedViolation)
        assertEquals(listOf(JsConcreteValue.number(42.0)), artifact.result.concrete?.counterexample)
    }

    @Test
    fun `stdout and saved artifact come from one native campaign`() {
        val sources = Files.createDirectory(directory.resolve("sources"))
        val counter = directory.resolve("calls")
        val counterLiteral = PropertyManifestJson.json.encodeToUtf8SafeString(counter.toString())
        sources.resolve("Counted.ts").writeText(
            """
            import { appendFileSync } from 'node:fs';
            export function counted(value: number): boolean {
                appendFileSync($counterLiteral, 'x');
                return true;
            }
            """.trimIndent(),
        )
        val countedProperty = property().copy(
            predicate = TypeScriptEntryPoint(module = "Counted.ts", exportName = "counted"),
            precondition = null,
        )
        val destination = directory.resolve("counted.json")

        val run = runCli(
            arguments(PropertyCampaignMode.PBT_ONLY, destination, root = sources, numRuns = 3),
            properties = listOf(countedProperty),
        )
        val artifact = readArtifact(run, destination)

        assertEquals(0, run.exitCode)
        assertEquals(3, artifact.result.concrete?.numRuns)
        assertEquals("xxx", Files.readString(counter))
    }

    @Test
    fun `campaign options reject missing or inconsistent controls before execution`() {
        val destination = directory.resolve("invalid.json").toString()
        val root = sourceRoot.toString()
        val cases = listOf(
            arrayOf("--source-root", root, "--artifact", destination) to "cli.campaign.mode.required",
            arrayOf("--source-root", root, "--validation-reserve-ms", "1") to "cli.campaign.mode.required",
            arrayOf("--source-root", root, "--mode", "PBT_ONLY") to "cli.campaign.artifact.required",
            arrayOf(
                "--source-root", root, "--source-root", root, "--mode", "PBT_ONLY", "--artifact", destination,
            ) to "cli.campaign.source-root.invalid",
            arrayOf(
                "--source-root", root, "--mode", "PBT_ONLY", "--artifact", destination, "--validation-reserve-ms", "0",
            ) to "cli.campaign.reserve.invalid",
            arrayOf(
                "--source-root", root, "--mode", "UNKNOWN", "--artifact", destination,
            ) to "cli.argument.invalid",
        )

        for ((arguments, expectedCode) in cases) {
            val run = runCli(arguments)

            assertEquals(2, run.exitCode)
            assertEquals(expectedCode, diagnosticCode(run))
            assertEquals("", run.output)
            assertFalse(Files.exists(directory.resolve("invalid.json")))
        }
    }

    @Test
    fun `one artifact requires one selected property`() {
        val destination = directory.resolve("multiple.json")
        val second = property().copy(id = PropertyId(value = "cli.second"))

        val run = runCli(
            arrayOf("--source-root", sourceRoot.toString(), "--mode", "PBT_ONLY", "--artifact", destination.toString()),
            properties = listOf(property(), second),
        )

        assertEquals(2, run.exitCode)
        assertEquals("cli.single-property.required", diagnosticCode(run))
        assertEquals("", run.output)
        assertFalse(Files.exists(destination))
    }

    private fun arguments(
        mode: PropertyCampaignMode,
        destination: Path,
        root: Path = sourceRoot,
        numRuns: Int = 1,
    ): Array<String> = arrayOf(
        "--source-root", root.toString(),
        "--registry", "campaign-cli",
        "--property", "cli.isolated",
        "--seed", "354",
        "--num-runs", numRuns.toString(),
        "--mode", mode.name,
        "--artifact", destination.toString(),
    )

    private fun writeExample(value: Double): Path {
        val path = directory.resolve("example-$value.json")
        val example: List<JsConcreteValue> = listOf(JsConcreteValue.number(value))
        val json = PropertyManifestJson.json.encodeToUtf8SafeString(listOf(example))
        Files.writeString(path, json)

        return path
    }

    private fun runCli(
        arguments: Array<String>,
        properties: List<PropertyDefinition> = listOf(property()),
    ): CliRun {
        val provider = object : PropertyRegistryProvider {
            override val registryId: String = "campaign-cli"
            override fun load(): PropertyRegistry = PropertyRegistry(properties)
        }
        val output = StringBuilder()
        val errors = StringBuilder()

        val exitCode = FastCheckCli(providers = listOf(provider), output = output, errors = errors).run(arguments)

        return CliRun(exitCode, output.toString(), errors.toString())
    }

    private fun readArtifact(run: CliRun, path: Path): PropertyCampaignArtifact {
        assertEquals(Files.readString(path), run.output.trimEnd())

        return PropertyCampaignArtifact.read(path)
    }

    private fun diagnosticCode(run: CliRun): String {
        val diagnostic = PropertyManifestJson.json.parseToJsonElement(run.errors).jsonObject

        return diagnostic.getValue("code").jsonPrimitive.content
    }

    private fun property() = PropertyDefinition(
        id = PropertyId(value = "cli.isolated"),
        inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = 0, max = 100))),
        predicate = TypeScriptEntryPoint(module = "DirectControls.ts", exportName = "isolatedFailure"),
        precondition = TypeScriptEntryPoint(module = "DirectControls.ts", exportName = "admits"),
    )

    private data class CliRun(val exitCode: Int, val output: String, val errors: String)
}
