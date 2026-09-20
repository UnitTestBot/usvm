package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.intrinsic.TsNumericIntrinsicModelFamily
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsNumericIntrinsicModelsTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/NumericIntrinsicModels.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(projectFiles = listOf(sourceFile))

    @Test
    fun `primary Math models preserve binary64 special values and signed zero`() {
        val expected = linkedMapOf(
            "absNegativeZero" to numberToken(0.0),
            "absNaN" to NAN,
            "absNegativeInfinity" to numberToken(Double.POSITIVE_INFINITY),
            "minSignedZero" to numberToken(-0.0),
            "minNaN" to NAN,
            "minNoArguments" to numberToken(Double.POSITIVE_INFINITY),
            "maxSignedZero" to numberToken(0.0),
            "maxInfinity" to numberToken(Double.POSITIVE_INFINITY),
            "maxNoArguments" to numberToken(Double.NEGATIVE_INFINITY),
            "roundNegativeHalf" to numberToken(-0.0),
            "roundPositiveHalf" to numberToken(1.0),
            "roundNegativeOneHalf" to numberToken(-1.0),
            "roundNaN" to NAN,
            "ceilNegativeFraction" to numberToken(-0.0),
            "ceilInfinity" to numberToken(Double.POSITIVE_INFINITY),
            "absNoArguments" to NAN,
            "absExtraArgument" to numberToken(2.0),
        )

        val result = analyze(expected.keys.toList())

        expected.forEach { (methodName, expectedToken) ->
            val actual = assertIs<TsTestValue.TsNumber>(result.values.getValue(methodName).single()).number

            assertEquals(expectedToken, numberToken(actual), methodName)
        }
        assertEquals(expected.size, result.events.size)
        assertTrue(result.events.all { event -> event.outcome == TsUnknownCallOutcome.MODEL_APPLIED })
    }

    @Test
    fun `Number isInteger handles finite boundaries and non numbers`() {
        val expected = linkedMapOf(
            "integerPositiveZero" to true,
            "integerNegativeZero" to true,
            "integerFraction" to false,
            "integerNaN" to false,
            "integerInfinity" to false,
            "integerLargeBinary64" to true,
            "integerBoolean" to false,
            "integerNoArguments" to false,
            "integerExtraArgument" to true,
        )

        val result = analyze(expected.keys.toList())

        expected.forEach { (methodName, expectedValue) ->
            val actual = assertIs<TsTestValue.TsBoolean>(result.values.getValue(methodName).single()).value

            assertEquals(expectedValue, actual, methodName)
        }
        assertEquals(
            List(expected.size) { TsNumericIntrinsicModelFamily.NUMBER_IS_INTEGER_ID },
            result.modelIds,
        )
    }

    @Test
    fun `symbolic numeric calls use intrinsic models`() {
        val methodNames = listOf("symbolicAbs", "symbolicInteger")

        val result = analyze(methodNames)

        val absValues = result.values.getValue("symbolicAbs")
            .map { value -> assertIs<TsTestValue.TsNumber>(value).number }
        val integerValues = result.values.getValue("symbolicInteger")
            .map { value -> assertIs<TsTestValue.TsNumber>(value).number }

        assertEquals(setOf(1.0), absValues.toSet())
        assertEquals(setOf(0.0, 1.0), integerValues.toSet())
        assertEquals(
            setOf(
                TsNumericIntrinsicModelFamily.MATH_ABS_ID,
                TsNumericIntrinsicModelFamily.NUMBER_IS_INTEGER_ID,
            ),
            result.modelIds.toSet(),
        )
        assertTrue(result.events.all { event -> event.outcome == TsUnknownCallOutcome.MODEL_APPLIED })
    }

    @Test
    fun `unsupported Math domain uses residual fallback`() {
        val methodNames = listOf("unsupportedAbsDomain")

        val result = analyze(methodNames)

        assertTrue(result.values.values.all { values -> values.isEmpty() })
        assertTrue(result.modelIds.isEmpty())
        assertEquals(
            List(methodNames.size) { TsUnknownCallOutcome.PATH_STOPPED },
            result.events.map { event -> event.outcome },
        )
    }

    @Test
    fun `disabled numeric model uses configured fallback`() {
        val result = analyze(
            methodNames = listOf("absNegativeZero"),
            tsOptions = TsOptions(
                unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()),
                unknownCallFallback = TsResidualCallPolicy.STOP_PATH,
            ),
        )

        assertTrue(result.values.getValue("absNegativeZero").isEmpty())
        assertTrue(result.modelIds.isEmpty())
        assertEquals(listOf(TsUnknownCallOutcome.PATH_STOPPED), result.events.map { event -> event.outcome })
    }

    @Test
    fun `adjacent Math models preserve special values`() {
        val expected = linkedMapOf(
            "floorNegativeFraction" to numberToken(-2.0),
            "floorNegativeZero" to numberToken(-0.0),
            "floorInfinity" to numberToken(Double.POSITIVE_INFINITY),
            "truncNegativeFraction" to numberToken(-1.0),
            "truncNegativeSmall" to numberToken(-0.0),
            "truncNaN" to NAN,
            "sqrtFour" to numberToken(2.0),
            "sqrtNegative" to NAN,
            "sqrtNegativeZero" to numberToken(-0.0),
            "sqrtInfinity" to numberToken(Double.POSITIVE_INFINITY),
        )

        val result = analyze(expected.keys.toList())

        expected.forEach { (methodName, expectedToken) ->
            val actual = assertIs<TsTestValue.TsNumber>(result.values.getValue(methodName).single()).number

            assertEquals(expectedToken, numberToken(actual), methodName)
        }
        assertEquals(
            setOf(
                TsNumericIntrinsicModelFamily.MATH_FLOOR_ID,
                TsNumericIntrinsicModelFamily.MATH_TRUNC_ID,
                TsNumericIntrinsicModelFamily.MATH_SQRT_ID,
            ),
            result.modelIds.toSet(),
        )
    }

    @Test
    fun `adjacent Number predicates handle special and non number values`() {
        val expected = linkedMapOf(
            "finiteNumber" to true,
            "finiteNaN" to false,
            "finiteInfinity" to false,
            "finiteBoolean" to false,
            "nanNaN" to true,
            "nanNumber" to false,
            "nanBoolean" to false,
            "safeIntegerMaximum" to true,
            "safeIntegerAboveMaximum" to false,
            "safeIntegerFraction" to false,
            "safeIntegerInfinity" to false,
            "safeIntegerBoolean" to false,
        )

        val result = analyze(expected.keys.toList())

        expected.forEach { (methodName, expectedValue) ->
            val actual = assertIs<TsTestValue.TsBoolean>(result.values.getValue(methodName).single()).value

            assertEquals(expectedValue, actual, methodName)
        }
        assertEquals(
            setOf(
                TsNumericIntrinsicModelFamily.NUMBER_IS_FINITE_ID,
                TsNumericIntrinsicModelFamily.NUMBER_IS_NAN_ID,
                TsNumericIntrinsicModelFamily.NUMBER_IS_SAFE_INTEGER_ID,
            ),
            result.modelIds.toSet(),
        )
    }

    private fun analyze(
        methodNames: List<String>,
        tsOptions: TsOptions = TsOptions(),
    ): AnalysisResult {
        val methods = methodNames.associateWith(::method)
        val observer = RecordingUnknownCallObserver()

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = tsOptions,
            observer = observer,
        ).use { machine ->
            val states = machine.analyze(methods.values.toList())
            val values = methods.mapValues { (_, method) ->
                states.filter { state -> state.entrypoint === method }
                    .map { state -> TsTestResolver().resolve(method, state).returnValue }
            }

            AnalysisResult(
                values = values,
                events = observer.events.toList(),
            )
        }
    }

    private fun method(name: String): EtsMethod = scene.projectClasses
        .single { clazz -> clazz.name == DEFAULT_ARK_CLASS_NAME && clazz.declaringFile === sourceFile }
        .methods
        .single { method -> method.name == name }

    private class RecordingUnknownCallObserver : TsInterpreterObserver {
        val events = mutableListOf<TsUnknownCallEvent>()

        override fun onUnknownCall(event: TsUnknownCallEvent) {
            events += event
        }
    }

    private data class AnalysisResult(
        val values: Map<String, List<TsTestValue>>,
        val events: List<TsUnknownCallEvent>,
    ) {
        val modelIds: List<String> = events.mapNotNull { event ->
            (event.decision as? TsUnknownCallDecision.ModelApplied)?.modelId
        }
    }

    private companion object {
        const val NAN: String = "nan"

        fun numberToken(value: Double): String =
            if (value.isNaN()) NAN else value.toRawBits().toULong().toString(radix = 16).padStart(16, '0')

        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            throwExceptionOnStepFailure = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
