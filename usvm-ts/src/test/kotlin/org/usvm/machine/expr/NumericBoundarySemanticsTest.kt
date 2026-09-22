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
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class NumericBoundarySemanticsTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/NumericBoundarySemantics.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

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
            assertEquals(expected, singleNumber(methodName), methodName)
        }
    }

    @Test
    fun `missing numeric array properties return undefined without integer coercion`() {
        assertEquals(63.0, singleNumber("missingNumericArrayProperties"))
        assertEquals(11.0, singleNumber("negativeZeroArrayIndex"))
    }

    @Test
    fun `unsupported named property and growth writes stop their paths`() {
        val unsupportedMethods = mapOf(
            "fractionalArrayWrite" to TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE,
            "negativeArrayWrite" to TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE,
            "outOfRangeArrayWrite" to TsRuntimeFeatureLimitationReason.ARRAY_INDEX_GROWTH,
            "chainedMissingArrayWrite" to TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE,
            "typedArrayCannotStoreMissingValue" to TsRuntimeFeatureLimitationReason.ARRAY_ELEMENT_KIND_WRITE,
        )

        unsupportedMethods.forEach { (methodName, reason) ->
            val observer = RecordingObserver()

            assertTrue(analyze(methodName, observer).isEmpty(), methodName)
            assertEquals(reason, observer.limitations.single().reason, methodName)
        }
        assertEquals(7.0, singleNumber("negativeZeroArrayWrite"))
    }

    @Test
    fun `chained array indexes preserve numeric and undefined branches`() {
        assertEquals(22.0, singleNumber("chainedArrayRead"))
        assertIs<TsTestValue.TsUndefined>(singleValue("chainedMissingArrayRead"))
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
        assertEquals("A", singleString("stringZeroIndex"))
        assertEquals("A", singleString("stringNegativeZeroIndex"))
        assertEquals("\uD83D", singleString("stringHighSurrogateIndex"))
        assertEquals("\uDE00", singleString("stringLowSurrogateIndex"))
        assertEquals(31.0, singleNumber("missingNumericStringProperties"))
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
        assertEquals(0.0, singleNumber("newArrayNegativeZero"))
        assertEquals(0.0, singleNumber("assignNegativeZeroLength"))
    }

    private fun singleNumber(methodName: String): Double {
        val result = singleValue(methodName)

        return assertIs<TsTestValue.TsNumber>(result).number
    }

    private fun singleString(methodName: String): String {
        val result = singleValue(methodName)

        return assertIs<TsTestValue.TsString>(result).value
    }

    private fun singleValue(methodName: String): TsTestValue {
        val method = method(methodName)
        val state = analyze(method).single()

        return TsTestResolver().resolve(method, state).returnValue
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
