package org.usvm.ts.pbt.mapping

import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.BranchArmCoverage
import org.usvm.ts.pbt.backend.BranchCoverage
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
import org.usvm.ts.pbt.testResourcePath
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertyEtsMapperTest {
    @Test
    fun `maps an exported predicate and its symbolic bindings`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val manifest = PropertyManifest(
            propertyId = "mapping.positive",
            inputs = listOf(
                PropertyInput(
                    name = "value",
                    domain = IntegerDomain(min = -10, max = 10),
                ),
            ),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest)

        assertEquals(PropertyId("mapping.positive"), artifact.propertyId)
        assertEquals(EtsMappingStatus.EXACT, artifact.predicate.status)
        val target = artifact.predicate.targets.single()
        val inputBinding = target.bindings.inputs.single()
        assertEquals("isPositive", target.method.name)
        assertEquals(0, target.bindings.receiver.stackSlot)
        assertEquals("value", inputBinding.propertyInputName)
        assertEquals(0, inputBinding.parameter.index)
        assertEquals(1, inputBinding.stackSlot)
        assertEquals(target.method.returnType, target.bindings.result.type)
    }

    @Test
    fun `maps an optional precondition independently from the predicate`() {
        val predicateSource = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val preconditionSource = testResourcePath("/mapping/PropertyPreconditionFixture.ts")
        val files = listOf(predicateSource, preconditionSource).map { source ->
            loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        }
        val manifest = PropertyManifest(
            propertyId = "mapping.precondition",
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
            precondition = TypeScriptEntryPoint(
                module = "PropertyPreconditionFixture.ts",
                exportName = "isNonZero",
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(files),
            sourceRoots = listOf(predicateSource.parent),
        )

        val artifact = mapper.map(manifest)

        val precondition = assertNotNull(artifact.precondition)
        assertEquals(EtsMappingStatus.EXACT, precondition.status)
        assertEquals("isNonZero", precondition.targets.single().method.name)
        val predicateTarget = artifact.predicate.targets.single()
        assertEquals("isPositive", predicateTarget.method.name)
    }

    @Test
    fun `reports an unmapped predicate instead of guessing or throwing`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val manifest = PropertyManifest(
            propertyId = "mapping.missing",
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "missingPredicate",
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest)

        assertEquals(EtsMappingStatus.UNMAPPED, artifact.predicate.status)
        assertEquals(emptyList(), artifact.predicate.targets)
        assertEquals("mapping.entry-point.unmapped", artifact.predicate.diagnostics.single().code)
    }

    @Test
    fun `reports ambiguous predicate candidates across source roots`() {
        val primarySource = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val duplicateSource = testResourcePath("/mapping/duplicate/PropertyMappingFixture.ts")
        val files = listOf(primarySource, duplicateSource).map { source ->
            loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        }
        val manifest = PropertyManifest(
            propertyId = "mapping.ambiguous",
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(files),
            sourceRoots = listOf(primarySource.parent, duplicateSource.parent),
        )

        val artifact = mapper.map(manifest)

        assertEquals(EtsMappingStatus.AMBIGUOUS, artifact.predicate.status)
        assertEquals(2, artifact.predicate.targets.size)
        assertEquals("mapping.entry-point.ambiguous", artifact.predicate.diagnostics.single().code)
    }

    @Test
    fun `duplicate frontend signatures keep coverage source provenance ambiguous`() {
        val sourceRoot = testResourcePath("/mapping/source-roots")
        val primarySource = sourceRoot.resolve("a/Foo.ts")
        val duplicateSource = sourceRoot.resolve("b/Foo.ts")
        val primaryFile = loadEtsFileAutoConvert(primarySource, provider = EtsIrProvider.TS_FRONTEND)
        val duplicateFile = loadEtsFileAutoConvert(duplicateSource, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.duplicate-source-provenance")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "Foo.ts",
                exportName = "predicate",
            ),
        )
        val branchLocation = SourceRange(
            start = SourcePosition(line = 2, column = 2),
            end = SourcePosition(line = 6, column = 3),
        )
        val coverage = coverageArtifact(
            source = primarySource,
            propertyId = propertyId,
            statements = listOf(
                StatementCoverage(
                    statementId = 0,
                    location = SourceRange(
                        start = SourcePosition(line = 3, column = 4),
                        end = SourcePosition(line = 3, column = 16),
                    ),
                    hits = 1,
                ),
            ),
            branches = listOf(
                BranchCoverage(
                    branchId = 0,
                    type = "if",
                    location = branchLocation,
                    arms = listOf(
                        BranchArmCoverage(location = branchLocation, hits = 1),
                        BranchArmCoverage(location = branchLocation, hits = 0),
                    ),
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(primaryFile, duplicateFile)),
            sourceRoots = listOf(primarySource.parent, duplicateSource.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val mapping = artifact.coverage.statements.single().mapping
        assertEquals(EtsMappingStatus.AMBIGUOUS, mapping.status)
        assertTrue(
            mapping.targets.any { target ->
                duplicateFile.classes
                    .flatMap { etsClass -> etsClass.methods }
                    .any { method -> target.statement.location.method === method }
            },
        )
        assertEquals("mapping.statement.ambiguous", mapping.diagnostics.single().code)
        val branchMapping = artifact.coverage.branches.single().mapping
        assertEquals(EtsMappingStatus.AMBIGUOUS, branchMapping.status)
        assertEquals("mapping.branch.ambiguous", branchMapping.diagnostics.single().code)
    }

    @Test
    fun `reports unsupported bindings when an ambiguous candidate has another arity`() {
        val primarySource = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val mismatchedSource = testResourcePath("/mapping/mismatched/PropertyMappingFixture.ts")
        val files = listOf(primarySource, mismatchedSource).map { source ->
            loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        }
        val manifest = PropertyManifest(
            propertyId = "mapping.ambiguous-arity",
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(files),
            sourceRoots = listOf(primarySource.parent, mismatchedSource.parent),
        )

        val artifact = mapper.map(manifest)

        assertEquals(EtsMappingStatus.UNSUPPORTED, artifact.predicate.status)
        assertEquals(emptyList(), artifact.predicate.targets)
        assertEquals("mapping.entry-point.bindings.unsupported", artifact.predicate.diagnostics.single().code)
    }

    @Test
    fun `reports unsupported bindings when property inputs do not match parameters`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val manifest = PropertyManifest(
            propertyId = "mapping.unsupported-bindings",
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "needsTwoInputs",
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest)

        assertEquals(EtsMappingStatus.UNSUPPORTED, artifact.predicate.status)
        assertEquals(emptyList(), artifact.predicate.targets)
        assertEquals("mapping.entry-point.bindings.unsupported", artifact.predicate.diagnostics.single().code)
    }

    @Test
    fun `produces an unsupported coverage mapping when the backend returned no coverage`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val manifest = PropertyManifest(
            propertyId = "mapping.no-coverage",
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest)

        assertEquals(EtsMappingStatus.UNSUPPORTED, artifact.coverage.status)
        assertNull(artifact.coverage.backendProvenance)
        assertEquals("mapping.coverage.unavailable", artifact.coverage.diagnostics.single().code)
    }

    @Test
    fun `does not map coverage produced for another property`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val manifest = PropertyManifest(
            propertyId = "mapping.expected-property",
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = PropertyId("mapping.other-property"),
            statements = emptyList(),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        assertEquals(PropertyId("mapping.expected-property"), artifact.propertyId)
        assertEquals(EtsMappingStatus.UNSUPPORTED, artifact.coverage.status)
        assertEquals(coverage.provenance, artifact.coverage.backendProvenance)
        assertEquals(emptyList(), artifact.coverage.statements)
        assertEquals(emptyList(), artifact.coverage.branches)
        assertEquals("mapping.coverage.property-id.mismatch", artifact.coverage.diagnostics.single().code)
    }
}

