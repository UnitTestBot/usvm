package org.usvm.ts.pbt.external

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class ArtifactContractTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `all golden v2 artifacts and raw run directory validate`() {
        val reports = listOf(
            ArtifactValidator.validateTargetManifest(fixture("valid/target-manifest.json")),
            ArtifactValidator.validateSourceTargets(fixture("valid/source-targets.jsonl")),
            ArtifactValidator.validateMethodIds(fixture("valid/method-ids.txt")),
            ArtifactValidator.validateRunConfig(fixture("valid/run-config.json")),
            ArtifactValidator.validateExternalTestCorpus(fixture("valid/raw-run/corpus.etc.jsonl")),
            ArtifactValidator.validateNativeCoverage(fixture("valid/raw-run/native-coverage.json")),
            ArtifactValidator.validateRunMeta(fixture("valid/raw-run/run-meta.json")),
            ArtifactValidator.validateRawRunDirectory(fixture("valid/raw-run")),
        )

        reports.forEach { report ->
            assertTrue(report.valid, "${report.artifact}: ${report.issues}")
            assertTrue(report.issues.isEmpty())
        }
    }

    @Test
    fun `special ETC values plans callables and aliases round-trip without loss`() {
        val first = ExternalTestCorpusCodec.read(fixture("valid/raw-run/corpus.etc.jsonl"))
        assertEquals(EXTERNAL_TEST_CORPUS_SCHEMA_VERSION, first.schemaVersion)
        assertTrue(first.rejections.isEmpty())
        val corpus = ExternalTestCorpus(producer = first.producer, cases = first.cases)

        val encoded = ExternalTestCorpusCodec.encode(corpus)
        val second = ExternalTestCorpusCodec.decode(encoded, "round-trip.etc.jsonl")

        assertEquals(first.producer, second.producer)
        assertEquals(first.cases, second.cases)
        assertTrue(second.rejections.isEmpty())
        assertTrue(encoded.lineSequence().first().contains("\"schemaVersion\":2"))
        assertFalse(encoded.contains("\"cases\""), "v2 must not fall back to document encoding")
        val values = second.cases.single().arguments
        assertTrue(values.any { it.kind == "number" && it.value == "NaN" })
        assertTrue(values.any { it.kind == "number" && it.value == "Infinity" })
        assertTrue(values.any { it.kind == "number" && it.value == "-Infinity" })
        assertTrue(values.any { it.kind == "number" && it.value == "-0" })
        assertTrue(values.any { it.kind == "array" && it.elements.first().kind == "hole" })
        assertTrue(values.any { it.kind == "map" })
        assertTrue(values.any { it.kind == "callable" && it.callableReference != null })
        assertTrue(values.any { it.kind == "alias" && it.aliasReference == "shared-object" })
        assertTrue(values.count { it.kind == "unrepresentable" } == 2)
        assertTrue(second.cases.single().receiver.constructorPlan != null)
    }

    @Test
    fun `source targets and document artifacts use their canonical codecs`() {
        val sourceText = fixture("valid/source-targets.jsonl").readText()
        val sourceRecords = ArtifactContractCodec.decodeSourceTargets(sourceText, "golden")
        assertEquals(sourceRecords, ArtifactContractCodec.decodeSourceTargets(ArtifactContractCodec.encodeSourceTargets(sourceRecords)))

        val configText = fixture("valid/run-config.json").readText()
        val config = ArtifactContractCodec.decodeRunConfig(configText, "golden")
        assertEquals(config, ArtifactContractCodec.decodeRunConfig(ArtifactContractCodec.encodeRunConfig(config)))

        val nativeText = fixture("valid/raw-run/native-coverage.json").readText()
        val native = ArtifactContractCodec.decodeNativeCoverage(nativeText, "golden")
        assertEquals(native, ArtifactContractCodec.decodeNativeCoverage(ArtifactContractCodec.encodeNativeCoverage(native)))

        val metaText = fixture("valid/raw-run/run-meta.json").readText()
        val meta = ArtifactContractCodec.decodeRunMeta(metaText, "golden")
        assertEquals(meta, ArtifactContractCodec.decodeRunMeta(ArtifactContractCodec.encodeRunMeta(meta)))
    }

    @Test
    fun `v1 converter emits valid canonical v2 and classifies old rejects`() {
        val legacy = fixture("valid/legacy-v1.etc.json")
        val converted = ExternalTestCorpusV1Converter.convert(legacy.readText(), legacy.toString())
        val output = tempDir.resolve("converted.etc.jsonl")
        ExternalTestCorpusV1Converter.convertFile(legacy, output)

        assertEquals(EXTERNAL_TEST_CORPUS_SCHEMA_VERSION, converted.schemaVersion)
        assertEquals(25, converted.cases.single().generatedAtMs)
        assertEquals("17", converted.cases.single().seed)
        assertEquals("legacy-v1:legacy-special", converted.cases.single().path)
        assertEquals("function", converted.cases.single().arguments.last().unrepresentableKind)
        assertTrue(output.readText().lineSequence().first().contains("\"schemaVersion\":2"))
        assertTrue(ArtifactValidator.validateExternalTestCorpus(output).valid)

        val implicitLegacy = ExternalTestCorpusV1Converter.convert(
            """[{"id":"implicit","methodId":"m/0","arguments":[]}]""",
            "unversioned-file.json",
        )
        assertEquals("legacy-v1@unknown", implicitLegacy.producer)
        assertEquals("unversioned-file.json", implicitLegacy.cases.single().metadata["legacyProducer"])
        assertEquals("legacy-v1:implicit", implicitLegacy.cases.single().path)
    }

    @Test
    fun `unknown fields are accepted but unknown versions and enums are explicit rejects`() {
        val manifestText = fixture("valid/target-manifest.json").readText()
        val manifest = TargetManifest.decode(manifestText, "unknown-field-golden")
        val reencoded = TargetManifest.encode(manifest)
        assertFalse(reencoded.contains("futureDocumentField"), "ignored extensions are not preserved")
        assertTrue(ArtifactValidator.validateTargetManifest(fixture("valid/target-manifest.json")).valid)

        val unknownVersion = ArtifactValidator.validateTargetManifest(
            fixture("invalid/unknown-version-target-manifest.json"),
        )
        val missingVersion = ArtifactValidator.validateTargetManifest(
            fixture("invalid/missing-version-target-manifest.json"),
        )
        val malformedVersion = ArtifactValidator.validateTargetManifest(
            fixture("invalid/malformed-version-target-manifest.json"),
        )
        val badSourceTargets = ArtifactValidator.validateSourceTargets(fixture("invalid/source-targets.jsonl"))
        val badMethodIds = ArtifactValidator.validateMethodIds(fixture("invalid/method-ids.txt"))
        val badConfig = ArtifactValidator.validateRunConfig(fixture("invalid/run-config.json"))
        val badEtc = ArtifactValidator.validateExternalTestCorpus(fixture("invalid/corpus.etc.jsonl"))
        val badRawRun = ArtifactValidator.validateRawRunDirectory(fixture("invalid/raw-run-bad-version"))

        assertTrue(unknownVersion.issues.any { it.code == "schema_version" })
        assertTrue(missingVersion.issues.any { it.code == "schema_version" })
        assertTrue(malformedVersion.issues.any { it.code == "schema_version" })
        assertTrue(badSourceTargets.issues.any { it.code == "unknown_enum" })
        assertTrue(badSourceTargets.issues.any { it.code == "invalid_range" })
        assertTrue(badMethodIds.issues.any { it.code == "whitespace" })
        assertTrue(badMethodIds.issues.any { it.code == "duplicate" })
        assertTrue(badConfig.issues.any { it.code == "deadline_mismatch" })
        assertTrue(badConfig.issues.any { it.code == "unknown_enum" })
        assertTrue(badEtc.issues.any { it.code == "unknown_discriminator" })
        assertTrue(badEtc.issues.any { it.code == "missing_generation_origin" })
        assertTrue(badRawRun.issues.any { it.code == "schema_version" })
    }

    @Test
    fun `v2 document encoding is rejected instead of becoming a second format`() {
        val cause = assertThrows(IllegalArgumentException::class.java) {
            ExternalTestCorpusCodec.read(fixture("invalid/v2-document.etc.json"))
        }
        assertTrue(cause.message.orEmpty().contains("requires JSONL"))
    }

    @Test
    fun `validator CLI has stable valid invalid and conversion exit codes`() {
        val validOut = StringBuilder()
        assertEquals(
            0,
            ArtifactContractCli.run(
                arrayOf("validate", "raw-run", fixture("valid/raw-run").toString()),
                validOut,
                StringBuilder(),
            ),
        )
        assertTrue(validOut.contains("\"valid\":true"))

        val invalidOut = StringBuilder()
        assertEquals(
            2,
            ArtifactContractCli.run(
                arrayOf("validate", "run-config", fixture("invalid/run-config.json").toString()),
                invalidOut,
                StringBuilder(),
            ),
        )
        assertTrue(invalidOut.contains("\"valid\":false"))

        val converted = tempDir.resolve("cli-converted.etc.jsonl")
        val convertOut = StringBuilder()
        assertEquals(
            0,
            ArtifactContractCli.run(
                arrayOf("convert-v1-etc", fixture("valid/legacy-v1.etc.json").toString(), converted.toString()),
                convertOut,
                StringBuilder(),
            ),
        )
        assertTrue(converted.exists())
        assertTrue(convertOut.contains("\"valid\":true"))
    }

    @Test
    fun `published JSON schemas are parseable and explicitly additive`() {
        val schemas = schemaDirectory().listDirectoryEntries("*.schema.json")
        assertEquals(6, schemas.size)
        schemas.forEach { schema ->
            val document = Json.parseToJsonElement(schema.readText()).toString()
            assertTrue(document.contains("\"additionalProperties\":true"), schema.toString())
        }
    }

    private fun fixture(relative: String): Path = Path.of(
        requireNotNull(javaClass.getResource("/artifact-contract/v2/$relative")) {
            "missing fixture $relative"
        }.toURI(),
    )

    private fun schemaDirectory(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        return listOf(
            workingDirectory.resolve("usvm-ts-pbt/artifact-contract/v2"),
            workingDirectory.resolve("artifact-contract/v2"),
        ).firstOrNull(Path::exists) ?: error("artifact-contract/v2 is not reachable from $workingDirectory")
    }
}
