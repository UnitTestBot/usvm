package org.usvm.ts.pbt.fastcheck

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.backend.SourceFileCoverage
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FastCheckCoverageTest {
    private val backend = FastCheckBackend(
        sourceRoots = listOf(sourceRoot()),
        adapterEntryPoint = adapterEntryPoint(),
    )

    @Test
    fun `golden TypeScript statements functions and branches are isolated per property`() {
        val positive = backend.run(
            property = property(
                exportName = "coversPositive",
                domain = IntegerDomain(min = 1, max = 1),
            ),
            configuration = configuration,
        )
        val nonPositive = backend.run(
            property = property(
                exportName = "coversNonPositive",
                domain = IntegerDomain(min = -1, max = -1),
            ),
            configuration = configuration,
        )

        val positiveFile = sourceUnderTest(positive)
        val nonPositiveFile = sourceUnderTest(nonPositive)
        val positiveFunctions = positiveFile.functions.filter { function -> function.name == "classify" }
        val nonPositiveFunctions = nonPositiveFile.functions.filter { function -> function.name == "classify" }
        assertEquals(1, positiveFunctions.size, positiveFile.toString())
        assertEquals(1, nonPositiveFunctions.size, nonPositiveFile.toString())
        assertEquals(1L, positiveFunctions.single().hits)
        assertEquals(1L, nonPositiveFunctions.single().hits)
        assertEquals(listOf(5), zeroHitBranchLines(positiveFile))
        assertEquals(listOf(2), zeroHitBranchLines(nonPositiveFile))
        assertEquals(1L, statementHitsAtLine(positiveFile, line = 2))
        assertEquals(1L, statementHitsAtLine(nonPositiveFile, line = 2))
    }

    @Test
    fun `falsified property retains coverage collected before failure`() {
        val result = backend.run(
            property = property(
                exportName = "failsAfterClassifying",
                domain = IntegerDomain(min = 1, max = 1),
            ),
            configuration = configuration,
        )

        assertEquals(PropertyRunStatus.FAILURE, result.status)
        val file = sourceUnderTest(result)
        val functions = file.functions.filter { function -> function.name == "classify" }
        assertEquals(1, functions.size, file.toString())
        assertEquals(1L, functions.single().hits)
        assertEquals(listOf(5), zeroHitBranchLines(file))
    }

    private fun sourceUnderTest(result: org.usvm.ts.pbt.backend.PropertyRunResult): SourceFileCoverage {
        val artifact = assertNotNull(result.coverage)
        return artifact.files.single { file -> file.path.endsWith("properties/coverage/source-under-test.ts") }
    }

    private fun statementHitsAtLine(file: SourceFileCoverage, line: Int): Long = file.statements
        .filter { statement -> statement.location.start.line == line }
        .maxOf { statement -> statement.hits }

    private fun zeroHitBranchLines(file: SourceFileCoverage): List<Int> = file.branches
        .filter { branch -> branch.arms.all { arm -> arm.hits == 0L } }
        .map { branch -> branch.location.start.line }
        .filter { line -> line > 1 }
        .sorted()

    private fun property(exportName: String, domain: IntegerDomain) = PropertyDefinition(
        id = PropertyId("coverage.$exportName"),
        inputs = listOf(
            PropertyInput(
                name = "value",
                domain = domain,
            ),
        ),
        predicate = TypeScriptEntryPoint(
            module = "properties/coverage/CoverageProperties.ts",
            exportName = exportName,
        ),
    )

    private companion object {
        val configuration = PropertyRunConfiguration(
            seed = 42,
            numRuns = 1,
            timeoutMillis = 5_000,
            coverageRequest = PropertyCoverageRequest(),
        )

        fun adapterEntryPoint(): Path = locateFile(
            "fast-check-adapter/dist/src/execution-cli.js",
            "usvm-ts-pbt/fast-check-adapter/dist/src/execution-cli.js",
        )

        fun sourceRoot(): Path = locateDirectory(
            "src/test/resources",
            "usvm-ts-pbt/src/test/resources",
        )

        fun locateFile(vararg candidates: String): Path = candidates
            .map { candidate -> Path.of(candidate).absolute() }
            .singleOrNull(Files::isRegularFile)
            ?: error("Cannot locate file; checked ${candidates.toList()}")

        fun locateDirectory(vararg candidates: String): Path = candidates
            .map { candidate -> Path.of(candidate).absolute() }
            .singleOrNull(Files::isDirectory)
            ?: error("Cannot locate directory; checked ${candidates.toList()}")
    }
}