class PropertyEtsStatementMappingTest {
    @Test
    fun `normalizes a TypeScript statement location and maps it to its EtsIR origin`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.statement")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = listOf(
                StatementCoverage(
                    statementId = 0,
                    location = SourceRange(
                        start = SourcePosition(line = 3, column = 2),
                        end = SourcePosition(line = 3, column = 19),
                    ),
                    hits = 1,
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        assertEquals(coverage.provenance, artifact.coverage.backendProvenance)
        assertEquals(
            listOf(source.parent.toAbsolutePath().normalize().toString()),
            artifact.provenance.sourceRoots,
        )
        assertEquals(EtsSourceCoordinateSystem.TYPESCRIPT_UTF16_ZERO_BASED_HALF_OPEN, artifact.provenance.coordinates)
        assertEquals(EtsBranchSuccessorOrder.TRUE_FALSE, artifact.provenance.branchSuccessorOrder)
        val statement = artifact.coverage.statements.single()
        val location = assertNotNull(statement.location)
        assertEquals(source.toAbsolutePath().normalize().toString(), location.path)
        assertEquals(NormalizedSourcePosition(line = 2, column = 2, offset = 82), location.start)
        assertEquals(NormalizedSourcePosition(line = 2, column = 19, offset = 99), location.end)
        assertEquals(EtsMappingStatus.EXACT, statement.mapping.status)
        assertTrue(statement.mapping.targets.size > 1, "Normalized EtsIR statements must retain their shared span")
        statement.mapping.targets.forEach { target ->
            val origin = assertNotNull(target.statement.location.origin)
            assertEquals("ReturnStatement", origin.nodeKind)
            assertEquals(82, origin.startOffset)
            assertEquals(99, origin.endOffset)
        }
    }

