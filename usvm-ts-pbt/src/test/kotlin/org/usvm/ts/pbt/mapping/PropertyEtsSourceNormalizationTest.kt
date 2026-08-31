package org.usvm.ts.pbt.mapping

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.backend.CoverageCollectorIdentity
import org.usvm.ts.pbt.backend.CoverageProvenance
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.SourceFileCoverage
import org.usvm.ts.pbt.backend.SourcePosition
import org.usvm.ts.pbt.backend.SourceRange
import org.usvm.ts.pbt.backend.StatementCoverage
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PropertyEtsSourceNormalizationTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `missing source roots produce an unsupported entry-point diagnostic`() {
        val missingRoot = tempDirectory.resolve("missing")
        val propertyId = PropertyId("mapping.missing-root")
        val mapper = PropertyEtsMapper(
            scene = EtsScene(emptyList()),
            sourceRoots = listOf(missingRoot),
        )

        val artifact = mapper.map(manifest(propertyId, module = "Predicate.ts"))

        assertEquals(EtsMappingStatus.UNSUPPORTED, artifact.predicate.status)
        assertEquals("mapping.source-root.unsupported", artifact.predicate.diagnostics.single().code)
    }

    @Test
    fun `canonical source roots align symlinked EtsIR origins with backend coverage`() {
        val realRoot = Files.createDirectory(tempDirectory.resolve("real"))
        val symlinkRoot = Files.createSymbolicLink(tempDirectory.resolve("alias"), realRoot)
        val realSource = realRoot.resolve("Predicate.ts")
        Files.writeString(
            realSource,
            """
                export function predicate(value: number): boolean {
                  return value > 0;
                }
            """.trimIndent(),
        )
        val symlinkSource = symlinkRoot.resolve(realSource.fileName)
        val file = loadEtsFileAutoConvert(symlinkSource, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.symlink-root")
        val coverage = coverageArtifact(
            sourceRoot = realRoot,
            sourcePath = realSource.toRealPath(),
            propertyId = propertyId,
            statements = listOf(
                StatementCoverage(
                    statementId = 0,
                    location = SourceRange(
                        start = SourcePosition(line = 2, column = 2),
                        end = SourcePosition(line = 2, column = 19),
                    ),
                    hits = 1,
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(symlinkRoot),
        )

        val artifact = mapper.map(manifest(propertyId, module = "Predicate.ts"), coverage)

        assertEquals(EtsMappingStatus.EXACT, artifact.predicate.status)
        val statement = artifact.coverage.statements.single()
        assertEquals(EtsMappingStatus.EXACT, statement.mapping.status)
        assertEquals(realSource.toRealPath().toString(), statement.location?.path)
    }

    @Test
    fun `TypeScript line terminators produce UTF-16 source offsets`() {
        val source = tempDirectory.resolve("LineTerminators.ts")
        Files.writeString(source, "a\r\nb\rc\u2028d\u2029e")
        val propertyId = PropertyId("mapping.line-terminators")
        val statements = listOf(
            statement(statementId = 0, line = 2),
            statement(statementId = 1, line = 3),
            statement(statementId = 2, line = 4),
            statement(statementId = 3, line = 5),
        )
        val coverage = coverageArtifact(
            sourceRoot = tempDirectory,
            sourcePath = source,
            propertyId = propertyId,
            statements = statements,
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(emptyList()),
            sourceRoots = listOf(tempDirectory),
        )

        val artifact = mapper.map(manifest(propertyId, module = source.fileName.toString()), coverage)

        val locations = artifact.coverage.statements.map { mapping -> assertNotNull(mapping.location) }
        assertEquals(listOf(3, 5, 7, 9), locations.map { location -> location.start.offset })
        assertEquals(listOf(4, 6, 8, 10), locations.map { location -> location.end.offset })
    }

    private fun statement(statementId: Int, line: Int): StatementCoverage = StatementCoverage(
        statementId = statementId,
        location = SourceRange(
            start = SourcePosition(line = line, column = 0),
            end = SourcePosition(line = line, column = 1),
        ),
        hits = 0,
    )

    private fun manifest(propertyId: PropertyId, module: String): PropertyManifest = PropertyManifest(
        propertyId = propertyId.value,
        inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
        predicate = TypeScriptEntryPoint(module = module, exportName = "predicate"),
    )

    private fun coverageArtifact(
        sourceRoot: Path,
        sourcePath: Path,
        propertyId: PropertyId,
        statements: List<StatementCoverage>,
    ): PropertyCoverageArtifact = PropertyCoverageArtifact(
        backendId = "fixture-backend",
        backendVersion = "1.0",
        propertyId = propertyId,
        provenance = CoverageProvenance(
            collector = CoverageCollectorIdentity(id = "fixture", version = "1.0"),
            runtimeId = "node",
            runtimeVersion = "22.0.0",
            sourceRoots = listOf(sourceRoot.toString()),
            request = PropertyCoverageRequest(),
        ),
        files = listOf(
            SourceFileCoverage(
                path = sourcePath.toString(),
                statements = statements,
                functions = emptyList(),
                branches = emptyList(),
            ),
        ),
    )
}
