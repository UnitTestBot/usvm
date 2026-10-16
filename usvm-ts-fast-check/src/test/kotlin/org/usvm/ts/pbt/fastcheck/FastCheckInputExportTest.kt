package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyInputExportRequest
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.observation.ObservationAdmission
import org.usvm.ts.pbt.observation.ObservationOutcome
import org.usvm.ts.pbt.observation.ObservationPhase
import org.usvm.ts.pbt.observation.ObservationValueStatus
import org.usvm.ts.pbt.observation.PropertyObservationRequest
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FastCheckInputExportTest {
    private val root = testResourcesRoot()
    private val backend = FastCheckBackend(sourceRoots = listOf(root))
    private val sources = listOf(
        PropertyObservationSource(module = MODULE, sha256 = sha256(Files.readAllBytes(root.resolve(MODULE)))),
    )
    private val configuration = PropertyRunConfiguration(seed = 37, numRuns = 300, timeoutMillis = 60_000)

    @Test
    fun `public backend exports more than 64 inputs alongside bounded observations and actual coverage`() {
        val result = backend.run(
            property = property(),
            configuration = configuration.copy(
                inputExportRequest = PropertyInputExportRequest(sources = sources, maxInvocations = 1000),
                observationRequest = PropertyObservationRequest(points = emptyList(), sources = sources),
                coverageRequest = PropertyCoverageRequest(
                    scopes = setOf(CoverageScope.PROPERTY_ENTRY_POINTS),
                    admittedOnly = true,
                ),
            ),
        )

        val export = assertNotNull(result.inputExport)
        assertEquals(PropertyRunStatus.SUCCESS, result.status)
        assertEquals(300, export.records.size)
        assertEquals(0, export.droppedInvocations)
        assertEquals(0, export.unsupportedInputs)
        assertEquals(0, export.pendingInvocations)
        assertTrue(export.records.all { it.phase == ObservationPhase.GENERATION })
        assertTrue(export.records.all { it.admission == ObservationAdmission.ADMITTED })
        assertTrue(export.records.all { it.outcome == ObservationOutcome.HOLDS })
        assertTrue(assertNotNull(result.observations).droppedInvocations > 0)
        assertTrue(assertNotNull(result.coverage).files.isNotEmpty())
    }

    @Test
    fun `large compact export fits the real transport together with coverage and observations`() {
        val text = JsConcreteValue.String("x".repeat(n = 9000) + "\uD800")
        val request = PropertyInputExportRequest(sources = sources)
        val result = backend.run(
            property = property(predicate = "textHolds", domain = ConstantDomain(value = text)),
            configuration = configuration.copy(
                numRuns = 128,
                inputExportRequest = request,
                observationRequest = PropertyObservationRequest(points = emptyList(), sources = sources),
                coverageRequest = PropertyCoverageRequest(
                    scopes = setOf(CoverageScope.PROPERTY_ENTRY_POINTS),
                    admittedOnly = true,
                ),
            ),
        )

        val export = assertNotNull(result.inputExport)
        val bytes = PropertyManifestJson.json.encodeToUtf8SafeString(export).toByteArray(Charsets.UTF_8)
        assertEquals(128, export.records.size)
        assertEquals(0, export.droppedInvocations)
        assertEquals(0, export.unsupportedInputs)
        assertTrue(bytes.size > 1024 * 1024)
        assertTrue(bytes.size <= request.maxBytes)
        assertTrue(assertNotNull(result.coverage).files.isNotEmpty())
        val firstInput = export.records.first().input.value as JsConcreteValue.Array
        assertEquals(text, firstInput.elements.single())
    }

    @Test
    fun `UTF16 and array inputs survive the actual process and artifact roundtrip`() {
        val values = JsConcreteValue.Array(listOf(JsConcreteValue.String("\uD800"), JsConcreteValue.String("\uDC00")))
        val property = property(
            predicate = "arrayHolds",
            domain = ArrayDomain(element = StringDomain(minLength = 0, maxLength = 3), maxLength = 3),
        )
        val result = backend.run(
            property = property,
            configuration = configuration.copy(
                numRuns = 100,
                examples = listOf(listOf(values)),
                inputExportRequest = PropertyInputExportRequest(sources = sources),
            ),
        )

        val json = PropertyManifestJson.json.encodeToUtf8SafeString(result)
        val restored = PropertyManifestJson.json.decodeFromString<PropertyRunResult>(json)
        val export = assertNotNull(restored.inputExport)
        val firstInput = export.records.first().input.value as JsConcreteValue.Array
        assertEquals(listOf(values), firstInput.elements)
        assertEquals(100, export.records.size)
        assertEquals(0, export.unsupportedInputs)
        assertEquals(result.inputExport, restored.inputExport)
    }

    @Test
    fun `original snapshots precede precondition and predicate array mutations`() {
        val property = property(
            predicate = "mutatingPredicate",
            domain = ArrayDomain(element = IntegerDomain(min = 1, max = 2), minLength = 2, maxLength = 2),
        ).copy(precondition = TypeScriptEntryPoint(module = MODULE, exportName = "mutatingPrecondition"))
        val result = backend.run(
            property = property,
            configuration = configuration.copy(inputExportRequest = PropertyInputExportRequest(sources = sources)),
        )

        val export = assertNotNull(result.inputExport)
        assertEquals(PropertyRunStatus.SUCCESS, result.status)
        assertEquals(300, export.records.size)
        for (record in export.records) {
            val arguments = record.input.value as JsConcreteValue.Array
            val original = arguments.elements.single() as JsConcreteValue.Array
            assertEquals(2, original.elements.size)
            assertEquals(ObservationOutcome.HOLDS, record.outcome)
        }
    }

    @Test
    fun `count and byte overflow preserve successful runs with explicit incomplete input exports`() {
        for (request in listOf(
            PropertyInputExportRequest(sources = sources, maxInvocations = 10),
            PropertyInputExportRequest(sources = sources, maxInvocations = 1000, maxBytes = 1024),
        )) {
            val result = backend.run(property(), configuration.copy(inputExportRequest = request))

            val export = assertNotNull(result.inputExport)
            assertEquals(PropertyRunStatus.SUCCESS, result.status)
            assertEquals(300, result.numRuns)
            assertEquals(300, export.records.size + export.droppedInvocations)
            assertTrue(export.droppedInvocations > 0)
            val bytes = PropertyManifestJson.json.encodeToUtf8SafeString(export).toByteArray(Charsets.UTF_8)
            assertTrue(bytes.size <= request.maxBytes)
        }
    }

    @Test
    fun `unsupported input snapshots and unresolved asynchronous outcomes stay explicit`() {
        val arrays = property(
            predicate = "arrayHolds",
            domain = ArrayDomain(element = StringDomain(maxLength = 1), minLength = 2, maxLength = 2),
        )
        val unsupported = backend.run(
            property = arrays,
            configuration = configuration.copy(
                inputExportRequest = PropertyInputExportRequest(sources = sources, maxArrayElements = 1),
            ),
        )
        val asynchronous = property().copy(
            predicate = TypeScriptEntryPoint(
                module = MODULE,
                exportName = "neverCompletes",
                executionKind = ExecutionKind.ASYNC,
            ),
        )
        val timeout = backend.run(
            property = asynchronous,
            configuration = configuration.copy(
                numRuns = 1,
                timeoutMillis = 50,
                inputExportRequest = PropertyInputExportRequest(sources = sources),
            ),
        )

        val unknownInputs = assertNotNull(unsupported.inputExport)
        assertEquals(300, unknownInputs.unsupportedInputs)
        assertTrue(unknownInputs.records.all { it.input.status == ObservationValueStatus.UNSUPPORTED })
        val pending = assertNotNull(timeout.inputExport)
        assertEquals(1, pending.pendingInvocations)
        assertTrue(pending.records.any { it.outcome == ObservationOutcome.PENDING })
    }

    @Test
    fun `ordinary default runs do not allocate or publish input exports`() {
        val result = backend.run(property(), configuration)

        assertEquals(PropertyRunStatus.SUCCESS, result.status)
        assertEquals(300, result.numRuns)
        assertNull(result.inputExport)
        assertNull(result.observations)
    }

    private fun property(
        predicate: String = "holds",
        domain: PropertyDomain = IntegerDomain(min = -100, max = 100),
    ) = PropertyDefinition(
        id = PropertyId(value = "inputs.$predicate"),
        inputs = listOf(PropertyInput(name = "value", domain = domain)),
        predicate = TypeScriptEntryPoint(module = MODULE, exportName = predicate),
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val MODULE = "properties/InputExportProperties.ts"
    }
}