    @Test
    fun `reports a source range containing distinct EtsIR spans as ambiguous`() {
        val source = testResourcePath("/mapping/BranchMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.ambiguous-statement")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "BranchMappingFixture.ts",
                exportName = "classifiesPositive",
            ),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = listOf(
                StatementCoverage(
                    statementId = 0,
                    location = SourceRange(
                        start = SourcePosition(line = 2, column = 1),
                        end = SourcePosition(line = 7, column = 0),
                    ),
                    hits = 1,
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val statement = artifact.coverage.statements.single()
        assertEquals(EtsMappingStatus.AMBIGUOUS, statement.mapping.status)
        assertTrue(statement.mapping.targets.isNotEmpty())
        assertEquals("mapping.statement.ambiguous", statement.mapping.diagnostics.single().code)
    }

    @Test
    fun `reports a valid source range without an EtsIR statement as unmapped`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.unmapped-statement")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = listOf(
                StatementCoverage(
                    statementId = 0,
                    location = SourceRange(
                        start = SourcePosition(line = 5, column = 0),
                        end = SourcePosition(line = 5, column = 0),
                    ),
                    hits = 0,
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val statement = artifact.coverage.statements.single()
        assertEquals(EtsMappingStatus.UNMAPPED, statement.mapping.status)
        assertEquals(emptyList(), statement.mapping.targets)
        assertEquals("mapping.statement.unmapped", statement.mapping.diagnostics.single().code)
    }
}

class PropertyEtsBranchMappingTest {
    @Test
    fun `maps an Istanbul if branch to ordered true and false EtsIR edges`() {
        val source = testResourcePath("/mapping/BranchMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.branch")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "BranchMappingFixture.ts",
                exportName = "classifiesPositive",
            ),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = emptyList(),
            branches = listOf(
                BranchCoverage(
                    branchId = 0,
                    type = "if",
                    location = SourceRange(
                        start = SourcePosition(line = 2, column = 2),
                        end = SourcePosition(line = 6, column = 3),
                    ),
                    arms = listOf(
                        BranchArmCoverage(
                            location = SourceRange(
                                start = SourcePosition(line = 2, column = 2),
                                end = SourcePosition(line = 4, column = 3),
                            ),
                            hits = 5,
                        ),
                        BranchArmCoverage(
                            location = SourceRange(
                                start = SourcePosition(line = 4, column = 4),
                                end = SourcePosition(line = 6, column = 3),
                            ),
                            hits = 2,
                        ),
                    ),
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val branch = artifact.coverage.branches.single()
        assertEquals(EtsMappingStatus.EXACT, branch.mapping.status)
        assertEquals(listOf(5L, 2L), branch.arms.map { arm -> arm.coverage.hits })
        assertEquals(listOf(true, false), branch.arms.map { arm -> arm.mapping.targets.single().outcome })
        val successorLines = branch.arms.map { arm ->
            val target = arm.mapping.targets.single()
            val origin = assertNotNull(target.successor.location.origin)

            origin.startLine
        }
        assertEquals(listOf(2, 4), successorLines)
    }

    @Test
    fun `reports a branch range without an EtsIR condition as unmapped`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.unmapped-branch")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
        )
        val branchLocation = SourceRange(
            start = SourcePosition(line = 3, column = 2),
            end = SourcePosition(line = 3, column = 19),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = emptyList(),
            branches = listOf(
                BranchCoverage(
                    branchId = 0,
                    type = "if",
                    location = branchLocation,
                    arms = listOf(
                        BranchArmCoverage(location = branchLocation, hits = 1),
                        BranchArmCoverage(location = branchLocation, hits = 0),
                    ),
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val branch = artifact.coverage.branches.single()
        assertEquals(EtsMappingStatus.UNMAPPED, branch.mapping.status)
        assertEquals(emptyList(), branch.mapping.targets)
        assertEquals("mapping.branch.unmapped", branch.mapping.diagnostics.single().code)
        assertTrue(branch.arms.all { arm -> arm.mapping.status == EtsMappingStatus.UNMAPPED })
    }

    @Test
    fun `reports a branch range containing distinct EtsIR conditions as ambiguous`() {
        val source = testResourcePath("/mapping/AmbiguousBranchMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.ambiguous-branch")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "AmbiguousBranchMappingFixture.ts",
                exportName = "classifiesLargePositive",
            ),
        )
        val branchLocation = SourceRange(
            start = SourcePosition(line = 2, column = 2),
            end = SourcePosition(line = 6, column = 3),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = emptyList(),
            branches = listOf(
                BranchCoverage(
                    branchId = 0,
                    type = "if",
                    location = branchLocation,
                    arms = listOf(
                        BranchArmCoverage(location = branchLocation, hits = 1),
                        BranchArmCoverage(location = branchLocation, hits = 0),
                    ),
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val branch = artifact.coverage.branches.single()
        assertEquals(EtsMappingStatus.AMBIGUOUS, branch.mapping.status)
        assertEquals(2, branch.mapping.targets.size)
        assertEquals("mapping.branch.ambiguous", branch.mapping.diagnostics.single().code)
        assertTrue(branch.arms.all { arm -> arm.mapping.targets.size == 2 })
    }
}

class PropertyEtsUnsupportedMappingTest {
    @Test
    fun `reports unsupported source mapping when covered source text is unavailable`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val missingSource = source.resolveSibling("MissingMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.missing-source")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
        )
        val coverage = coverageArtifact(
            source = source,
            coveragePath = missingSource,
            propertyId = propertyId,
            statements = listOf(
                StatementCoverage(
                    statementId = 0,
                    location = SourceRange(
                        start = SourcePosition(line = 1, column = 0),
                        end = SourcePosition(line = 1, column = 1),
                    ),
                    hits = 0,
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val statement = artifact.coverage.statements.single()
        assertNull(statement.location)
        assertEquals(EtsMappingStatus.UNSUPPORTED, statement.mapping.status)
        assertEquals("mapping.source.unavailable", statement.mapping.diagnostics.single().code)
    }

    @Test
    fun `reports unsupported mapping when the EtsIR frontend supplied no source origins`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        file.allClasses
            .flatMap { etsClass -> etsClass.methods }
            .flatMap { method -> method.cfg.stmts }
            .forEach { statement -> statement.location.origin = null }
        val propertyId = PropertyId("mapping.no-origins")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = listOf(
                StatementCoverage(
                    statementId = 0,
                    location = SourceRange(
                        start = SourcePosition(line = 3, column = 2),
                        end = SourcePosition(line = 3, column = 19),
                    ),
                    hits = 1,
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val statement = artifact.coverage.statements.single()
        assertEquals(EtsMappingStatus.UNSUPPORTED, statement.mapping.status)
        assertEquals("mapping.source-origins.unsupported", statement.mapping.diagnostics.single().code)
    }

    @Test
    fun `reports unsupported branch mapping when the EtsIR frontend supplied no source origins`() {
        val source = testResourcePath("/mapping/BranchMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        file.allClasses
            .flatMap { etsClass -> etsClass.methods }
            .flatMap { method -> method.cfg.stmts }
            .forEach { statement -> statement.location.origin = null }
        val propertyId = PropertyId("mapping.branch-no-origins")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "BranchMappingFixture.ts",
                exportName = "classifiesPositive",
            ),
        )
        val branchLocation = SourceRange(
            start = SourcePosition(line = 2, column = 2),
            end = SourcePosition(line = 6, column = 3),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = emptyList(),
            branches = listOf(
                BranchCoverage(
                    branchId = 0,
                    type = "if",
                    location = branchLocation,
                    arms = listOf(
                        BranchArmCoverage(location = branchLocation, hits = 1),
                        BranchArmCoverage(location = branchLocation, hits = 0),
                    ),
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val branch = artifact.coverage.branches.single()
        assertEquals(EtsMappingStatus.UNSUPPORTED, branch.mapping.status)
        assertEquals("mapping.source-origins.unsupported", branch.mapping.diagnostics.single().code)
        assertTrue(branch.arms.all { arm -> arm.mapping.status == EtsMappingStatus.UNSUPPORTED })
    }

    @Test
    fun `reports unsupported non-if branch types even when they have two arms`() {
        val source = testResourcePath("/mapping/BranchMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.unsupported-branch")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "BranchMappingFixture.ts",
                exportName = "classifiesPositive",
            ),
        )
        val branchLocation = SourceRange(
            start = SourcePosition(line = 2, column = 2),
            end = SourcePosition(line = 6, column = 3),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = emptyList(),
            branches = listOf(
                BranchCoverage(
                    branchId = 0,
                    type = "switch",
                    location = branchLocation,
                    arms = listOf(
                        BranchArmCoverage(location = branchLocation, hits = 1),
                        BranchArmCoverage(location = branchLocation, hits = 1),
                    ),
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val branch = artifact.coverage.branches.single()
        assertEquals(EtsMappingStatus.UNSUPPORTED, branch.mapping.status)
        assertEquals(emptyList(), branch.mapping.targets)
        assertEquals("mapping.branch.shape.unsupported", branch.mapping.diagnostics.single().code)
        assertTrue(branch.arms.all { arm -> arm.mapping.status == EtsMappingStatus.UNSUPPORTED })
    }

    @Test
    fun `reports unsupported mapping when an EtsIR condition has fewer than two successors`() {
        val source = testResourcePath("/mapping/BranchMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val condition = file.allClasses
            .flatMap { etsClass -> etsClass.methods }
            .flatMap { method -> method.cfg.stmts }
            .filterIsInstance<EtsIfStmt>()
            .single()
        val method = condition.location.method
        val originalCfg = method.cfg
        val conditionBlock = originalCfg.blocks.single { block -> condition in block.statements }
        val conditionSuccessors = originalCfg.successors.getValue(conditionBlock.id)
        method.body.cfg = EtsBlockCfg(
            blocks = originalCfg.blocks,
            successors = originalCfg.successors + (conditionBlock.id to conditionSuccessors.take(1)),
        )
        val propertyId = PropertyId("mapping.unsupported-cfg-branch")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "BranchMappingFixture.ts",
                exportName = "classifiesPositive",
            ),
        )
        val branchLocation = SourceRange(
            start = SourcePosition(line = 2, column = 2),
            end = SourcePosition(line = 6, column = 3),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = emptyList(),
            branches = listOf(
                BranchCoverage(
                    branchId = 0,
                    type = "if",
                    location = branchLocation,
                    arms = listOf(
                        BranchArmCoverage(location = branchLocation, hits = 1),
                        BranchArmCoverage(location = branchLocation, hits = 0),
                    ),
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val branch = artifact.coverage.branches.single()
        assertEquals(EtsMappingStatus.UNSUPPORTED, branch.mapping.status)
        assertEquals("mapping.branch.cfg.unsupported", branch.mapping.diagnostics.single().code)
        assertTrue(branch.arms.all { arm -> arm.mapping.status == EtsMappingStatus.UNSUPPORTED })
    }
}

class PropertyEtsMappingEdgeCasesTest {
    @Test
    fun `resolves extensionless modules through a named TypeScript re-export`() {
        val entrySource = testResourcePath("/mapping/reexports/Entry.ts")
        val predicateSource = testResourcePath("/mapping/reexports/Predicate.ts")
        val files = listOf(entrySource, predicateSource).map { source ->
            loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        }
        val manifest = PropertyManifest(
            propertyId = "mapping.reexport",
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "Entry",
                exportName = "predicate",
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(files),
            sourceRoots = listOf(entrySource.parent),
        )

        val artifact = mapper.map(manifest)

        assertEquals(EtsMappingStatus.EXACT, artifact.predicate.status)
        val target = artifact.predicate.targets.single()
        val targetClass = target.method.signature.enclosingClass
        val targetFileName = targetClass.file.fileName
        assertEquals("corePredicate", target.method.name)
        assertTrue(targetFileName.endsWith("Predicate.ts"))
    }

    @Test
    fun `reports unsupported coverage coordinates outside the UTF-16 source line`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.invalid-location")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "PropertyMappingFixture.ts",
                exportName = "isPositive",
            ),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = listOf(
                StatementCoverage(
                    statementId = 0,
                    location = SourceRange(
                        start = SourcePosition(line = 3, column = 200),
                        end = SourcePosition(line = 3, column = 200),
                    ),
                    hits = 0,
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val statement = artifact.coverage.statements.single()
        assertNull(statement.location)
        assertEquals(EtsMappingStatus.UNSUPPORTED, statement.mapping.status)
        assertEquals("mapping.source.location.unsupported", statement.mapping.diagnostics.single().code)
    }

    @Test
    fun `reports an invalid branch arm without discarding the mapped condition`() {
        val source = testResourcePath("/mapping/BranchMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.invalid-branch-arm")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "BranchMappingFixture.ts",
                exportName = "classifiesPositive",
            ),
        )
        val branchLocation = SourceRange(
            start = SourcePosition(line = 2, column = 2),
            end = SourcePosition(line = 6, column = 3),
        )
        val coverage = coverageArtifact(
            source = source,
            propertyId = propertyId,
            statements = emptyList(),
            branches = listOf(
                BranchCoverage(
                    branchId = 0,
                    type = "if",
                    location = branchLocation,
                    arms = listOf(
                        BranchArmCoverage(location = branchLocation, hits = 1),
                        BranchArmCoverage(
                            location = SourceRange(
                                start = SourcePosition(line = 4, column = 200),
                                end = SourcePosition(line = 4, column = 200),
                            ),
                            hits = 0,
                        ),
                    ),
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val branch = artifact.coverage.branches.single()
        assertEquals(EtsMappingStatus.EXACT, branch.mapping.status)
        assertEquals(EtsMappingStatus.EXACT, branch.arms.first().mapping.status)
        val invalidArm = branch.arms.last()
        assertNull(invalidArm.location)
        assertEquals(EtsMappingStatus.UNSUPPORTED, invalidArm.mapping.status)
        assertEquals("mapping.source.location.unsupported", invalidArm.mapping.diagnostics.single().code)
        assertEquals(EtsMappingStatus.UNSUPPORTED, artifact.coverage.status)
    }

    @Test
    fun `reports unsupported branch mapping when covered source text is unavailable`() {
        val source = testResourcePath("/mapping/BranchMappingFixture.ts")
        val missingSource = source.resolveSibling("MissingBranchMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val propertyId = PropertyId("mapping.missing-branch-source")
        val manifest = PropertyManifest(
            propertyId = propertyId.value,
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
            predicate = TypeScriptEntryPoint(
                module = "BranchMappingFixture.ts",
                exportName = "classifiesPositive",
            ),
        )
        val location = SourceRange(
            start = SourcePosition(line = 1, column = 0),
            end = SourcePosition(line = 1, column = 1),
        )
        val coverage = coverageArtifact(
            source = source,
            coveragePath = missingSource,
            propertyId = propertyId,
            statements = emptyList(),
            branches = listOf(
                BranchCoverage(
                    branchId = 0,
                    type = "if",
                    location = location,
                    arms = listOf(
                        BranchArmCoverage(location = location, hits = 0),
                        BranchArmCoverage(location = location, hits = 0),
                    ),
                ),
            ),
        )
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val artifact = mapper.map(manifest, coverage)

        val branch = artifact.coverage.branches.single()
        assertNull(branch.location)
        assertEquals(EtsMappingStatus.UNSUPPORTED, branch.mapping.status)
        assertEquals("mapping.source.unavailable", branch.mapping.diagnostics.single().code)
        assertTrue(
            branch.arms.all { arm ->
                arm.location == null && arm.mapping.status == EtsMappingStatus.UNSUPPORTED
            },
        )
    }
}

private fun coverageArtifact(
    source: Path,
    coveragePath: Path = source,
    propertyId: PropertyId,
    statements: List<StatementCoverage>,
    branches: List<BranchCoverage> = emptyList(),
): PropertyCoverageArtifact = PropertyCoverageArtifact(
    backendId = "fixture-backend",
    backendVersion = "1.0",
    propertyId = propertyId,
    provenance = CoverageProvenance(
        collector = CoverageCollectorIdentity(id = "fixture", version = "1.0"),
        runtimeId = "node",
        runtimeVersion = "22.0.0",
        sourceRoots = listOf(source.parent.toString()),
        request = PropertyCoverageRequest(),
    ),
    files = listOf(
        SourceFileCoverage(
            path = coveragePath.toString(),
            statements = statements,
            functions = emptyList(),
            branches = branches,
        ),
    ),
)
