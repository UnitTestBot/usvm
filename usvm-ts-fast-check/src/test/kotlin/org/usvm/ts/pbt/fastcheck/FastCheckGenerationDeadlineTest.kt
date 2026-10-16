package org.usvm.ts.pbt.fastcheck

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyInputExportRequest
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FastCheckGenerationDeadlineTest {
    @Test
    fun `budget-driven generation publishes retained native inputs before the transport deadline`() {
        val root = testResourcesRoot()
        val module = "properties/InputExportProperties.ts"
        val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(module)))
            .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
        val property = PropertyDefinition(
            id = PropertyId(value = "deadline.inputs"),
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = -100, max = 100))),
            predicate = TypeScriptEntryPoint(module = module, exportName = "holds"),
        )
        val configuration = PropertyRunConfiguration(
            seed = 37,
            numRuns = 1_000_000,
            timeoutMillis = 5000,
            coverageRequest = PropertyCoverageRequest(admittedOnly = true),
            inputExportRequest = PropertyInputExportRequest(
                sources = listOf(PropertyObservationSource(module = module, sha256 = hash)),
            ),
        )

        val result = FastCheckBackend(sourceRoots = listOf(root)).run(
            property = property,
            configuration = configuration,
            deadline = WitnessReplayDeadline(timeoutMillis = 5000),
        )

        val export = assertNotNull(result.inputExport)
        assertEquals(PropertyFailureKind.TIMEOUT, result.failure?.kind)
        assertTrue(export.records.size > 64)
        assertEquals(result.numRuns + result.numSkips, export.records.size + export.droppedInvocations)
        assertEquals(0, export.unsupportedInputs)
    }
}
