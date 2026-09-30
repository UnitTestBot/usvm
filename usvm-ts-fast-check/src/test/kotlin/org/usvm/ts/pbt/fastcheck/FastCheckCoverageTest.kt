package org.usvm.ts.pbt.fastcheck

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.backend.SourceFileCoverage
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectory
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        assertEquals(listOf(1L, 0L), exactIf(positiveFile).arms.map { arm -> arm.hits })
        assertEquals(listOf(0L, 1L), exactIf(nonPositiveFile).arms.map { arm -> arm.hits })
        assertTrue(positiveFile.branches.any { branch -> branch.type == "branch" && branch.arms.size == 1 })
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

    @Test
    fun `real backend reports mixed TypeScript branch hits`() {
        val result = backend.run(
            property = property(
                exportName = "alwaysCovers",
                domain = IntegerDomain(min = -1, max = 1),
            ),
            configuration = configuration.copy(
                numRuns = 3,
                examples = listOf(
                    listOf(JsConcreteValue.number(-1.0)),
                    listOf(JsConcreteValue.number(1.0)),
                ),
            ),
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.status)
        val armHits = exactIf(sourceUnderTest(result)).arms.map { arm -> arm.hits }
        assertEquals(3L, armHits.sum())
        assertTrue(armHits.all { hits -> hits >= 1L })
    }

    @Test
    fun `real TypeScript branch arms map to ordered EtsIR successors`() {
        val definition = property(
            exportName = "coversPositive",
            domain = IntegerDomain(min = 1, max = 1),
        )
        val result = backend.run(property = definition, configuration = configuration)
        val source = sourceRoot().resolve("properties/coverage/source-under-test.ts").toRealPath()
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val mapped = mapper.map(definition.toManifest(), assertNotNull(result.coverage))
        val branch = mapped.coverage.branches.single { candidate -> candidate.coverage.type == "if" }

        assertEquals(EtsMappingStatus.EXACT, branch.mapping.status)
        assertEquals(listOf(1L, 0L), branch.arms.map { arm -> arm.coverage.hits })
        assertEquals(listOf(true, false), branch.arms.map { arm -> arm.mapping.targets.single().outcome })
    }

    @Test
    fun `nested TypeScript branches map to distinct EtsIR conditions`() {
        val definition = property(
            exportName = "coversNested",
            domain = IntegerDomain(min = 20, max = 20),
        )
        val result = backend.run(property = definition, configuration = configuration)
        val source = sourceRoot().resolve("properties/coverage/nested-source-under-test.ts").toRealPath()
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val mapped = mapper.map(definition.toManifest(), assertNotNull(result.coverage))
        val branches = mapped.coverage.branches.filter { branch -> branch.coverage.type == "if" }

        assertEquals(2, branches.size)
        assertTrue(branches.all { branch -> branch.mapping.status == EtsMappingStatus.EXACT })
        assertEquals(listOf(1L, 0L), branches[0].arms.map { arm -> arm.coverage.hits })
        assertEquals(listOf(1L, 0L), branches[1].arms.map { arm -> arm.coverage.hits })
        val outcomes = branches.map { branch ->
            branch.arms.map { arm -> arm.mapping.targets.single().outcome }
        }
        assertEquals(listOf(listOf(true, false), listOf(true, false)), outcomes)
    }

    @Test
    fun `unsupported TypeScript branch retains exact statement coverage`() {
        val result = backend.run(
            property = property(
                exportName = "coversNonterminal",
                domain = IntegerDomain(min = 1, max = 1),
            ),
            configuration = configuration,
        )

        val coverage = assertNotNull(result.coverage)
        val file = coverage.files.single { candidate ->
            candidate.path.endsWith("properties/coverage/nonterminal-source-under-test.ts")
        }

        assertTrue(file.statements.any { statement -> statement.hits > 0 })
        assertTrue(file.branches.none { branch -> branch.type == "if" })
        assertTrue(coverage.diagnostics.any { diagnostic -> diagnostic.code == "coverage.branch.unsupported" })
    }

    @Test
    fun `real c8 reports a missing referenced source map when the final report omits the script`() {
        val module = "properties/coverage/missing-map-entry.js"

        val result = backend.run(
            property = property(
                module = module,
                exportName = "missingMapPredicate",
                domain = IntegerDomain(min = 1, max = 1),
            ),
            configuration = configuration,
        )

        val artifact = assertNotNull(result.coverage)
        val diagnostic = artifact.diagnostics.single()
        assertEquals("coverage.source-map.missing", diagnostic.code)
        assertEquals(sourceRoot().resolve(module).toRealPath().toString(), diagnostic.path)
    }

    @Test
    fun `real c8 reports an invalid referenced source map when the final report omits the script`() {
        val module = "properties/coverage/invalid-map-entry.js"

        val result = backend.run(
            property = property(
                module = module,
                exportName = "invalidMapPredicate",
                domain = IntegerDomain(min = 1, max = 1),
            ),
            configuration = configuration,
        )

        val artifact = assertNotNull(result.coverage)
        val diagnostic = artifact.diagnostics.single()
        assertEquals("coverage.source-map.invalid", diagnostic.code)
        assertEquals(sourceRoot().resolve(module).toRealPath().toString(), diagnostic.path)
    }

    @Test
    fun `symlinked entry point is retained only in the entry-point scope`() {
        val sourceRoot = createTempDirectory(prefix = "coverage-symlink-entry-")
        try {
            val realDirectory = sourceRoot.resolve("real").createDirectory()
            val realEntryPoint = realDirectory.resolve("Property.ts")
            realEntryPoint.writeText("export function predicate(value: number): boolean { return value > 0; }")
            sourceRoot.resolve("Property.ts").createSymbolicLinkPointingTo(realEntryPoint)
            val symlinkBackend = FastCheckBackend(
                sourceRoots = listOf(sourceRoot),
                adapterEntryPoint = adapterEntryPoint(),
            )
            val symlinkProperty = property(
                module = "Property.ts",
                exportName = "predicate",
                domain = IntegerDomain(min = 1, max = 1),
            )

            val sourceResult = symlinkBackend.run(
                property = symlinkProperty,
                configuration = configuration,
            )
            val entryPointResult = symlinkBackend.run(
                property = symlinkProperty,
                configuration = configuration.copy(
                    coverageRequest = PropertyCoverageRequest(
                        scopes = setOf(CoverageScope.PROPERTY_ENTRY_POINTS),
                    ),
                ),
            )

            assertTrue(assertNotNull(sourceResult.coverage).files.isEmpty())
            assertEquals(
                listOf(realEntryPoint.toRealPath().toString()),
                assertNotNull(entryPointResult.coverage).files.map { file -> file.path },
            )
        } finally {
            sourceRoot.toFile().deleteRecursively()
        }
    }

    private fun sourceUnderTest(result: PropertyRunResult): SourceFileCoverage {
        val artifact = assertNotNull(result.coverage)
        return artifact.files.single { file -> file.path.endsWith("properties/coverage/source-under-test.ts") }
    }

    private fun exactIf(file: SourceFileCoverage) = file.branches.single { branch -> branch.type == "if" }

    private fun statementHitsAtLine(file: SourceFileCoverage, line: Int): Long = file.statements
        .filter { statement -> statement.location.start.line == line }
        .maxOf { statement -> statement.hits }

    private fun zeroHitBranchLines(file: SourceFileCoverage): List<Int> = file.branches
        .filter { branch -> branch.arms.all { arm -> arm.hits == 0L } }
        .map { branch -> branch.location.start.line }
        .filter { line -> line > 1 }
        .sorted()

    private fun property(
        exportName: String,
        domain: IntegerDomain,
        module: String = "properties/coverage/CoverageProperties.ts",
    ) = PropertyDefinition(
        id = PropertyId("coverage.$exportName"),
        inputs = listOf(
            PropertyInput(
                name = "value",
                domain = domain,
            ),
        ),
        predicate = TypeScriptEntryPoint(
            module = module,
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
            "usvm-ts-fast-check/fast-check-adapter/dist/src/execution-cli.js",
        )

        fun sourceRoot(): Path = locateDirectory(
            "src/test/resources",
            "usvm-ts-fast-check/src/test/resources",
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
