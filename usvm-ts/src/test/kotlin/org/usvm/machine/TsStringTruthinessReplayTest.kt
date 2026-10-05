package org.usvm.machine

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
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import org.usvm.util.jsString
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

private const val REPLAY_FAILURE_CONTEXT_LIMIT = 1000

class TsStringTruthinessReplayTest : TsMethodTestRunner() {
    private data class FieldWitness(val value: TsTestValue, val result: Double)

    @TempDir
    lateinit var directory: Path

    override val scene: EtsScene = loadScene("/samples/lang/SymbolicStringInput.ts")

    private val machineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
    )

    @Test
    fun `symbolic strings have both truthiness outcomes`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = getMethod(methodName = "conditional", className = "SymbolicStringInput"),
            { input, result -> input.value.isEmpty() && result.number == 0.0 },
            { input, result -> input.value.isNotEmpty() && result.number == 1.0 },
            invariants = arrayOf({ input, result -> result.number == if (input.value.isEmpty()) 0.0 else 1.0 }),
        )
    }

    @Test
    fun `string field conditions and equality preserve both outcomes`() {
        for (name in listOf("fieldConditional", "fieldNegation", "fieldAnd", "fieldOr", "fieldEqualsEmpty")) {
            discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
                method = getMethod(methodName = name, className = "SymbolicStringInput"),
                { _, result -> result.number == 0.0 },
                { _, result -> result.number == 1.0 },
                invariants = arrayOf({ input, result ->
                    val value = assertIs<TsTestValue.TsString>(input.properties.getValue("value")).value
                    val expected = if (name == "fieldEqualsEmpty") value.isEmpty() else value.isNotEmpty()

                    result.number == if (expected) 1.0 else 0.0
                }),
            )
        }
    }

    @Test
    fun `literal string fields preserve exact input contents`() {
        for ((name, expected) in mapOf("emptyLiteralField" to "", "nonemptyLiteralField" to "A\u0000\uD83D\uDE00")) {
            discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
                method = getMethod(methodName = name, className = "SymbolicStringInput"),
                { input, result ->
                    val value = assertIs<TsTestValue.TsString>(input.properties.getValue("value")).value

                    value == expected && result.number == if (expected.isEmpty()) 0.0 else 1.0
                },
            )
        }
    }

    @Test
    fun `writes determine subsequent field truthiness`() {
        for ((name, expected) in mapOf("writtenNonEmptyField" to 1.0, "writtenEmptyField" to 0.0)) {
            discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
                method = getMethod(methodName = name, className = "SymbolicStringInput"),
                { _, result -> result.number == expected },
                invariants = arrayOf({ _, result -> result.number == expected }),
            )
        }
    }

    @Test
    fun `conditional write preserves untouched empty and nonempty fields`() {
        options = options.copy(stateCollectionStrategy = StateCollectionStrategy.ALL, stopOnCoverage = 0)

        discoverProperties<TsTestValue.TsClass, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = getMethod(methodName = "conditionallyWrittenField", className = "SymbolicStringInput"),
            { _, overwrite, result -> overwrite.value && result.number == 0.0 },
            { _, overwrite, result -> !overwrite.value && result.number == 0.0 },
            { _, overwrite, result -> !overwrite.value && result.number == 1.0 },
            invariants = arrayOf({ input, overwrite, result ->
                val truthy = !overwrite.value &&
                    assertIs<TsTestValue.TsString>(input.properties.getValue("value")).value.isNotEmpty()

                result.number == if (truthy) 1.0 else 0.0
            }),
        )
    }

    @Test
    fun `symbolic and literal string truthiness replays in Node`() {
        val source = getResourcePath("/samples/lang/SymbolicStringInput.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val names = setOf(
            "truthy",
            "conditional",
            "emptyLiteralIsFalsy",
            "nullCodeUnitIsTruthy",
            "surrogatePairIsTruthy",
        )
        val methods = scene.projectClasses.single { it.name == "SymbolicStringInput" }.methods
            .filter { it.name in names }
            .associateBy { it.name }
        assertEquals(names, methods.keys)

        val tests = TsMachine(scene, options = machineOptions, tsOptions = TsOptions(maxArraySize = 2)).use { machine ->
            methods.mapValues { (_, method) ->
                machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
            }
        }

        for (name in listOf("truthy", "conditional")) {
            val generated = tests.getValue(name)
            assertTrue(generated.isNotEmpty(), "$name produced no witnesses")
            val outcomes = generated.map { test ->
                val input = assertIs<TsTestValue.TsString>(test.before.parameters.single()).value
                val result = when (val value = test.returnValue) {
                    is TsTestValue.TsBoolean -> value.value
                    is TsTestValue.TsNumber -> value.number == 1.0
                    else -> error("Unexpected result for $name: $value")
                }

                assertEquals(input.isNotEmpty(), result, message = test.toString())
                result
            }.toSet()
            if (name == "conditional") {
                assertEquals(setOf(false, true), outcomes)
            }
        }

        for ((name, expected) in listOf(
            "emptyLiteralIsFalsy" to false,
            "nullCodeUnitIsTruthy" to true,
            "surrogatePairIsTruthy" to true,
        )) {
            val generated = tests.getValue(name)
            assertTrue(generated.isNotEmpty(), "$name produced no witnesses")
            generated.forEach { test ->
                assertEquals(expected, assertIs<TsTestValue.TsBoolean>(test.returnValue).value)
            }
        }

        val script = buildString {
            appendLine(source.readText())
            tests.forEach { (name, generated) ->
                generated.forEachIndexed { index, test ->
                    val args = test.before.parameters.joinToString { value ->
                        jsString(assertIs<TsTestValue.TsString>(value).value)
                    }
                    val expected = when (val result = test.returnValue) {
                        is TsTestValue.TsBoolean -> result.value.toString()
                        is TsTestValue.TsNumber -> result.number.toString()
                        else -> error("Unexpected result for $name: $result")
                    }

                    appendLine("if (new SymbolicStringInput().$name($args) !== $expected) {")
                    appendLine("  throw Error('$name witness $index');")
                    appendLine("}")
                }
            }
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "string-truthiness",
            timeoutMessage = "string-truthiness replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }

    @Test
    fun `typed string field truthiness replays in Node`() {
        val source = getResourcePath("/samples/lang/SymbolicStringInput.ts")
        val names = setOf("fieldConditional", "fieldNegation", "fieldAnd", "fieldOr")
        val tests = analyzeFieldMethods(
            source = source,
            names = names,
            tsOptions = TsOptions(maxArraySize = 2),
            requireComplete = false,
        )

        tests.forEach { (name, generated) ->
            assertTrue(generated.isNotEmpty(), "$name produced no witnesses")
            val outcomes = generated.map { test ->
                val witness = fieldWitness(test)
                val value = assertIs<TsTestValue.TsString>(witness.value).value
                val result = witness.result

                assertEquals(if (value.isEmpty()) 0.0 else 1.0, result, message = test.toString())
                result
            }.toSet()
            assertEquals(setOf(0.0, 1.0), outcomes, message = name)
        }

        val script = buildString {
            appendLine(source.readText())
            tests.forEach { (name, generated) ->
                generated.forEachIndexed { index, test ->
                    appendFieldWitness(name = name, index = index, witness = fieldWitness(test))
                }
            }
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "string-field-truthiness",
            timeoutMessage = "string-field-truthiness replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }

    @Test
    fun `typed string fields have no unsupported truthiness or equality paths`() {
        val source = getResourcePath("/samples/lang/SymbolicStringInput.ts")
        val names = setOf("fieldConditional", "fieldEqualsEmpty")
        val generated = analyzeFieldMethods(
            source = source,
            names = names,
            tsOptions = TsOptions(maxArraySize = 2),
            requireComplete = true,
        )

        val script = buildString {
            appendLine(source.readText())
            generated.forEach { (name, tests) ->
                assertTrue(tests.isNotEmpty(), "$name produced no witnesses")
                val outcomes = tests.mapIndexed { index, test ->
                    val witness = fieldWitness(test)
                    val value = assertIs<TsTestValue.TsString>(witness.value).value
                    val result = witness.result
                    val expected = if (name == "fieldEqualsEmpty") value.isEmpty() else value.isNotEmpty()
                    assertEquals(if (expected) 1.0 else 0.0, result, message = test.toString())

                    appendFieldWitness(name = name, index = index, witness = witness)
                    result
                }.toSet()
                assertEquals(setOf(0.0, 1.0), outcomes, name)
            }
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "string-field-truthiness-and-equality",
            timeoutMessage = "string-field-truthiness-and-equality replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }

    @Test
    fun `literal typed string fields retain exact contents and truthiness`() {
        val source = getResourcePath("/samples/lang/SymbolicStringInput.ts")
        val expected = mapOf(
            "emptyLiteralField" to ("" to 0.0),
            "nonemptyLiteralField" to ("A\u0000\uD83D\uDE00" to 1.0),
        )
        val generated = analyzeFieldMethods(
            source = source,
            names = expected.keys,
            tsOptions = TsOptions(maxArraySize = 4),
            requireComplete = true,
        )

        val script = buildString {
            appendLine(source.readText())
            generated.forEach { (name, tests) ->
                assertTrue(tests.isNotEmpty(), "$name produced no witnesses")
                val (expectedValue, expectedResult) = expected.getValue(name)
                tests.forEachIndexed { index, test ->
                    val witness = fieldWitness(test)
                    val value = assertIs<TsTestValue.TsString>(witness.value).value
                    val result = witness.result
                    assertEquals(expectedValue, value, message = test.toString())
                    assertEquals(expectedResult, result, message = test.toString())

                    appendFieldWitness(name = name, index = index, witness = witness)
                }
            }
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "literal-string-field-truthiness",
            timeoutMessage = "literal-string-field-truthiness replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }

    @Test
    fun `literal field beyond configured bound is explicitly unsupported`() {
        val source = getResourcePath("/samples/lang/SymbolicStringInput.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val method = scene.projectClasses.single { it.name == "SymbolicStringInput" }.methods
            .single { it.name == "longLiteralField" }

        val analysis = TsMachine(
            scene,
            options = machineOptions.copy(throwExceptionOnStepFailure = true),
            tsOptions = TsOptions(maxArraySize = 4),
        ).use { machine -> machine.analyzeWithOutcome(listOf(method)) }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertTrue(analysis.states.isEmpty())
        assertEquals(
            listOf("Literal string field exceeds configured string length 4"),
            analysis.unsupportedPaths,
        )
    }

    @Test
    fun `written string field keeps literal truthiness`() {
        val source = getResourcePath("/samples/lang/SymbolicStringInput.ts")
        val expectedResults = mapOf("writtenNonEmptyField" to 1.0, "writtenEmptyField" to 0.0)
        val tests = analyzeFieldMethods(
            source = source,
            names = expectedResults.keys,
            tsOptions = TsOptions(maxArraySize = 2),
            requireComplete = false,
        )

        val script = buildString {
            appendLine(source.readText())
            tests.forEach { (name, generated) ->
                assertTrue(generated.isNotEmpty(), "$name produced no witnesses")
                generated.forEachIndexed { index, test ->
                    val witness = fieldWitness(test)
                    assertEquals(expectedResults.getValue(name), witness.result, message = test.toString())

                    appendFieldWitness(name = name, index = index, witness = witness)
                }
            }
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "written-string-field-truthiness",
            timeoutMessage = "written-string-field-truthiness replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }

    @Test
    fun `conditional string field write preserves both paths`() {
        val source = getResourcePath("/samples/lang/SymbolicStringInput.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val clazz = scene.projectClasses.single { it.name == "SymbolicStringInput" }
        val method = clazz.methods.single { it.name == "conditionallyWrittenField" }

        val tests = TsMachine(scene, options = machineOptions, tsOptions = TsOptions(maxArraySize = 2)).use { machine ->
            machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }

        assertTrue(tests.isNotEmpty())
        val outcomes = tests.map { test ->
            val input = assertIs<TsTestValue.TsClass>(test.before.parameters[0])
            val overwrite = assertIs<TsTestValue.TsBoolean>(test.before.parameters[1]).value
            val field = input.properties.getValue("value")
            val result = assertIs<TsTestValue.TsNumber>(test.returnValue).number
            val isTruthy = !overwrite && assertIs<TsTestValue.TsString>(field).value.isNotEmpty()
            val expected = if (isTruthy) 1.0 else 0.0

            assertEquals(expected, result, message = test.toString())
            result
        }.toSet()
        assertEquals(setOf(0.0, 1.0), outcomes)

        val script = buildString {
            appendLine(source.readText())
            tests.forEachIndexed { index, test ->
                val input = assertIs<TsTestValue.TsClass>(test.before.parameters[0])
                val overwrite = assertIs<TsTestValue.TsBoolean>(test.before.parameters[1]).value
                val value = when (val field = input.properties.getValue("value")) {
                    is TsTestValue.TsString -> jsString(field.value)
                    is TsTestValue.TsUndefined -> "undefined"
                    else -> error("Unexpected field value: $field")
                }
                val expected = assertIs<TsTestValue.TsNumber>(test.returnValue).number

                val call = "new SymbolicStringInput().conditionallyWrittenField({ value: $value }, $overwrite)"
                appendLine("if ($call !== $expected) {")
                appendLine("  throw Error('conditional field witness $index');")
                appendLine("}")
            }
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "conditional-string-field-truthiness",
            timeoutMessage = "conditional-string-field-truthiness replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }

    private fun analyzeFieldMethods(
        source: Path,
        names: Set<String>,
        tsOptions: TsOptions,
        requireComplete: Boolean,
    ): Map<String, List<TsTest>> {
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val methods = scene.projectClasses.single { it.name == "SymbolicStringInput" }.methods
            .filter { it.name in names }
            .associateBy { it.name }
        assertEquals(names, methods.keys)

        val options = if (requireComplete) {
            machineOptions.copy(
                stateCollectionStrategy = StateCollectionStrategy.ALL,
                throwExceptionOnStepFailure = true,
            )
        } else {
            machineOptions
        }

        return TsMachine(scene, options = options, tsOptions = tsOptions).use { machine ->
            methods.mapValues { (_, method) ->
                val states = if (requireComplete) {
                    val analysis = machine.analyzeWithOutcome(listOf(method))
                    assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
                    assertTrue(analysis.unsupportedPaths.isEmpty(), "$method: ${analysis.unsupportedPaths}")
                    analysis.states
                } else {
                    machine.analyze(listOf(method))
                }

                states.map { state -> TsTestResolver().resolve(method, state) }
            }
        }
    }

    private fun fieldWitness(test: TsTest): FieldWitness {
        val input = assertIs<TsTestValue.TsClass>(test.before.parameters.single())
        val value = input.properties.getValue("value")
        val result = assertIs<TsTestValue.TsNumber>(test.returnValue).number

        return FieldWitness(value = value, result = result)
    }

    private fun StringBuilder.appendFieldWitness(name: String, index: Int, witness: FieldWitness) {
        val encodedValue = when (val value = witness.value) {
            is TsTestValue.TsString -> jsString(value.value)
            is TsTestValue.TsUndefined -> "undefined"
            is TsTestValue.TsNull -> "null"
            else -> error("Unexpected field value: $value")
        }

        appendLine("if (new SymbolicStringInput().$name({ value: $encodedValue }) !== ${witness.result}) {")
        appendLine("  throw Error('$name witness $index');")
        appendLine("}")
    }
}
