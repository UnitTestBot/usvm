package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsvmPreconditionSearchTest {
    @Test
    fun `mapped precondition restricts the declared domain before predicate search`() {
        val result = search(manifest(preconditionExport = "isPositive"))

        assertEquals(ProjectionLevel.EXACT, result.capability.symbolic.level)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, result.status)
    }

    @Test
    fun `precondition search distinguishes rejection modeled errors and disabled calls`() {
        val errorPrecondition = manifest(preconditionExport = "acceptsNonPositiveOrThrows")
        val searcherWithoutModels = UsvmPropertySearcher(
            scene = scene,
            tsOptions = TsOptions(unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet())),
        )

        val rejected = search(manifest(preconditionExport = "alwaysFalse"))
        val modeled = search(errorPrecondition)
        val unsupported = search(manifest = errorPrecondition, propertySearcher = searcherWithoutModels)

        assertEquals(UsvmPropertySearchStatus.PRECONDITION_REJECTED, rejected.status)
        assertEquals(UsvmPropertySearchStatus.PROPERTY_ERROR, modeled.status)
        assertTrue(modeled.diagnostics.any { it.code == "usvm.precondition.threw" })
        assertEquals(UsvmPropertyViolationTarget.PREDICATE_FALSE, modeled.target)
        assertEquals(listOf(JsConcreteValue.number(0.0)), modeled.inputs)
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, unsupported.status)
        assertTrue(unsupported.diagnostics.any { it.code == "usvm.execution.unsupported" })
    }

    @Test
    fun `async and non-boolean preconditions retain distinct classifications`() {
        val async = search(
            manifest(
                preconditionExport = "asyncIsPositive",
                executionKind = ExecutionKind.ASYNC,
            ),
        )
        val nonBoolean = search(manifest(preconditionExport = "returnsNumber"))

        assertEquals(ProjectionLevel.UNSUPPORTED, async.capability.precondition.level)
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, async.status)
        assertEquals(UsvmPropertySearchStatus.PROPERTY_ERROR, nonBoolean.status)
        assertTrue(nonBoolean.diagnostics.any { it.code == "usvm.precondition.result.non-boolean" })
    }

    private fun search(
        manifest: PropertyManifest,
        propertySearcher: UsvmPropertySearcher = searcher,
    ): UsvmPropertySearchResult = propertySearcher.search(
        manifest = manifest,
        mapping = mapper.map(manifest),
        concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
    )

    private fun manifest(
        preconditionExport: String,
        executionKind: ExecutionKind = ExecutionKind.SYNC,
    ) = PropertyManifest(
        propertyId = "usvm.precondition.$preconditionExport.${executionKind.name.lowercase()}",
        inputs = listOf(
            PropertyInput(
                name = "value",
                domain = IntegerDomain(min = -2, max = 3),
            ),
        ),
        predicate = TypeScriptEntryPoint(
            module = "UsvmPreconditionFixture.ts",
            exportName = "predicate",
        ),
        precondition = TypeScriptEntryPoint(
            module = "UsvmPreconditionFixture.ts",
            exportName = preconditionExport,
            executionKind = executionKind,
        ),
    )

    companion object {
        private lateinit var scene: EtsScene
        private lateinit var mapper: PropertyEtsMapper
        private lateinit var searcher: UsvmPropertySearcher

        @JvmStatic
        @BeforeAll
        fun loadFixture() {
            val source = testResourcePath("/usvm/UsvmPreconditionFixture.ts")
            val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
            scene = EtsScene(listOf(file))

            mapper = PropertyEtsMapper(scene = scene, sourceRoots = listOf(source.parent))
            searcher = UsvmPropertySearcher(scene)
        }
    }
}
