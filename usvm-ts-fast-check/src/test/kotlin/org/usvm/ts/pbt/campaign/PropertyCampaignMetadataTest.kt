package org.usvm.ts.pbt.campaign

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertyCampaignMetadataTest {
    @TempDir
    lateinit var directory: Path

    private val configuration = PropertyRunConfiguration(seed = 354, numRuns = 1, timeoutMillis = 60_000)

    @Test
    fun `no finding records selected modules and explicit settings without a version probe`() {
        val oracle = directory.resolve("Oracle.ts")
        oracle.writeText(
            "import { holds } from './Helper'; export function predicate(value: number) { return holds(value); }",
        )
        directory.resolve("Helper.ts").writeText("export function holds(value: number) { return value === value; }")
        val admission = directory.resolve("Admission.ts")
        admission.writeText("export function admits(value: number) { return value >= 0; }")
        val tsconfig = directory.resolve("tsconfig.json")
        tsconfig.writeText("""{"compilerOptions":{"target":"ES2020"}}""")
        val executable = directory.resolve("node-without-version-probe")
        executable.writeText("#!/bin/sh\nif [ \"${'$'}1\" = \"--version\" ]; then exit 90; fi\nexec node \"${'$'}@\"\n")
        check(executable.toFile().setExecutable(true))
        val property = property().copy(
            predicate = TypeScriptEntryPoint(module = "Oracle.ts", exportName = "predicate"),
            precondition = TypeScriptEntryPoint(module = "Admission.ts", exportName = "admits"),
        )

        val artifact = PropertyCampaignRunner(
            sourceRoot = directory.resolve("."),
            tsconfig = tsconfig,
            nodeExecutable = executable.toString(),
        ).runArtifact(property, PropertyCampaignMode.PBT_ONLY, configuration)
        val restored = saveAndRead(artifact)

        val metadata = assertNotNull(restored.metadata)
        assertEquals(directory.toRealPath().toString(), metadata.sourceRoot)
        assertEquals(executable.toString(), metadata.requestedNodeCommand)
        assertEquals(PropertyCampaignSourceScope.PROPERTY_ENTRY_MODULES, metadata.sourceScope)
        assertEquals(
            mapOf("Oracle.ts" to hash(oracle), "Admission.ts" to hash(admission)),
            metadata.sources.associate { source -> source.module to source.sha256 },
        )
        assertEquals(
            PropertyCampaignTsconfig(path = tsconfig.toRealPath().toString(), sha256 = hash(tsconfig)),
            metadata.explicitConcreteTsconfig,
        )
        assertEquals(PropertyRunStatus.SUCCESS, restored.result.concrete?.status)
        assertTrue(assertNotNull(restored.result.concrete?.runtimeVersion).startsWith("v"))
        assertFalse(restored.result.confirmedViolation)
        assertEquals(artifact, restored)
    }

    @Test
    fun `a concrete violation survives source removal after the initial snapshot`() {
        val module = directory.resolve("DirectControls.ts")
        module.writeText(
            """
            import { unlinkSync } from 'node:fs';
            export function isolatedFailure(value: number): boolean {
                unlinkSync(${PropertyManifestJson.json.encodeToUtf8SafeString(module.toString())});
                return false;
            }
            """.trimIndent(),
        )
        val initialHash = hash(module)

        val artifact = PropertyCampaignRunner(sourceRoot = directory).runArtifact(
            property = property(),
            mode = PropertyCampaignMode.SEQUENTIAL,
            configuration = configuration,
        )
        val restored = saveAndRead(artifact)

        assertFalse(Files.exists(module))
        assertTrue(restored.result.confirmedViolation)
        assertEquals(PropertyCampaignStop.COMPLETED, restored.result.stop)
        assertEquals(initialHash, restored.metadata?.sources?.single()?.sha256)
        assertNotNull(restored.result.concrete?.runtimeVersion)
        assertNull(restored.result.symbolic)
    }

    @Test
    fun `native confirmation still checks source freshness after both snapshots`() {
        val module = directory.resolve("DirectControls.ts")
        Files.copy(testResourcesRoot().resolve("properties/campaign/DirectControls.ts"), module)
        val initialHash = hash(module)
        val executable = directory.resolve("changing-node")
        executable.writeText("#!/bin/sh\nprintf '\\n// changed before replay\\n' >> '$module'\nexec node \"${'$'}@\"\n")
        check(executable.toFile().setExecutable(true))

        val artifact = PropertyCampaignRunner(
            sourceRoot = directory,
            nodeExecutable = executable.toString(),
        ).runArtifact(property(), PropertyCampaignMode.SYMBOLIC_ONLY, configuration)
        val restored = saveAndRead(artifact)

        assertNotNull(restored.result.symbolic?.inputs)
        val confirmation = assertNotNull(restored.result.confirmation)
        assertEquals(WitnessReplayStatus.SOURCE_MISMATCH, confirmation.status)
        assertEquals(initialHash, restored.metadata?.sources?.single()?.sha256)
        assertEquals(initialHash, confirmation.candidate.sources.single().sha256)
        assertFalse(restored.result.confirmedViolation)
        assertNull(restored.result.concrete)
        assertNotNull(confirmation.originalReplay?.runtimeVersion)
    }

    @Test
    fun `symbolic candidate uses the later source revision while metadata retains the initial snapshot`() {
        val module = directory.resolve("DirectControls.ts")
        val changedSource = "export function isolatedFailure(value: number): boolean { return value !== 42; }"
        val changedJson = PropertyManifestJson.json.encodeToUtf8SafeString(changedSource)
        module.writeText(
            """
            import { writeFileSync } from 'node:fs';
            export function isolatedFailure(value: number): boolean {
                writeFileSync(${PropertyManifestJson.json.encodeToUtf8SafeString(module.toString())}, $changedJson);
                return true;
            }
            """.trimIndent(),
        )
        val initialHash = hash(module)

        val artifact = PropertyCampaignRunner(sourceRoot = directory).runArtifact(
            property = property(),
            mode = PropertyCampaignMode.SEQUENTIAL,
            configuration = configuration,
        )
        val restored = saveAndRead(artifact)

        assertEquals(PropertyRunStatus.SUCCESS, restored.result.concrete?.status)
        val confirmation = assertNotNull(restored.result.confirmation)
        assertEquals(initialHash, restored.metadata?.sources?.single()?.sha256)
        assertEquals(hash(module), confirmation.candidate.sources.single().sha256)
        assertFalse(initialHash == hash(module))
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, confirmation.status)
        assertTrue(restored.result.confirmedViolation)
    }

    @Test
    fun `source capture failure remains a preparation failure without a fabricated runtime`() {
        val artifact = PropertyCampaignRunner(sourceRoot = directory).runArtifact(
            property = property(),
            mode = PropertyCampaignMode.PBT_ONLY,
            configuration = configuration,
        )

        assertEquals(PropertyCampaignStop.EXECUTION_FAILURE, artifact.result.stop)
        assertEquals(PropertyCampaignPhase.PREPARATION, artifact.result.stoppedAt)
        assertNotNull(artifact.result.diagnostic)
        assertNull(artifact.metadata)
        assertNull(artifact.result.concrete)
        assertFalse(artifact.result.confirmedViolation)
    }

    @Test
    fun `previous artifacts remain readable with unknown metadata and runtime version`() {
        val artifact = PropertyCampaignRunner(sourceRoot = testResourcesRoot().resolve("properties/campaign"))
            .runArtifact(property(), PropertyCampaignMode.PBT_ONLY, configuration)
        val json = PropertyManifestJson.json.parseToJsonElement(
            PropertyManifestJson.json.encodeToUtf8SafeString(artifact),
        ).jsonObject
        val result = json.getValue("result").jsonObject
        val concrete = JsonObject(result.getValue("concrete").jsonObject - "runtimeVersion")
        val previousResult = JsonObject(result + ("concrete" to concrete))
        val previous = JsonObject((json - "metadata") + ("result" to previousResult))
        val path = directory.resolve("previous.json")
        Files.writeString(path, previous.toString())

        val restored = PropertyCampaignArtifact.read(path)

        assertNull(restored.metadata)
        assertNull(restored.result.concrete?.runtimeVersion)
        assertEquals(artifact.result.concrete?.counterexample, restored.result.concrete?.counterexample)
    }

    private fun property() = PropertyDefinition(
        id = PropertyId(value = "metadata.property"),
        inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = 0, max = 100))),
        predicate = TypeScriptEntryPoint(module = "DirectControls.ts", exportName = "isolatedFailure"),
    )

    private fun saveAndRead(artifact: PropertyCampaignArtifact): PropertyCampaignArtifact {
        val path = directory.resolve("campaign.json")
        artifact.write(path)

        return PropertyCampaignArtifact.read(path)
    }

    private fun hash(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
