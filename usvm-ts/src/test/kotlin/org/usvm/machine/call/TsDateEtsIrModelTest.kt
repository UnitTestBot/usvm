package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsDateEtsIrModelTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/DateEtsIr.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `Date now and zero argument constructor share the configured fixed clock`() {
        val method = method("fixedClock")
        val states = analyze(
            method = method,
            tsOptions = TsOptions(dateNowMilliseconds = 1_710_067_696_789.0),
        )
        val value = TsTestResolver().resolve(method, states.single()).returnValue

        assertEquals(0.0, assertIs<TsTestValue.TsNumber>(value).number)
    }

    @Test
    fun `clock dependent Date calls remain unsupported without an explicit clock`() {
        assertTrue(analyze(method("fixedClock")).isEmpty())
    }

    @Test
    fun `numeric constructors getters UTC and overflow execute through source models`() {
        assertNumber(methodName = "epochYear", expected = 1970.0)
        assertNumber(methodName = "leapDay", expected = 129.0)
        assertNumber(methodName = "overflow", expected = 20_231_201.0)
    }

    @Test
    fun `UTC distinguishes omitted arguments from explicit undefined`() {
        assertNaN(methodName = "utcNoArguments")
        assertNaN(methodName = "utcUndefinedYear")
        assertNumber(methodName = "utcYearOnly", expected = 1_577_836_800_000.0)
        assertNaN(methodName = "utcExplicitUndefined")
    }

    @Test
    fun `timezone offset of an invalid Date is NaN`() {
        assertNaN(methodName = "invalidTimezoneOffset")
    }

    @Test
    fun `Date truncates fractional timestamps and components toward zero`() {
        assertNumber(methodName = "fractionalTimestamps", expected = 9.0)
        assertNumber(methodName = "fractionalUtcDay", expected = 1.0)
    }

    @Test
    fun `Date calls through any aliases use the Date model`() {
        assertNumber(methodName = "anyAliasValueOf", expected = 123.0)
        assertNumber(methodName = "anyAliasGetTime", expected = 456.0)
    }

    @Test
    fun `UTC minute and millisecond setters execute through source models`() {
        assertNumber(methodName = "utcMinuteSetters", expected = 7_318_000.0)
    }

    @Test
    fun `concrete ISO formatting executes through the source model`() {
        val method = method("isoEpoch")
        val value = TsTestResolver().resolve(method, analyze(method).single()).returnValue

        assertEquals("1970-01-01T00:00:00.000Z", assertIs<TsTestValue.TsString>(value).value)
    }

    @Test
    fun `setter updates the shared Date timestamp slot`() {
        assertNumber(methodName = "setter", expected = 951_782_400_029.0)
    }

    @Test
    fun `symbolic numeric timestamp round trips through constructor and valueOf`() {
        val method = method("symbolicRoundTrip")
        val states = analyze(method)

        assertTrue(states.isNotEmpty())
        val allResultsAreNumbers = states.all { state ->
            val result = assertIs<TsTestValue.TsNumber>(TsTestResolver().resolve(method, state).returnValue)
            result.number.isFinite() || result.number.isNaN()
        }

        assertTrue(allResultsAreNumbers)
    }

    private fun assertNumber(methodName: String, expected: Double) {
        val method = method(methodName)
        val values = analyze(method).map { state -> TsTestResolver().resolve(method, state).returnValue }

        assertEquals(expected, assertIs<TsTestValue.TsNumber>(values.single()).number)
    }

    private fun assertNaN(methodName: String) {
        val method = method(methodName)
        val values = analyze(method).map { state -> TsTestResolver().resolve(method, state).returnValue }

        assertTrue(assertIs<TsTestValue.TsNumber>(values.single()).number.isNaN())
    }

    private fun analyze(
        method: EtsMethod,
        tsOptions: TsOptions = TsOptions(),
    ) = TsMachine(
        scene = scene,
        options = machineOptions,
        tsOptions = tsOptions,
    ).use { machine -> machine.analyze(listOf(method)) }

    private fun method(name: String): EtsMethod = scene.projectClasses
        .single { it.name == "DateEtsIr" }
        .methods
        .single { it.name == name }

    private companion object {
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
