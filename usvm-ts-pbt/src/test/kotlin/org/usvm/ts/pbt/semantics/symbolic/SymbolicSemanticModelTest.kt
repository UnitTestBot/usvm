package org.usvm.ts.pbt.semantics.symbolic

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@EnabledIfEnvironmentVariable(named = "ETS_FRONTEND_DIR", matches = ".+")
class SymbolicSemanticModelTest {
    private fun loadScene(): EtsScene {
        val root = Paths.get(
            requireNotNull(javaClass.getResource("/symbolic-semantics/collections")).toURI(),
        )
        return loadEtsProjectAutoConvert(root)
    }

    private fun EtsScene.method(name: String): EtsMethod = projectFiles
        .flatMap { it.classes }
        .flatMap { it.methods }
        .single { it.name == name }

    private fun analyze(
        name: String,
        semanticOptions: TsOptions = exactSemanticOptions(),
    ): List<TsTest> {
        val scene = loadScene()
        val method = scene.method(name)
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            exceptionsPropagation = true,
            timeout = 20.seconds,
            stepsFromLastCovered = 2_000L,
            solverType = SolverType.YICES,
        )
        return TsMachine(scene, machineOptions, semanticOptions).use { machine ->
            machine.analyze(listOf(method)).map { TsTestResolver().resolve(method, it) }
        }
    }

    @Test
    fun `namespace callable is materialized and dispatched without an unconstrained mock`() {
        val indexOf = analyze("indexOf")
        val returned = indexOf.mapNotNull { (it.returnValue as? TsTestValue.TsNumber)?.number }.toSet()

        assertTrue(-1.0 in returned)
        assertTrue(returned.any { it >= 0.0 })
        assertFalse(indexOf.any { it.returnValue is TsTestValue.TsException })

        val exactCalls = analyze("callAddConstant")
        assertEquals(
            setOf(5.0),
            exactCalls.mapNotNull { (it.returnValue as? TsTestValue.TsNumber)?.number }.toSet(),
            exactCalls.toString(),
        )
        val callDecisions = analyze("callAddDecision")
            .mapNotNull { (it.returnValue as? TsTestValue.TsBoolean)?.value }
            .toSet()
        assertEquals(setOf(false, true), callDecisions)
    }

    @Test
    fun `array iterator exposes exact done and value fields`() {
        val forEach = analyze("forEach")

        // The empty-array path is exact. A non-empty array with an arbitrary
        // callback is rejected by callableValueModel unless it has stable source identity.
        assertTrue(forEach.isNotEmpty())
        assertTrue(
            forEach.all {
                (it.before.parameters.firstOrNull() as? TsTestValue.TsArray<*>)?.values?.isEmpty() == true
            },
        )
        assertFalse(forEach.any { it.returnValue is TsTestValue.TsException })

        val doneValues = analyze("iteratorDoneValue").map { it.returnValue }
        assertTrue(TsTestValue.TsUndefined in doneValues)
        assertTrue(doneValues.any { (it as? TsTestValue.TsNumber)?.number == 1.0 })
    }

    @Test
    fun `Array isArray and Object tag use exact runtime type conditions`() {
        val isArray = analyze("arrayIsArray")
        val decisions = isArray.mapNotNull { (it.returnValue as? TsTestValue.TsBoolean)?.value }.toSet()
        assertEquals(setOf(false, true), decisions)

        val tags = analyze("objectToStringTag")
            .mapNotNull { (it.returnValue as? TsTestValue.TsString)?.value }
            .toSet()
        assertTrue(tags.isNotEmpty())
        assertTrue(tags.all { it.startsWith("[object ") })
        assertFalse("I am a string" in tags)
    }

    @Test
    fun `exact mode rejects membership and Map state that the heap cannot represent`() {
        assertTrue(analyze("objectHasOwn").isEmpty())
        assertTrue(analyze("propertyIn").isEmpty())
        assertTrue(analyze("mapGet").isEmpty())
    }

    @Test
    fun `hot numeric builtins and Array constructor avoid call mocks`() {
        val absoluteValues = analyze("mathAbs")
            .mapNotNull { (it.returnValue as? TsTestValue.TsNumber)?.number }
        assertTrue(absoluteValues.isNotEmpty())
        assertTrue(absoluteValues.all { it >= 0.0 || it.isNaN() })

        assertEquals(setOf(0.0), analyzeNumberResults("mathRoundBelowHalf"))
        assertEquals(setOf(4_503_599_627_370_497.0), analyzeNumberResults("mathRoundLargeInteger"))
        val negativeZero = analyzeNumberResults("mathRoundNegativeZero").single()
        assertEquals((-0.0).toBits(), negativeZero.toBits())

        val integralDecisions = analyze("numberIsInteger")
            .mapNotNull { (it.returnValue as? TsTestValue.TsBoolean)?.value }
            .toSet()
        assertEquals(setOf(false, true), integralDecisions)

        val lengths = analyze("allocatedArrayLength")
            .mapNotNull { (it.returnValue as? TsTestValue.TsNumber)?.number }
        assertTrue(lengths.isNotEmpty())
        assertTrue(lengths.all { it >= 0.0 })
    }

    @Test
    fun `all semantic models remain opt in`() {
        assertTrue(analyze("arrayIsArray", TsOptions(maxArraySize = 2)).isEmpty())

        val exact = exactSemanticOptions()
        assertTrue(analyze("indexOf", exact.copy(moduleRuntimeModel = false)).isEmpty())
        assertFailsWith<IllegalArgumentException> {
            analyze("indexOf", exact.copy(callableValueModel = false))
        }
        assertTrue(analyze("forEach", exact.copy(iteratorModel = false)).isEmpty())
        assertTrue(analyze("arrayIsArray", exact.copy(exactCollectionBuiltins = false)).isEmpty())
    }

    private companion object {
        fun exactSemanticOptions(): TsOptions = TsOptions(
            maxArraySize = 2,
            moduleRuntimeModel = true,
            callableValueModel = true,
            iteratorModel = true,
            exactCollectionBuiltins = true,
        )
    }

    private fun analyzeNumberResults(name: String): Set<Double> = analyze(name)
        .mapNotNull { (it.returnValue as? TsTestValue.TsNumber)?.number }
        .toSet()
}
