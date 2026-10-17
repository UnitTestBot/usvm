package org.usvm.ts.pbt.cli

import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.campaign.PropertyCampaignArtifact
import org.usvm.ts.pbt.campaign.PropertyCampaignMode
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.testResourcesRoot
import java.io.IOException
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampaignFileCliTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `frozen file invokes the public symbolic campaign without a registry`() {
        val request = request(mode = PropertyCampaignMode.SYMBOLIC_ONLY).copy(completedInputLimit = 3)
        val path = directory.resolve("request.json")
        val destination = directory.resolve("artifact.json")
        Files.writeString(path, PropertyManifestJson.json.encodeToUtf8SafeString(request))
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit = runCampaignFile(arrayOf(path.toString(), destination.toString()), output, errors)
        val artifact = PropertyCampaignArtifact.read(destination)

        assertEquals(0, exit)
        assertEquals("", errors.toString())
        assertEquals(Files.readString(destination), output.toString().trim())
        assertEquals(37, artifact.configuration.seed)
        assertEquals(PropertyCampaignMode.SYMBOLIC_ONLY, artifact.result.mode)
        assertFalse(artifact.shrinkWitness)
        assertTrue(artifact.result.symbolicInputReplays.isNotEmpty())
    }

    @Test
    fun `file controls reject invalid capture before running or creating an artifact`() {
        val request = request(mode = PropertyCampaignMode.PBT_ONLY).copy(completedInputLimit = 1)
        val path = directory.resolve("request.json")
        val destination = directory.resolve("artifact.json")
        Files.writeString(path, PropertyManifestJson.json.encodeToUtf8SafeString(request))
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit = runCampaignFile(arrayOf(path.toString(), destination.toString()), output, errors)

        assertEquals(2, exit)
        assertEquals("", output.toString())
        assertTrue(errors.contains("symbolic campaign"))
        assertFalse(Files.exists(destination))
    }

    @Test
    fun `file artifact is readable before failing stdout publication`() {
        val request = request(mode = PropertyCampaignMode.PBT_ONLY)
        val path = directory.resolve("request.json")
        val destination = directory.resolve("artifact.json")
        Files.writeString(path, PropertyManifestJson.json.encodeToUtf8SafeString(request))
        val errors = StringBuilder()
        var stdoutAttempted = false
        val output = object : Writer() {
            override fun write(buffer: CharArray, offset: Int, length: Int) {
                stdoutAttempted = true
                val artifact = PropertyCampaignArtifact.read(destination)
                assertEquals(PropertyCampaignMode.PBT_ONLY, artifact.result.mode)

                throw IOException("stdout closed")
            }

            override fun flush() = Unit

            override fun close() = Unit
        }

        val exit = runCampaignFile(arrayOf(path.toString(), destination.toString()), output, errors)
        val artifact = PropertyCampaignArtifact.read(destination)

        assertEquals(2, exit)
        assertTrue(stdoutAttempted)
        assertTrue(errors.contains("stdout closed"))
        assertEquals(1, artifact.result.concrete?.numRuns)
        assertEquals(37, artifact.configuration.seed)
    }

    @Test
    fun `file write failure retains the full stdout artifact and original error`() {
        val request = request(mode = PropertyCampaignMode.PBT_ONLY)
        val path = directory.resolve("request.json")
        val destination = directory.resolve("missing-parent").resolve("artifact.json")
        Files.writeString(path, PropertyManifestJson.json.encodeToUtf8SafeString(request))
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit = runCampaignFile(arrayOf(path.toString(), destination.toString()), output, errors)
        val artifact = PropertyManifestJson.json.decodeFromString<PropertyCampaignArtifact>(output.toString())

        assertEquals(2, exit)
        assertEquals(destination.toString(), errors.toString().trim())
        assertFalse(Files.exists(destination))
        assertEquals(1, artifact.result.concrete?.numRuns)
        assertEquals(37, artifact.configuration.seed)
    }

    private fun request(mode: PropertyCampaignMode) = CampaignFileRequest(
        sourceRoot = testResourcesRoot().resolve("properties/campaign").toString(),
        property = PropertyDefinition(
            id = PropertyId(value = "file.healthy"),
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = -100, max = 100))),
            predicate = TypeScriptEntryPoint(module = "CompletedProperties.ts", exportName = "healthy"),
        ),
        mode = mode,
        configuration = PropertyRunConfiguration(seed = 37, numRuns = 1, timeoutMillis = 60_000),
    )
}
