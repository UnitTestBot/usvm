package org.usvm.machine.expr

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val UNMODELED_NUMERIC_CONVERSION_REASON =
    "Numeric conversion needs a modeled StringToNumber or ToPrimitive conversion"
private const val DYNAMIC_STRING_ADDITION_REASON =
    "Dynamic string addition needs a modeled mixed-kind result"

class RuntimeLimitationBoundariesTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/RuntimeLimitationBoundaries.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )

    @Test
    fun `regular expression limitation terminates once with its exact reason`() {
        val observer = RecordingObserver()
        val target = method("regularExpressionLiteral")

        TsMachine(
            scene = scene,
            options = UMachineOptions(
                solverType = SolverType.YICES,
                throwExceptionOnStepFailure = true,
                stepsFromLastCovered = 128L
            ),
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine ->
            val outcome = machine.analyzeWithOutcome(listOf(target))

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
            assertTrue(outcome.states.isEmpty())
            assertTrue(
                outcome.unsupportedPaths.any { "REGULAR_EXPRESSION_LITERAL" in it },
                outcome.unsupportedPaths.toString(),
            )
        }
        assertEquals(listOf(TsRuntimeFeatureLimitationReason.REGULAR_EXPRESSION_LITERAL), observer.reasons)
    }

    @Test
    fun `throwing limitation observer preserves unsupported path metadata`() {
        var callbackCount = 0
        val observer = object : TsInterpreterObserver {
            override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                callbackCount++
                error("Injected runtime limitation observer failure")
            }
        }
        val target = method("regularExpressionLiteral")

        val result = TsMachine(
            scene = scene,
            options = UMachineOptions(
                solverType = SolverType.YICES,
                throwExceptionOnStepFailure = true,
                stepsFromLastCovered = 128L,
            ),
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine ->
            machine.analyzeWithMetadata(methods = listOf(target))
        }

        assertEquals(1, callbackCount)
        assertTrue(result.states.isEmpty())
        assertTrue(result.runtimeLimited)
        assertFalse(result.engineFailed)
        assertTrue(
            result.unsupportedPaths.any { "REGULAR_EXPRESSION_LITERAL" in it },
            result.unsupportedPaths.toString(),
        )
    }

    @Test
    fun `unsupported operand conversion terminates while a supported sibling still returns`() {
        val observer = RecordingObserver()
        val target = method("unsupportedConversionOrSupportedSibling")

        TsMachine(
            scene = scene,
            options = UMachineOptions(
                solverType = SolverType.YICES,
                throwExceptionOnStepFailure = true,
                stepsFromLastCovered = 128L
            ),
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine ->
            val outcome = machine.analyzeWithOutcome(listOf(target))
            val values = outcome.states.map { state -> TsTestResolver().resolve(target, state).returnValue }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
            assertTrue(
                outcome.unsupportedPaths.any { "STRING_CONCAT_OPERAND_CONVERSION" in it },
                outcome.unsupportedPaths.toString(),
            )
            assertTrue(values.any { value -> value is TsTestValue.TsString && value.value == "supported" })
        }
        assertTrue(observer.reasons.isNotEmpty())
        assertTrue(
            observer.reasons.all { reason ->
                reason == TsRuntimeFeatureLimitationReason.STRING_CONCAT_OPERAND_CONVERSION
            },
        )
        assertTrue(observer.reasons.size < 128)
    }

    @Test
    fun `ordinary numeric object key is not an array storage limitation`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method("ordinaryNumericKeyField"),
            { result -> result.number == 17.0 },
            invariants = arrayOf({ result -> result.number == 17.0 }),
        )
    }

    @Test
    fun `ordinary object length is read as its actual field`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method("ordinaryLengthField"),
            { result -> result.number == 17.0 },
            invariants = arrayOf({ result -> result.number == 17.0 }),
        )
    }

    @Test
    fun `unmodeled numeric conversion stops while a stored numeric sibling returns`() {
        assertUnsupportedNumericSibling(methodName = "unsupportedNumericConversionOrSupportedSibling", expected = 7.0)
    }

    @Test
    fun `unmodeled binary numeric conversion stops while a stored numeric sibling returns`() {
        assertUnsupportedNumericSibling(
            methodName = "unsupportedNumericBinaryConversionOrSupportedSibling",
            expected = 6.0,
        )
    }

    @Test
    fun `dynamic string addition stops while numeric sibling returns`() {
        assertUnsupportedNumericSibling(
            methodName = "dynamicStringAdditionOrSupportedNumericSibling",
            expected = 9.0,
            unsupportedReason = DYNAMIC_STRING_ADDITION_REASON,
        )
    }

    private fun assertUnsupportedNumericSibling(
        methodName: String,
        expected: Double,
        unsupportedReason: String = UNMODELED_NUMERIC_CONVERSION_REASON,
    ) {
        val target = method(name = methodName)
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            solverType = SolverType.YICES,
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            throwExceptionOnStepFailure = false,
            stepsFromLastCovered = 128L,
        )

        TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
            val outcome = machine.analyzeWithOutcome(listOf(target))
            val witnesses = outcome.states.map { state -> TsTestResolver().resolve(target, state) }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
            assertTrue(unsupportedReason in outcome.unsupportedPaths)
            assertTrue(witnesses.isNotEmpty())
            witnesses.forEach { witness ->
                val useString = witness.before.parameters.single() as TsTestValue.TsBoolean
                val result = witness.returnValue as TsTestValue.TsNumber

                assertTrue(!useString.value)
                assertEquals(expected, result.number)
            }
        }
    }

    @Test
    fun `stored primitives and zero or one code unit strings keep numeric conversions`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = method(name = "modeledNumericConversions"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    private fun method(name: String): EtsMethod = getMethod(
        methodName = name,
        className = "RuntimeLimitationBoundaries",
    )

    private class RecordingObserver : TsInterpreterObserver {
        val reasons = mutableListOf<TsRuntimeFeatureLimitationReason>()

        override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
            reasons += event.reason
        }
    }
}
