package org.usvm.machine

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.TsUnsupportedWitnessException
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import org.usvm.util.jsString
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsStringEqualityTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/StringEquality.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
    private val analysisOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
        throwExceptionOnStepFailure = true,
    )

    @Test
    fun `string equality and inequality produce replayable witnesses`() {
        val expected = mapOf<String, (List<String>) -> Int>(
            "equalsA" to { args -> if (args[0] == "a") 1 else 2 },
            "notEqualsA" to { args -> if (args[0] != "a") 1 else 2 },
            "equalsEmpty" to { args -> if (args[0].isEmpty()) 1 else 2 },
            "equalsUnicode" to { args -> if (args[0] == "\uD83D\uDE00") 1 else 2 },
            "equalsOther" to { args -> if (args[0] == args[1]) 1 else 2 },
            "equalsAtLengthOne" to { args ->
                if (args[0].length != 1 || args[1].length != 1) 3 else if (args[0] == args[1]) 1 else 2
            },
            "looselyEqualsOther" to { args -> if (args[0] == args[1]) 1 else 2 },
            "looselyNotEqualsOther" to { args -> if (args[0] != args[1]) 1 else 2 },
        )

        val twoParameterMethods = setOf(
            "equalsOther",
            "equalsAtLengthOne",
            "looselyEqualsOther",
            "looselyNotEqualsOther",
        )

        expected.forEach { (name, expectedResult) ->
            val method = getMethod(methodName = name, className = "StringEquality")

            if (name in twoParameterMethods) {
                val results = if (name == "equalsAtLengthOne") listOf(1, 2, 3) else listOf(1, 2)
                discoverProperties<TsTestValue.TsString, TsTestValue.TsString, TsTestValue.TsNumber>(
                    method = method,
                    *results.map { expectedNumber ->
                        {
                                left: TsTestValue.TsString,
                                right: TsTestValue.TsString,
                                result: TsTestValue.TsNumber,
                            ->
                            result.number == expectedNumber.toDouble() &&
                                result.number == expectedResult(listOf(left.value, right.value)).toDouble()
                        }
                    }.toTypedArray(),
                    invariants = arrayOf({ left, right, result ->
                        result.number == expectedResult(listOf(left.value, right.value)).toDouble()
                    }),
                )
            } else {
                discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
                    method = method,
                    { input, result -> result.number == 1.0 && expectedResult(listOf(input.value)) == 1 },
                    { input, result -> result.number == 2.0 && expectedResult(listOf(input.value)) == 2 },
                    invariants = arrayOf({ input, result ->
                        result.number == expectedResult(listOf(input.value)).toDouble()
                    }),
                )
            }
        }

        val tests = expected.mapValues { (name, _) -> analyze(name) }

        tests.forEach { (name, generated) ->
            val expectedResults = if (name == "equalsAtLengthOne") setOf(1, 2, 3) else setOf(1, 2)
            assertEquals(expectedResults, generated.map { resultNumber(it) }.toSet(), name)
            generated.forEach { test ->
                val inputs = test.before.parameters.map { assertIs<TsTestValue.TsString>(it).value }
                assertEquals(expected.getValue(name)(inputs), resultNumber(test), "$name: $test")
            }
        }

        replay(tests)
    }

    @Test
    fun `null and undefined strict and loose equality remain distinct`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "nullAndUndefined", className = "StringEquality"),
            { result -> result.number == 2.0 },
            invariants = arrayOf({ result -> result.number == 2.0 }),
        )

        val tests = mapOf("nullAndUndefined" to analyze("nullAndUndefined"))

        assertEquals(setOf(2), tests.getValue("nullAndUndefined").map(::resultNumber).toSet())

        replay(tests)
    }

    @Test
    fun `default string bound can compare two symbolic inputs`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString, TsTestValue.TsNumber>(
            method = getMethod(methodName = "equalsOther", className = "StringEquality"),
            { left, right, result -> left.value == right.value && result.number == 1.0 },
            { left, right, result -> left.value != right.value && result.number == 2.0 },
        )

        val tests = analyze("equalsOther", maxStringLength = 1_000)

        assertEquals(setOf(1, 2), tests.map(::resultNumber).toSet())

        replay(mapOf("equalsOther" to tests))
    }

    @Test
    fun `refined any and unknown strings produce replayable equality witnesses`() {
        val generated = listOf("equalsAnyStrings", "equalsUnknownStrings").associateWith { name ->
            val method = getMethod(methodName = name, className = "StringEquality")
            val expectedResult: (TsTestValue, TsTestValue.TsString) -> Int = { left, right ->
                if (left is TsTestValue.TsString) {
                    if (left.value == right.value) 1 else 2
                } else {
                    3
                }
            }

            discoverProperties<TsTestValue, TsTestValue.TsString, TsTestValue.TsNumber>(
                method = method,
                { left, right, result -> result.number == 1.0 && expectedResult(left, right) == 1 },
                { left, right, result -> result.number == 2.0 && expectedResult(left, right) == 2 },
                { left, right, result -> result.number == 3.0 && expectedResult(left, right) == 3 },
                invariants = arrayOf({ left, right, result ->
                    result.number == expectedResult(left, right).toDouble()
                }),
            )

            val analysis = TsMachine(
                scene,
                options = analysisOptions.copy(stateCollectionStrategy = StateCollectionStrategy.ALL),
                tsOptions = TsOptions(maxArraySize = 4),
            ).use { machine ->
                machine.analyzeWithOutcome(listOf(method))
            }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason, name)
            assertTrue(analysis.unsupportedPaths.isEmpty(), name)
            val tests = analysis.states.map { state -> TsTestResolver().resolve(method, state) }
            assertTrue(tests.isNotEmpty(), name)
            assertEquals(setOf(1, 2, 3), tests.map(::resultNumber).toSet(), name)
            tests.forEach { test ->
                val left = test.before.parameters[0]
                val right = assertIs<TsTestValue.TsString>(test.before.parameters[1])

                assertEquals(expectedResult(left, right), resultNumber(test), "$name: $test")
            }

            tests
        }

        replayDynamic(generated)
        replayLongMismatch()

        val method = scene.projectClasses.single { it.name == "StringEquality" }
            .methods
            .single { it.name == "equalsAnyStrings" }
        val ordinaryStates = TsMachine(
            scene,
            options = analysisOptions,
            tsOptions = TsOptions(maxArraySize = 4),
        ).use { machine ->
            machine.analyze(listOf(method))
        }
        val ordinaryTests = ordinaryStates.map { state -> TsTestResolver().resolve(method, state) }
        assertEquals(setOf(1, 2, 3), ordinaryTests.map(::resultNumber).toSet())
    }

    @Test
    fun `refinement prepares backing before length alias reads and unused string results`() {
        val expected = mapOf<String, (TsTestValue) -> Int>(
            "refinedStringLength" to { input ->
                if (input is TsTestValue.TsString) {
                    if (input.value.length == 1) 1 else 2
                } else {
                    3
                }
            },
            "refinedStringAlias" to { input ->
                if (input is TsTestValue.TsString) {
                    if (input.value.length == 1) 1 else 2
                } else {
                    3
                }
            },
            "refinedStringUnused" to { input -> if (input is TsTestValue.TsString) 1 else 3 },
            "refinedStringNullish" to { input ->
                when (input) {
                    TsTestValue.TsNull -> 4
                    TsTestValue.TsUndefined -> 5
                    is TsTestValue.TsString -> if (input.value.length == 1) 1 else 2
                    else -> 3
                }
            },
        )
        val generated = expected.mapValues { (name, expectedResult) ->
            val tests = analyze(name)
            val expectedResults = when (name) {
                "refinedStringUnused" -> setOf(1, 3)
                "refinedStringNullish" -> setOf(1, 2, 3, 4, 5)
                else -> setOf(1, 2, 3)
            }

            assertEquals(expectedResults, tests.map(::resultNumber).toSet(), name)
            tests.forEach { test ->
                assertEquals(expectedResult(test.before.parameters[0]), resultNumber(test), "$name: $test")
            }

            tests
        }

        replayDynamic(generated)
    }

    @Test
    fun `two refined dynamic strings produce replayable equality witnesses`() {
        val method = scene.projectClasses.single { it.name == "StringEquality" }
            .methods
            .single { it.name == "looselyEqualsDynamicStrings" }
        val analysis = TsMachine(
            scene,
            options = analysisOptions.copy(stateCollectionStrategy = StateCollectionStrategy.ALL),
            tsOptions = TsOptions(maxArraySize = 4),
        ).use { machine ->
            machine.analyzeWithOutcome(listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertTrue(analysis.unsupportedPaths.isEmpty())
        val unsupportedWitnesses = mutableListOf<TsUnsupportedWitnessException>()
        val tests = analysis.states.mapNotNull { state ->
            try {
                TsTestResolver().resolve(method, state)
            } catch (failure: TsUnsupportedWitnessException) {
                // An input left unrefined by the early return may still have no string model.
                val result = assertIs<TsMethodResult.Success>(state.methodResult).value
                val number = state.models.first().eval(result.asExpr(state.ctx.fp64Sort)).extractDouble()
                assertEquals(3.0, number, "A refined string branch must have a replayable witness")

                unsupportedWitnesses += failure
                null
            }
        }

        assertTrue(unsupportedWitnesses.all { "missing backing array" in it.message.orEmpty() })
        assertEquals(setOf(1, 2, 3), tests.map(::resultNumber).toSet())
        tests.forEach { test ->
            val left = test.before.parameters[0]
            val right = test.before.parameters[1]
            val expected = if (left is TsTestValue.TsString && right is TsTestValue.TsString) {
                if (left.value == right.value) 1 else 2
            } else {
                3
            }

            assertEquals(expected, resultNumber(test), "$test")
        }

        replayDynamic(mapOf("looselyEqualsDynamicStrings" to tests))
    }

    @Test
    fun `unsupported fork does not consume covered-new slot or stop before supported result`() {
        val method = scene.projectClasses.single { it.name == "StringEquality" }
            .methods
            .single { it.name == "equalsAnyDirect" }
        val analysis = TsMachine(
            scene,
            options = analysisOptions.copy(collectedStatesLimit = 1),
            tsOptions = TsOptions(maxArraySize = 4),
        ).use { machine ->
            machine.analyzeWithOutcome(listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertTrue(analysis.unsupportedPaths.any { "modeled string backing" in it })
        val tests = analysis.states.map { state -> TsTestResolver().resolve(method, state) }
        val supported = tests.filter { test ->
            test.before.parameters[0] !is TsTestValue.TsString && resultNumber(test) == 2
        }
        assertTrue(supported.isNotEmpty(), "$tests")

        replayDynamic(mapOf("equalsAnyDirect" to supported))
    }

    private fun analyze(name: String, maxStringLength: Int = 4): List<TsTest> {
        val method = scene.projectClasses.single { it.name == "StringEquality" }
            .methods
            .single { it.name == name }
        val analysis = TsMachine(
            scene,
            options = analysisOptions,
            tsOptions = TsOptions(maxArraySize = maxStringLength),
        ).use { machine ->
            machine.analyzeWithOutcome(listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason, name)
        assertTrue(analysis.states.isNotEmpty(), name)
        assertTrue(analysis.states.all { it.methodResult is TsMethodResult.Success }, name)

        return analysis.states.map { state -> TsTestResolver().resolve(method, state) }
    }

    private fun resultNumber(test: TsTest): Int =
        assertIs<TsTestValue.TsNumber>(test.returnValue).number.toInt()

    private fun replay(tests: Map<String, List<TsTest>>) {
        val script = buildString {
            appendLine(source.readText())
            tests.forEach { (name, generated) ->
                generated.forEachIndexed { index, test ->
                    val args = test.before.parameters.joinToString { value ->
                        jsString(assertIs<TsTestValue.TsString>(value).value)
                    }
                    val expected = resultNumber(test)

                    appendLine("if (new StringEquality().$name($args) !== $expected) {")
                    appendLine("  throw Error('$name witness $index');")
                    appendLine("}")
                }
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "string-equality",
            timeoutMessage = "Node replay timed out",
        )
    }

    private fun replayDynamic(tests: Map<String, List<TsTest>>) {
        val script = buildString {
            appendLine(source.readText())
            tests.forEach { (name, generated) ->
                generated.forEachIndexed { index, test ->
                    val args = test.before.parameters.joinToString(transform = ::jsDynamic)

                    appendLine("if (new StringEquality().$name($args) !== ${resultNumber(test)}) {")
                    appendLine("  throw Error('$name witness $index');")
                    appendLine("}")
                }
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "dynamic-string-equality",
            timeoutMessage = "Node replay timed out",
        )
    }

    private fun jsDynamic(value: TsTestValue): String = when (value) {
        is TsTestValue.TsString -> jsString(value.value)
        is TsTestValue.TsBoolean -> value.value.toString()
        is TsTestValue.TsNumber -> value.number.toString()
        TsTestValue.TsNull -> "null"
        TsTestValue.TsUndefined -> "undefined"
        is TsTestValue.TsClass -> "{}"
        is TsTestValue.TsArray<*> -> "[]"
        else -> error("Unexpected string alternative: $value")
    }

    private fun replayLongMismatch() {
        val script = buildString {
            appendLine(source.readText())
            appendLine("const left = 'a'.repeat(4) + 'x';")
            appendLine("const right = 'a'.repeat(4) + 'y';")
            appendLine("if (new StringEquality().equalsAnyStrings(left, right) !== 2) throw Error('long any');")
            appendLine("if (new StringEquality().equalsUnknownStrings(left, right) !== 2) throw Error('long unknown');")
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "long-string-equality",
            timeoutMessage = "Node replay timed out",
        )
    }
}
