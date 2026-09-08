package org.usvm.ts.pbt.usvm

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.isTrue
import org.usvm.machine.state.TsMethodResult
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcePath
import org.usvm.util.mkRegisterStackLValue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsvmPreconditionProjectionTest {
    @Test
    fun `mapped precondition retains only satisfiable true states inside the declared domain`() {
        val manifest = manifest(preconditionExport = "isPositive")
        val mapping = mapper.map(manifest)

        val result = projector.analyzePrecondition(manifest, mapping, exact())

        assertEquals(ProjectionLevel.EXACT, result.capability.symbolic.level)
        assertEquals(UsvmPreconditionStatus.ACCEPTED, result.status)
        assertTrue(result.acceptedStates.isNotEmpty())
        result.acceptedStates.forEach { state ->
            assertTrue(state.methodResult is TsMethodResult.Success)
            with(state.ctx) {
                val input = state.memory.read(mkRegisterStackLValue(fp64Sort, 1)).asExpr(fp64Sort)
                val value = state.models.single().eval(input)

                assertTrue(mkFpGreaterExpr(value, mkFp(0.0, fp64Sort)).isTrue)
                assertTrue(mkFpLessOrEqualExpr(value, mkFp(3.0, fp64Sort)).isTrue)
            }
        }
    }

    @Test
    fun `false-only states are rejected and unsupported exception construction is explicit`() {
        val falseManifest = manifest(preconditionExport = "alwaysFalse")
        val throwingManifest = manifest(preconditionExport = "acceptsNonPositiveOrThrows")

        val falseResult = projector.analyzePrecondition(falseManifest, mapper.map(falseManifest), exact())
        val throwingResult = projector.analyzePrecondition(throwingManifest, mapper.map(throwingManifest), exact())

        assertEquals(emptyList(), falseResult.acceptedStates)
        assertEquals(UsvmPreconditionStatus.REJECTED, falseResult.status)
        assertEquals(emptyList(), throwingResult.acceptedStates)
        assertEquals(UsvmPreconditionStatus.UNSUPPORTED, throwingResult.status)
        assertTrue(throwingResult.diagnostics.any { it.code == "usvm.execution.unsupported" })
    }

    @Test
    fun `async preconditions are unsupported and non-boolean preconditions are property errors`() {
        val asyncManifest = manifest(
            preconditionExport = "asyncIsPositive",
            executionKind = ExecutionKind.ASYNC,
        )
        val nonBooleanManifest = manifest(preconditionExport = "returnsNumber")

        val asyncResult = projector.analyzePrecondition(asyncManifest, mapper.map(asyncManifest), exact())
        val nonBooleanResult = projector.analyzePrecondition(
            nonBooleanManifest,
            mapper.map(nonBooleanManifest),
            exact(),
        )

        assertEquals(ProjectionLevel.UNSUPPORTED, asyncResult.capability.precondition.level)
        assertEquals(UsvmPreconditionStatus.UNSUPPORTED, asyncResult.status)
        assertEquals(emptyList(), asyncResult.acceptedStates)
        assertEquals(ProjectionLevel.EXACT, nonBooleanResult.capability.precondition.level)
        assertEquals(UsvmPreconditionStatus.PROPERTY_ERROR, nonBooleanResult.status)
        assertEquals(emptyList(), nonBooleanResult.acceptedStates)
        assertTrue(nonBooleanResult.diagnostics.any { it.code == "usvm.precondition.result.non-boolean" })
    }

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

    private fun exact() = ProjectionCapability(level = ProjectionLevel.EXACT)

    companion object {
        private lateinit var mapper: PropertyEtsMapper
        private lateinit var projector: UsvmPropertyProjector

        @JvmStatic
        @BeforeAll
        fun loadFixture() {
            val source = testResourcePath("/usvm/UsvmPreconditionFixture.ts")
            val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
            val scene = EtsScene(listOf(file))
            mapper = PropertyEtsMapper(scene = scene, sourceRoots = listOf(source.parent))
            projector = UsvmPropertyProjector(scene)
        }
    }
}
