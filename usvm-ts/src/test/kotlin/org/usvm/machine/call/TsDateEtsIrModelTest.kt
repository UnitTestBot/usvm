package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.callExpr
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsDateEtsIrModelTest {
    @TempDir
    lateinit var replayDirectory: Path

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
    fun `a primitive named Date keeps its ordinary valueOf behavior`() {
        assertNumber(methodName = "primitiveNamedDate", expected = 123.0)
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
    fun `explicit undefined Date constructor arguments invalidate the date`() {
        assertNaN(methodName = "undefinedConstructor")
        assertNaN(methodName = "undefinedConstructorComponent")

        val method = method("undefinedConstructor")
        val states = analyze(method = method, tsOptions = TsOptions(dateNowMilliseconds = 123.0))
        val value = TsTestResolver().resolve(method, states.single()).returnValue

        assertTrue(assertIs<TsTestValue.TsNumber>(value).number.isNaN())
        replayDateArguments(
            assertions = listOf(
                "Number.isNaN(example.undefinedConstructor())",
                "Number.isNaN(example.undefinedConstructorComponent())",
            ),
        )
    }

    @Test
    fun `missing required and explicit undefined setter arguments invalidate return and receiver`() {
        val methodNames = listOf(
            "missingSetTime",
            "missingSetMonth",
            "missingSetMinutes",
            "undefinedSetDate",
            "undefinedOptionalSetterComponent",
        )

        for (methodName in methodNames) {
            val method = method(methodName)
            val states = analyze(method)
            val value = TsTestResolver().resolve(method, states.single()).returnValue

            assertTrue(assertIs<TsTestValue.TsBoolean>(value).value, methodName)
        }

        replayDateArguments(assertions = methodNames.map { "example.$it()" })
    }

    @Test
    fun `omitted optional setter components preserve the existing date components`() {
        val method = method("omittedOptionalSetterComponents")
        val states = analyze(method)
        val value = TsTestResolver().resolve(method, states.single()).returnValue

        assertTrue(assertIs<TsTestValue.TsBoolean>(value).value)
        replayDateArguments(assertions = listOf("example.omittedOptionalSetterComponents()"))
    }

    @Test
    fun `copy construction preserves valid and invalid Date instants independently`() {
        assertNumber(methodName = "copiedDateKeepsItsOwnTimestamp", expected = 123.0)
        assertNaN(methodName = "copiedInvalidDateStaysInvalid")

        replayDateArguments(
            assertions = listOf(
                "example.copiedDateKeepsItsOwnTimestamp() === 123",
                "Number.isNaN(example.copiedInvalidDateStaysInvalid())",
            ),
        )
    }

    @Test
    fun `primitive Date argument coercions preserve booleans null and boxed values`() {
        assertNumber(methodName = "primitiveConstructorCoercions", expected = 10.0)
        assertNumber(methodName = "boxedPrimitiveComponents", expected = 1_000.0)
        assertNumber(methodName = "boxedDateCopy", expected = 123.0)

        replayDateArguments(
            assertions = listOf(
                "example.primitiveConstructorCoercions() === 10",
                "example.boxedPrimitiveComponents() === 1000",
                "example.boxedDateCopy() === 123",
            ),
        )
    }

    @Test
    fun `boxed number and undefined alternatives preserve constructor and setter results`() {
        val scenarios = mapOf("boxedConstructorArgument" to 123.0, "boxedSetterArgument" to 246.0)

        for ((methodName, numericResult) in scenarios) {
            val method = method(methodName)
            val tests = analyze(method).map { state -> TsTestResolver().resolve(method, state) }
            val observedChoices = mutableSetOf<Boolean>()

            for (test in tests) {
                val useNumber = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
                val result = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                observedChoices += useNumber

                if (useNumber) {
                    assertEquals(numericResult, result, methodName)
                } else {
                    assertTrue(result.isNaN(), methodName)
                }
            }

            assertEquals(setOf(true, false), observedChoices, methodName)
        }

        replayDateArguments(
            assertions = listOf(
                "example.boxedConstructorArgument(true) === 123",
                "Number.isNaN(example.boxedConstructorArgument(false))",
                "example.boxedSetterArgument(true) === 246",
                "Number.isNaN(example.boxedSetterArgument(false))",
            ),
        )
    }

    @Test
    fun `unsupported boxed object conversions keep a residual outcome`() {
        val method = method("boxedUnsupportedArgument")
        val events = mutableListOf<TsUnknownCallEvent>()
        val observer = object : TsInterpreterObserver {
            override fun onUnknownCall(event: TsUnknownCallEvent) {
                events += event
            }
        }

        val states = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine -> machine.analyze(listOf(method)) }
        val tests = states.map { state -> TsTestResolver().resolve(method, state) }

        assertTrue(tests.isNotEmpty())
        tests.forEach { test ->
            assertTrue(assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value)
            assertEquals(123.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        }
        assertTrue(events.any { event -> event.decision is TsUnknownCallDecision.ResidualFallback })
        replayDateArguments(assertions = listOf("example.boxedUnsupportedArgument(true) === 123"))
    }

    @Test
    fun `timezone offset of an invalid Date is NaN`() {
        assertNaN(methodName = "invalidTimezoneOffset")
    }

    @Test
    fun `time getters preserve negative clipped boundaries invalid dates and positive zero`() {
        val expectedValues = mapOf(
            "timeFieldsBeforeEpoch" to 345_599_999.0,
            "timeFieldsEpoch" to 345_600_000.0,
            "timeFieldsMinimum" to 172_800_000.0,
            "timeFieldsMaximum" to 518_400_000.0,
            "timeFieldsFraction" to 345_540_000.0,
        )

        for ((name, expected) in expectedValues) {
            assertNumber(methodName = name, expected = expected)
        }
        assertNaN(methodName = "timeFieldsInvalid")

        replayDateArguments(
            assertions = expectedValues.map { (name, expected) -> "Object.is(example.$name(), $expected)" } +
                "Number.isNaN(example.timeFieldsInvalid())",
        )
    }

    @Test
    fun `time getters avoid calendar decomposition`() {
        var calendarSteps = 0
        var totalSteps = 0
        val observer = object : TsInterpreterObserver {
            override fun onStatementEntered(stmt: EtsStmt, scope: TsStepScope) {
                totalSteps++
                val methodName = stmt.location.method.name
                if (methodName == "parts" || methodName == "civilFromDays") calendarSteps++
            }
        }

        val states = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine -> machine.analyze(listOf(method("timeFieldsBeforeEpoch"))) }

        assertEquals(expected = 1, actual = states.size)
        assertEquals(expected = 0, actual = calendarSteps, message = "Executed $totalSteps instructions")
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
    fun `Date alias model observation keeps the frontend callee`() {
        val method = method("anyAliasGetTime")
        val sourceCall = assertNotNull(
            method.cfg.stmts.single { stmt -> stmt.callExpr?.callee?.name == "getTime" }.callExpr
        )
        val events = mutableListOf<TsUnknownCallEvent>()
        val observer = object : TsInterpreterObserver {
            override fun onUnknownCall(event: TsUnknownCallEvent) {
                events += event
            }
        }

        val states = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine -> machine.analyze(listOf(method)) }

        assertTrue(states.isNotEmpty())
        val dateEvents = events.filter { event ->
            (event.decision as? TsUnknownCallDecision.ModelApplied)?.modelId == "ts.date.getTime"
        }
        assertTrue(dateEvents.isNotEmpty())
        assertTrue(dateEvents.all { event -> event.callee == sourceCall.callee })
    }

    @Test
    fun `Date model does not replace an unavailable user Date static method`() {
        val shadowFile = loadEtsFileAutoConvert(
            getResourcePath("/models/DateShadowEtsIr.ts"),
            provider = EtsIrProvider.TS_FRONTEND,
        )
        val shadowScene = EtsScene(listOf(shadowFile))
        val method = shadowScene.projectClasses
            .single { it.name == "DateShadowEtsIr" }
            .methods
            .single { it.name == "call" }

        val states = TsMachine(
            scene = shadowScene,
            options = machineOptions,
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyze(listOf(method))
        }

        assertTrue(states.isEmpty())
    }

    @Test
    fun `unrelated objects do not acquire Date behavior through any aliases`() {
        val states = analyze(method("foreignReceiverThroughAny"))

        assertTrue(states.isEmpty())
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
    fun `deleting a public timestamp property preserves the private Date value`() {
        assertNumber(methodName = "deletePublicTimestamp", expected = 456.0)
    }

    @Test
    fun `symbolic numeric timestamp round trips through constructor and valueOf`() {
        val method = method("symbolicRoundTrip")
        val states = analyze(method)

        assertTrue(states.isNotEmpty())
        val tests = states.map { state -> TsTestResolver().resolve(method, state) }
        tests.forEach { test ->
            val timestamp = assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
            val actual = assertIs<TsTestValue.TsNumber>(test.returnValue).number
            val expected = if (!timestamp.isFinite() || timestamp < -MAX_DATE_TIME || timestamp > MAX_DATE_TIME) {
                Double.NaN
            } else {
                timestamp.toLong().toDouble()
            }

            if (expected.isNaN()) {
                assertTrue(actual.isNaN(), "TimeClip($timestamp) must be NaN, got $actual")
            } else {
                assertEquals(expected, actual, "TimeClip($timestamp)")
            }
        }

        assertTrue(
            tests.any { test ->
                assertIs<TsTestValue.TsNumber>(test.returnValue).number.isNaN()
            }
        )
        assertTrue(
            tests.any { test ->
                assertIs<TsTestValue.TsNumber>(test.returnValue).number.isFinite()
            }
        )
    }

    private fun replayDateArguments(assertions: List<String>) {
        val source = getResourcePath("/models/DateEtsIr.ts").readText()
        val checks = assertions.joinToString(separator = "\n") { assertion ->
            "if (!($assertion)) throw new Error('Date argument replay failed: $assertion');"
        }

        assertNodeReplay(
            source = source + "\nconst example = new DateEtsIr();\n" + checks,
            directory = replayDirectory,
            name = "date-arguments",
            timeoutMessage = "Date argument replay timed out",
        )
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
        const val MAX_DATE_TIME = 8_640_000_000_000_000.0

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
