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

private const val REPLAY_FAILURE_CONTEXT_LIMIT = 1000

class TsSymbolicStringInputTest {
    private data class FieldWitness(val value: TsTestValue, val result: Double)

    @TempDir
    lateinit var directory: Path

    private val machineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
    )

    @Test
    fun `symbolic string witnesses replay including empty and nonempty values`() {
        val source = getResourcePath("/models/SymbolicStringInput.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val methods = scene.projectClasses.single { it.name == "SymbolicStringInput" }.methods
            .filter { it.name in setOf("identity", "lengthOne", "literal", "literalLength") }
            .associateBy { it.name }
        val tests = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
            methods.mapValues { (_, method) ->
                machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
            }
        }

        val identityTests = tests.getValue("identity")
        assertTrue(identityTests.isNotEmpty())
        identityTests.forEach { test ->
            val input = assertIs<TsTestValue.TsString>(test.before.parameters.single()).value
            val result = assertIs<TsTestValue.TsString>(test.returnValue).value
            assertEquals(input, result, message = test.toString())
        }

        val lengthTests = tests.getValue("lengthOne")
        assertEquals(
            setOf(0.0, 1.0),
            lengthTests.map { test ->
                assertIs<TsTestValue.TsNumber>(test.returnValue).number
            }.toSet()
        )
        assertTrue(
            lengthTests.any { test ->
                assertIs<TsTestValue.TsString>(test.before.parameters.single()).value.isEmpty()
            }
        )
        assertTrue(
            lengthTests.any { test ->
                assertIs<TsTestValue.TsString>(test.before.parameters.single()).value.isNotEmpty()
            }
        )

        val literalTests = tests.getValue("literal")
        assertTrue(literalTests.isNotEmpty())
        literalTests.forEach { test ->
            assertEquals("A\u0000\u03a9\uD83D\uDE00", assertIs<TsTestValue.TsString>(test.returnValue).value)
        }

        val literalLengthTests = tests.getValue("literalLength")
        assertTrue(literalLengthTests.isNotEmpty())
        literalLengthTests.forEach { test ->
            assertEquals(5.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        }

        val script = buildString {
            appendLine(source.readText())
            tests.forEach { (name, generated) ->
                generated.forEachIndexed { index, test ->
                    val args = test.before.parameters.joinToString { value ->
                        jsString(assertIs<TsTestValue.TsString>(value).value)
                    }
                    val expected = when (val result = test.returnValue) {
                        is TsTestValue.TsString -> jsString(result.value)
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
            name = "basic-strings",
            timeoutMessage = "basic-strings replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }

    @Test
    fun `string backing cannot alias an input number array`() {
        val source = getResourcePath("/models/SymbolicStringInput.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val method = scene.projectClasses.single { it.name == "SymbolicStringInput" }
            .methods
            .single { it.name == "independentArrayLength" }

        val tests = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
            machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }

        assertTrue(tests.isNotEmpty())
        assertEquals(
            setOf(0.0, 2.0),
            tests.map { test ->
                assertIs<TsTestValue.TsNumber>(test.returnValue).number
            }.toSet()
        )

        val script = buildString {
            appendLine(source.readText())
            tests.forEachIndexed { index, test ->
                val input = assertIs<TsTestValue.TsString>(test.before.parameters[0]).value
                val array = assertIs<TsTestValue.TsArray<*>>(test.before.parameters[1])
                val elements = array.values.joinToString { value ->
                    assertIs<TsTestValue.TsNumber>(value).number.toString()
                }
                val expected = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                val encodedInput = jsString(input)

                appendLine(
                    "if (new SymbolicStringInput().independentArrayLength($encodedInput, [$elements]) !== $expected) {"
                )
                appendLine("  throw Error('array alias witness $index');")
                appendLine("}")
            }
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "array-isolation",
            timeoutMessage = "array-isolation replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }

    @Test
    fun `configured string bound is shared with concrete test extraction`() {
        val source = getResourcePath("/models/SymbolicStringInput.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val method = scene.projectClasses.single { it.name == "SymbolicStringInput" }
            .methods
            .single { it.name == "lengthIs10001" }
        val maxStringLength = 10_001

        val tests = TsMachine(
            scene,
            options = machineOptions,
            tsOptions = TsOptions(maxArraySize = maxStringLength),
        ).use { machine ->
            machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }

        assertEquals(
            setOf(0.0, 1.0),
            tests.map { test ->
                assertIs<TsTestValue.TsNumber>(test.returnValue).number
            }.toSet()
        )
        val longWitness = tests.single { test ->
            assertIs<TsTestValue.TsNumber>(test.returnValue).number == 1.0
        }
        assertEquals(
            maxStringLength,
            assertIs<TsTestValue.TsString>(longWitness.before.parameters.single()).value.length
        )

        val script = buildString {
            appendLine(source.readText())
            tests.forEachIndexed { index, test ->
                val input = assertIs<TsTestValue.TsString>(test.before.parameters.single()).value
                val expected = assertIs<TsTestValue.TsNumber>(test.returnValue).number

                appendLine("if (new SymbolicStringInput().lengthIs10001(${jsString(input)}) !== $expected) {")
                appendLine("  throw Error('string bound witness $index');")
                appendLine("}")
            }
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "string-bound",
            timeoutMessage = "string-bound replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }

    @Test
    fun `symbolic and literal string truthiness replays in Node`() {
        val source = getResourcePath("/models/SymbolicStringInput.ts")
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
        val source = getResourcePath("/models/SymbolicStringInput.ts")
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
        val source = getResourcePath("/models/SymbolicStringInput.ts")
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
        val source = getResourcePath("/models/SymbolicStringInput.ts")
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
        val source = getResourcePath("/models/SymbolicStringInput.ts")
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
        val source = getResourcePath("/models/SymbolicStringInput.ts")
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
        val source = getResourcePath("/models/SymbolicStringInput.ts")
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

    @Test
    fun `string inferred from any without backing cannot become an empty witness`() {
        val source = getResourcePath("/models/SymbolicStringInput.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val method = scene.projectClasses.single { it.name == "SymbolicStringInput" }
            .methods
            .single { it.name == "anyStringLength" }

        val states = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
            machine.analyze(listOf(method))
        }

        assertTrue(states.isNotEmpty())
        val resolutions = states.map { state -> runCatching { TsTestResolver().resolve(method, state) } }
        val unsupported = resolutions.mapNotNull { it.exceptionOrNull() }
        assertTrue(
            unsupported.isNotEmpty(),
            "Expected an unbacked symbolic string: $resolutions",
        )
        unsupported.forEach { failure ->
            assertIs<TsUnsupportedWitnessException>(failure)
            assertTrue("missing backing array" in failure.message.orEmpty(), failure.toString())
        }

        val supported = resolutions.mapNotNull { it.getOrNull() }
        assertTrue(supported.isNotEmpty())
        val script = buildString {
            appendLine(source.readText())
            appendLine("if (new SymbolicStringInput().anyStringLength(\"\") !== 2) throw Error('empty string');")
            appendLine("if (new SymbolicStringInput().anyStringLength(\"x\") !== 1) throw Error('one-char string');")
            supported.forEachIndexed { index, test ->
                val input = when (val value = test.before.parameters.single()) {
                    TsTestValue.TsUndefined -> "undefined"
                    TsTestValue.TsNull -> "null"
                    is TsTestValue.TsBoolean -> value.value.toString()
                    is TsTestValue.TsNumber -> value.number.toString()
                    is TsTestValue.TsString -> jsString(value.value)
                    else -> error("Unexpected input for anyStringLength: $value")
                }
                val expected = assertIs<TsTestValue.TsNumber>(test.returnValue).number

                appendLine("if (new SymbolicStringInput().anyStringLength($input) !== $expected) {")
                appendLine("  throw Error('any string witness $index');")
                appendLine("}")
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "any-string",
            timeoutMessage = "any-string replay timed out",
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
