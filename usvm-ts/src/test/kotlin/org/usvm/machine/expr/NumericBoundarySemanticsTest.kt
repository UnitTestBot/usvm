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
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class NumericBoundarySemanticsTest : TsMethodTestRunner() {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/NumericBoundarySemantics.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    override val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `bitwise operators use total ECMAScript int32 conversion`() {
        val expectedResults = mapOf(
            "bitwiseNaN" to 0.0,
            "bitwiseInfinity" to 0.0,
            "bitwiseUint32Wrap" to 1.0,
            "bitwiseFraction" to 1.0,
            "bitwiseNegativeFraction" to -1.0,
            "bitwiseNotNaN" to -1.0,
            "bitwiseAndInfinity" to 0.0,
            "bitwiseXorInfinity" to 7.0,
            "leftShiftMasksCount" to 2.0,
            "rightShiftTruncates" to -2.0,
            "unsignedRightShift" to 4_294_967_295.0,
            "bitwiseNegativeZero" to 0.0,
        )

        expectedResults.forEach { (methodName, expected) ->
            discoverNumber(methodName, expected)
        }
    }

    @Test
    fun `missing numeric array properties return undefined without integer coercion`() {
        discoverNumber("missingNumericArrayProperties", expected = 63.0)
        discoverNumber("negativeZeroArrayIndex", expected = 11.0)
    }

    @Test
    fun `unsupported named property and growth writes stop their paths`() {
        val unsupportedMethods = mapOf(
            "fractionalArrayWrite" to TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE,
            "negativeArrayWrite" to TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE,
            "outOfRangeArrayWrite" to TsRuntimeFeatureLimitationReason.ARRAY_INDEX_GROWTH,
            "chainedMissingArrayWrite" to TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE,
        )

        unsupportedMethods.forEach { (methodName, reason) ->
            val observer = RecordingObserver()

            assertTrue(analyze(methodName, observer).isEmpty(), methodName)
            assertEquals(reason, observer.limitations.single().reason, methodName)
        }
        discoverNumber("negativeZeroArrayWrite", expected = 7.0)
    }

    @Test
    fun `chained array indexes preserve numeric and undefined branches`() {
        discoverNumber("chainedArrayRead", expected = 22.0)
        discoverProperties<TsTestValue.TsUndefined>(
            method = method("chainedMissingArrayRead"),
            { result -> result == TsTestValue.TsUndefined },
        )
    }

    @Test
    fun `reference property keys remain explicit read limitations`() {
        val unsupportedMethods = listOf(
            "stringArrayIndexRead",
            "objectArrayIndexRead",
        )

        unsupportedMethods.forEach { methodName ->
            val observer = RecordingObserver()

            assertTrue(analyze(methodName, observer).isEmpty(), methodName)
            assertEquals(
                TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_READ,
                observer.limitations.single().reason,
                methodName,
            )
        }
    }

    @Test
    fun `numeric string indexes address UTF16 code units without coercion`() {
        discoverString("stringZeroIndex", expected = "A")
        discoverString("stringNegativeZeroIndex", expected = "A")
        discoverString("stringHighSurrogateIndex", expected = "\uD83D")
        discoverString("stringLowSurrogateIndex", expected = "\uDE00")
        discoverNumber("missingNumericStringProperties", expected = 31.0)
    }

    @Test
    fun `invalid array lengths throw before conversion`() {
        val invalidMethods = listOf(
            "newArrayInfinity",
            "newArrayUint32Overflow",
            "newArrayFraction",
            "assignInvalidLength",
        )

        invalidMethods.forEach { methodName ->
            val state = analyze(methodName).single()
            assertIs<TsMethodResult.TsException>(state.methodResult, methodName)
        }
    }

    @Test
    fun `valid but unsupported lengths remain residual`() {
        val unsupportedMethods = listOf(
            "newArrayBeyondModelCapacity",
            "assignLengthBeyondModelCapacity",
        )

        unsupportedMethods.forEach { methodName ->
            val observer = RecordingObserver()

            assertTrue(analyze(methodName, observer).isEmpty(), methodName)
            assertEquals(
                TsRuntimeFeatureLimitationReason.ARRAY_LENGTH_CAPACITY,
                observer.limitations.single().reason,
                methodName,
            )
        }
    }

    @Test
    fun `negative zero is a valid array length`() {
        discoverNumber("newArrayNegativeZero", expected = 0.0)
        discoverNumber("assignNegativeZeroLength", expected = 0.0)
    }

    private fun discoverNumber(methodName: String, expected: Double) {
        discoverProperties<TsTestValue.TsNumber>(
            method = method(methodName),
            { result -> result.number == expected },
            invariants = arrayOf({ result -> result.number == expected }),
        )
    }

    private fun discoverString(methodName: String, expected: String) {
        discoverProperties<TsTestValue.TsString>(
            method = method(methodName),
            { result -> result.value == expected },
            invariants = arrayOf({ result -> result.value == expected }),
        )
    }

    private fun analyze(
        methodName: String,
        observer: TsInterpreterObserver? = null,
    ): List<TsState> = analyze(method(methodName), observer)

    private fun analyze(
        method: EtsMethod,
        observer: TsInterpreterObserver? = null,
    ): List<TsState> = TsMachine(
        scene = scene,
        options = machineOptions,
        tsOptions = TsOptions(maxArraySize = 16),
        observer = observer,
    ).use { machine ->
        machine.analyze(listOf(method))
    }

    private fun method(name: String): EtsMethod = scene.projectClasses
        .single { it.name == "NumericBoundarySemantics" }
        .methods
        .single { it.name == name }

    private class RecordingObserver : TsInterpreterObserver {
        val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()

        override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
            limitations += event
        }
    }

    private companion object {
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            throwExceptionOnStepFailure = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 20_000L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
