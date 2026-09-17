package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
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
    fun `false precondition is rejected and unsupported calls remain explicit`() {
        val rejected = search(manifest(preconditionExport = "alwaysFalse"))
        val unsupported = search(manifest(preconditionExport = "acceptsNonPositiveOrThrows"))

        assertEquals(UsvmPropertySearchStatus.PRECONDITION_REJECTED, rejected.status)
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

    private fun search(manifest: PropertyManifest): UsvmPropertySearchResult = searcher.search(
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
        private lateinit var mapper: PropertyEtsMapper
        private lateinit var searcher: UsvmPropertySearcher

        @JvmStatic
        @BeforeAll
        fun loadFixture() {
            val source = testResourcePath("/usvm/UsvmPreconditionFixture.ts")
            val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
            val scene = EtsScene(listOf(file))

            mapper = PropertyEtsMapper(scene = scene, sourceRoots = listOf(source.parent))
            searcher = UsvmPropertySearcher(scene)
        }
    }
}
