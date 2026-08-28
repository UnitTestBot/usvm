package org.usvm.ts.pbt.backend

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.model.PropertyId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PropertyCoverageTest {
    @Test
    fun `coverage request defaults to source under test only`() {
        val request = PropertyCoverageRequest()

        assertEquals(setOf(CoverageScope.SOURCE_UNDER_TEST), request.scopes)
        assertEquals(emptyList(), request.includePatterns)
        assertEquals(emptyList(), request.excludePatterns)
    }

    @Test
    fun `supported and unsupported capabilities carry different evidence`() {
        val supported = PropertyCoverageCapability.supported(
            backendId = "fast-check",
            backendVersion = "4.9.0",
            collector = CoverageCollectorIdentity(
                id = "c8",
                version = "10.1.3",
            ),
            artifactVersion = 1,
        )

        assertEquals(CoverageCapabilityLevel.SUPPORTED, supported.level)
        assertEquals("c8", supported.collector?.id)
        assertEquals(1, supported.artifactVersion)
        assertEquals(emptyList(), supported.diagnostics)

        val unsupported = PropertyCoverageCapability.unsupported(
            backendId = "other-backend",
            backendVersion = "1.0.0",
            diagnostic = CoverageDiagnostic(
                code = "coverage.unsupported",
                message = "The backend does not collect source coverage",
                path = "coverageRequest",
            ),
        )

        assertEquals(CoverageCapabilityLevel.UNSUPPORTED, unsupported.level)
        assertNull(unsupported.collector)
        assertNull(unsupported.artifactVersion)
        assertEquals("coverage.unsupported", unsupported.diagnostics.single().code)
    }

    @Test
    fun `coverage locations and hit counters reject invalid values`() {
        assertFailsWith<IllegalArgumentException> { SourcePosition(line = 0, column = 0) }
        assertFailsWith<IllegalArgumentException> { SourcePosition(line = 1, column = -1) }
        assertFailsWith<IllegalArgumentException> {
            SourceRange(
                start = SourcePosition(line = 2, column = 0),
                end = SourcePosition(line = 1, column = 0),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            StatementCoverage(
                statementId = 0,
                location = range,
                hits = -1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BranchCoverage(
                branchId = 0,
                type = "if",
                location = range,
                arms = emptyList(),
            )
        }
    }

    @Test
    fun `run result requires matching coverage identity`() {
        val artifact = artifact()

        successfulResult(coverage = artifact)

        assertFailsWith<IllegalArgumentException> {
            successfulResult(
                coverage = artifact.copy(propertyId = PropertyId("different.property")),
            )
        }
    }

    private fun artifact() = PropertyCoverageArtifact(
        schemaVersion = PROPERTY_COVERAGE_ARTIFACT_VERSION,
        kind = CoverageArtifactKind.NODE_SOURCE,
        backendId = "fast-check",
        backendVersion = "4.9.0",
        propertyId = PropertyId("example.property"),
        provenance = CoverageProvenance(
            collector = CoverageCollectorIdentity(
                id = "c8",
                version = "10.1.3",
            ),
            runtimeId = "node",
            runtimeVersion = "v22.14.0",
            sourceRoots = listOf("/workspace/src"),
            request = PropertyCoverageRequest(),
        ),
        files = listOf(
            SourceFileCoverage(
                path = "/workspace/src/example.ts",
                statements = listOf(StatementCoverage(statementId = 0, location = range, hits = 1)),
                functions = listOf(
                    FunctionCoverage(
                        functionId = 0,
                        name = "example",
                        declaration = range,
                        body = range,
                        hits = 1,
                    ),
                ),
                branches = listOf(
                    BranchCoverage(
                        branchId = 0,
                        type = "if",
                        location = range,
                        arms = listOf(BranchArmCoverage(location = range, hits = 1)),
                    ),
                ),
            ),
        ),
    )

    private fun successfulResult(coverage: PropertyCoverageArtifact?) = PropertyRunResult(
        propertyId = PropertyId("example.property"),
        status = PropertyRunStatus.SUCCESS,
        seed = 42,
        replayPath = null,
        counterexample = null,
        numRuns = 1,
        numSkips = 0,
        numShrinks = 0,
        failure = null,
        executionTimeMillis = 1,
        coverage = coverage,
    )

    private companion object {
        val range = SourceRange(
            start = SourcePosition(line = 1, column = 0),
            end = SourcePosition(line = 1, column = 10),
        )
    }
}
