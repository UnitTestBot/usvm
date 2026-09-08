package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.PropertyCapabilityLevel
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.EtsMappingDiagnostic
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TupleDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcePath
import kotlin.test.assertEquals

class UsvmProjectionCapabilityTest {
    @Test
    fun `reports scalar fidelity and type mismatches`() {
        val cases = listOf(
            Case(BooleanDomain, "acceptsBoolean", ProjectionLevel.EXACT, emptyList()),
            Case(IntegerDomain(min = -2, max = 3), "acceptsNumber", ProjectionLevel.EXACT, emptyList()),
            Case(NumberDomain(allowNaN = false), "acceptsNumber", ProjectionLevel.EXACT, emptyList()),
            Case(StringDomain(maxLength = 4), "acceptsString", ProjectionLevel.APPROXIMATE, listOf("inputs[0].domain")),
            Case(
                ConstantDomain(JsConcreteValue.Boolean(value = true)),
                "acceptsBoolean",
                ProjectionLevel.EXACT,
                emptyList(),
            ),
            Case(IntegerDomain(), "acceptsString", ProjectionLevel.UNSUPPORTED, listOf("inputs[0].domain")),
        )

        cases.forEach { case ->
            val capability = resolve(case.domain, case.exportName)

            assertEquals(case.level, capability.symbolic.level, case.exportName)
            assertEquals(case.diagnosticPaths, capability.symbolic.diagnostics.map { it.path }, case.exportName)
        }
    }

    @Test
    fun `recursive domains inherit the least capable nested projection`() {
        val optional = resolve(
            domain = OptionalDomain(IntegerDomain(min = 0, max = 5)),
            exportName = "acceptsOptionalNumber",
        )
        val tuple = resolve(
            domain = TupleDomain(listOf(IntegerDomain(), StringDomain(maxLength = 3))),
            exportName = "acceptsTuple",
        )
        val array = resolve(
            domain = ArrayDomain(IntegerDomain(), maxLength = 4),
            exportName = "acceptsNumberArray",
        )
        val oversizedArray = resolve(
            domain = ArrayDomain(IntegerDomain(), maxLength = 11),
            exportName = "acceptsNumberArray",
            options = UsvmProjectionOptions(maxSymbolicCollectionLength = 10),
        )

        assertEquals(ProjectionLevel.EXACT, optional.symbolic.level)
        assertEquals(ProjectionLevel.APPROXIMATE, tuple.symbolic.level)
        assertEquals(listOf("inputs[0].domain.elements[1]"), tuple.symbolic.diagnostics.map { it.path })
        assertEquals(ProjectionLevel.EXACT, array.symbolic.level)
        assertEquals(ProjectionLevel.UNSUPPORTED, oversizedArray.symbolic.level)
        assertEquals(listOf("inputs[0].domain"), oversizedArray.symbolic.diagnostics.map { it.path })
    }

    @Test
    fun `reports unsupported mapping and execution boundaries without classifying property errors`() {
        val manifest = manifest(IntegerDomain(), predicateExport = "acceptsNumber")
        val mapping = mapper.map(manifest)
        val nonExactMapping = mapping.copy(
            predicate = mapping.predicate.copy(
                status = EtsMappingStatus.UNMAPPED,
                targets = emptyList(),
                diagnostics = listOf(
                    EtsMappingDiagnostic(
                        code = "test.mapping.unmapped",
                        message = "Synthetic unmapped predicate",
                    ),
                ),
            ),
        )
        val asyncPreconditionManifest = manifest(
            domain = IntegerDomain(),
            predicateExport = "acceptsNumber",
            preconditionExport = "acceptsNumber",
            preconditionExecutionKind = ExecutionKind.ASYNC,
        )
        val nonBooleanPreconditionManifest = manifest(
            domain = IntegerDomain(),
            predicateExport = "acceptsNumber",
            preconditionExport = "returnsNumber",
        )

        val nonExact = resolver.resolve(manifest, nonExactMapping, exact())
        val async = resolver.resolve(
            asyncPreconditionManifest,
            mapper.map(asyncPreconditionManifest),
            exact(),
        )
        val nonBoolean = resolver.resolve(
            nonBooleanPreconditionManifest,
            mapper.map(nonBooleanPreconditionManifest),
            exact(),
        )

        assertEquals(ProjectionLevel.UNSUPPORTED, nonExact.symbolic.level)
        assertEquals(
            "predicate",
            nonExact.symbolic.diagnostics.single { it.code == "usvm.predicate.mapping.non-exact" }.path,
        )
        assertEquals(ProjectionLevel.UNSUPPORTED, async.precondition.level)
        assertEquals("precondition", async.precondition.diagnostics.single().path)
        assertEquals(ProjectionLevel.EXACT, nonBoolean.precondition.level)
        assertEquals(emptyList(), nonBoolean.precondition.diagnostics)
    }

    @Test
    fun `derives concrete only from a supported concrete projection`() {
        val capability = resolve(
            domain = ArrayDomain(IntegerDomain(), maxLength = 11),
            exportName = "acceptsNumberArray",
            concreteCapability = exact(),
            options = UsvmProjectionOptions(maxSymbolicCollectionLength = 10),
        )

        assertEquals(PropertyCapabilityLevel.CONCRETE_ONLY, capability.property)
    }

    private fun resolve(
        domain: org.usvm.ts.pbt.model.PropertyDomain,
        exportName: String,
        concreteCapability: ProjectionCapability = exact(),
        options: UsvmProjectionOptions = UsvmProjectionOptions(),
    ): UsvmPropertyProjectionCapability {
        val manifest = manifest(domain, predicateExport = exportName)

        return resolver.resolve(
            manifest = manifest,
            mapping = mapper.map(manifest),
            concreteCapability = concreteCapability,
            options = options,
        )
    }

    private fun manifest(
        domain: org.usvm.ts.pbt.model.PropertyDomain,
        predicateExport: String,
        preconditionExport: String? = null,
        preconditionExecutionKind: ExecutionKind = ExecutionKind.SYNC,
    ) = PropertyManifest(
        propertyId = "usvm.capability.$predicateExport",
        inputs = listOf(PropertyInput(name = "value", domain = domain)),
        predicate = TypeScriptEntryPoint(
            module = "UsvmCapabilityFixture.ts",
            exportName = predicateExport,
        ),
        precondition = preconditionExport?.let { exportName ->
            TypeScriptEntryPoint(
                module = "UsvmCapabilityFixture.ts",
                exportName = exportName,
                executionKind = preconditionExecutionKind,
            )
        },
    )

    private fun exact() = ProjectionCapability(level = ProjectionLevel.EXACT)

    private data class Case(
        val domain: org.usvm.ts.pbt.model.PropertyDomain,
        val exportName: String,
        val level: ProjectionLevel,
        val diagnosticPaths: List<String>,
    )

    companion object {
        private lateinit var mapper: PropertyEtsMapper
        private val resolver = UsvmProjectionCapabilityResolver()

        @JvmStatic
        @BeforeAll
        fun loadFixture() {
            val source = testResourcePath("/usvm/UsvmCapabilityFixture.ts")
            val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)

            mapper = PropertyEtsMapper(
                scene = EtsScene(listOf(file)),
                sourceRoots = listOf(source.parent),
            )
        }
    }
}
