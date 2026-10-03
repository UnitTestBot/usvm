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
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import org.usvm.util.jsString
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsSymbolicStringConcatTest {
    @TempDir
    lateinit var directory: Path

    private val machineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
        throwExceptionOnStepFailure = true,
    )

    @Test
    fun `symbolic concatenation preserves code units and replays witnesses`() {
        val source = getResourcePath("/models/SymbolicStringConcat.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val methods = scene.projectClasses.single { it.name == "SymbolicStringConcat" }.methods
            .filter {
                it.name in setOf("append", "prepend", "combine", "combineTwo", "appendLength", "utf16", "primitives")
            }
            .associateBy { it.name }
        val tests = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
            methods.mapValues { (_, method) ->
                val result = machine.analyzeWithOutcome(listOf(method))
                assertEquals(TsAnalysisStopReason.EXHAUSTED, result.stopReason)
                result.states.map { state -> TsTestResolver().resolve(method, state) }
            }
        }

        assertEquals(7, tests.size)
        verifyWitnesses(tests)

        assertEquals(
            setOf(0.0, 1.0),
            tests.getValue("appendLength").map {
                assertIs<TsTestValue.TsNumber>(it.returnValue).number
            }.toSet(),
        )
        assertTrue(
            tests.getValue("combineTwo").any { test ->
                val inputs = test.before.parameters.map { assertIs<TsTestValue.TsString>(it).value }
                inputs.all { it.length == 1 } && assertIs<TsTestValue.TsString>(test.returnValue).value.length == 2
            },
            "No witness copied two nonempty symbolic strings",
        )

        replay(tests, source.readText())
    }

    @Test
    fun `concatenation result respects the witness length bound`() {
        val source = getResourcePath("/models/SymbolicStringConcat.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val method = scene.projectClasses.single { it.name == "SymbolicStringConcat" }
            .methods
            .single { it.name == "appendLength" }
        val (unsupportedPaths, tests) = TsMachine(
            scene,
            options = machineOptions.copy(throwExceptionOnStepFailure = false),
            tsOptions = TsOptions(maxArraySize = 2),
        ).use { machine ->
            val result = machine.analyzeWithOutcome(listOf(method))
            assertEquals(TsAnalysisStopReason.EXHAUSTED, result.stopReason)
            result.unsupportedPaths to result.states.map { state -> TsTestResolver().resolve(method, state) }
        }

        assertTrue(unsupportedPaths.any { "result exceeds the configured length bound" in it })
        assertEquals(
            setOf(0.0, 1.0),
            tests.map { assertIs<TsTestValue.TsNumber>(it.returnValue).number }.toSet(),
        )
        tests.forEach { test ->
            val input = assertIs<TsTestValue.TsString>(test.before.parameters.single()).value
            assertTrue(input.length <= 1, "Concatenated witness exceeds the configured bound")
        }

        replay(mapOf("appendLength" to tests), source.readText())
    }

    @Test
    fun `typed and literal string fields concatenate with modeled backing`() {
        val source = getResourcePath("/models/SymbolicStringConcat.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val names = setOf("fromField", "fromEmptyLiteralField", "fromNonemptyLiteralField")
        val methods = scene.projectClasses.single { it.name == "SymbolicStringConcat" }.methods
            .filter { it.name in names }
            .associateBy { it.name }
        assertEquals(names, methods.keys)

        val analyses = TsMachine(
            scene,
            options = machineOptions.copy(stateCollectionStrategy = StateCollectionStrategy.ALL),
            tsOptions = TsOptions(maxArraySize = 5),
        ).use { machine ->
            methods.mapValues { (_, method) ->
                machine.analyzeWithOutcome(listOf(method))
            }
        }

        val expectedLiterals = mapOf(
            "fromEmptyLiteralField" to "",
            "fromNonemptyLiteralField" to "A\u0000\uD83D\uDE00",
        )
        val script = buildString {
            appendLine(source.readText())
            analyses.forEach { (name, analysis) ->
                assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason, name)
                assertTrue(
                    analysis.unsupportedPaths.none { "modeled string backing" in it },
                    "$name: ${analysis.unsupportedPaths}",
                )
                if (name in expectedLiterals) {
                    assertTrue(analysis.unsupportedPaths.isEmpty(), "$name: ${analysis.unsupportedPaths}")
                }

                val method = methods.getValue(name)
                val tests = analysis.states.map { state -> TsTestResolver().resolve(method, state) }
                assertTrue(tests.isNotEmpty(), "$name produced no witnesses")
                tests.forEachIndexed { index, test ->
                    val holder = assertIs<TsTestValue.TsClass>(test.before.parameters.single())
                    val value = assertIs<TsTestValue.TsString>(holder.properties.getValue("value")).value
                    val result = assertIs<TsTestValue.TsString>(test.returnValue).value
                    expectedLiterals[name]?.let { expected -> assertEquals(expected, value, test.toString()) }
                    assertEquals(value + "!", result, test.toString())

                    appendLine(
                        "if (new SymbolicStringConcat().$name({ value: ${jsString(value)} }) " +
                            "!== ${jsString(result)}) {"
                    )
                    appendLine("  throw Error('$name witness $index');")
                    appendLine("}")
                }
            }
        }
        val typedValues = analyses.getValue("fromField").states.map { state ->
            val test = TsTestResolver().resolve(methods.getValue("fromField"), state)
            val holder = assertIs<TsTestValue.TsClass>(test.before.parameters.single())
            assertIs<TsTestValue.TsString>(holder.properties.getValue("value")).value
        }
        assertTrue(typedValues.any { it.isEmpty() })
        assertTrue(typedValues.any { it.isNotEmpty() })

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "string-field-concat",
            timeoutMessage = "Node replay timed out",
            failureContext = script.take(1000),
        )
    }

    @Test
    fun `concatenation result has modeled backing for string equality`() {
        val source = getResourcePath("/models/SymbolicStringConcat.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val method = scene.projectClasses.single { it.name == "SymbolicStringConcat" }
            .methods
            .single { it.name == "appendEquals" }
        val (unsupportedPaths, tests) = TsMachine(
            scene,
            options = machineOptions,
            tsOptions = TsOptions(maxArraySize = 4),
        ).use { machine ->
            val result = machine.analyzeWithOutcome(listOf(method))
            assertEquals(TsAnalysisStopReason.EXHAUSTED, result.stopReason)
            result.unsupportedPaths to result.states.map { state -> TsTestResolver().resolve(method, state) }
        }

        assertTrue(unsupportedPaths.isEmpty(), unsupportedPaths.toString())
        assertEquals(
            setOf(0.0, 1.0, 3.0),
            tests.map { assertIs<TsTestValue.TsNumber>(it.returnValue).number }.toSet(),
        )
        tests.forEach { test ->
            val input = assertIs<TsTestValue.TsString>(test.before.parameters.single()).value
            val expected = if (input.length != 1) 3.0 else if (input == "a") 1.0 else 0.0
            assertEquals(expected, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        }

        replay(mapOf("appendEquals" to tests), source.readText())
    }

    private fun verifyWitnesses(tests: Map<String, List<TsTest>>) {
        tests.forEach { (name, generated) ->
            assertTrue(generated.isNotEmpty(), "No $name witnesses")
            generated.forEach { test -> verifyWitness(name, test) }
        }
    }

    private fun verifyWitness(name: String, test: TsTest) {
        val inputs = test.before.parameters.map { assertIs<TsTestValue.TsString>(it).value }
        val expected = when (name) {
            "append" -> inputs.single() + "!"
            "prepend" -> "\u03A9" + inputs.single()
            "combine" -> inputs[0] + inputs[1]
            "combineTwo" -> if (inputs[0].length == 1 && inputs[1].length == 1) {
                inputs[0] + inputs[1]
            } else {
                ""
            }
            "utf16" -> inputs.single() + "\u0000\uD83D\uDE00"
            "primitives" -> inputs.single() + "truenullundefined1.5"
            "appendLength" -> null
            else -> error("Unexpected method: $name")
        }

        if (expected != null) {
            assertEquals(expected, assertIs<TsTestValue.TsString>(test.returnValue).value, test.toString())
        } else {
            val value = if (inputs.single().isEmpty()) 1.0 else 0.0
            assertEquals(value, assertIs<TsTestValue.TsNumber>(test.returnValue).number, test.toString())
        }
    }

    private fun replay(tests: Map<String, List<TsTest>>, source: String) {
        val statements = tests.flatMap { (name, generated) ->
            generated.mapIndexed { index, test -> replayStatement(name, index, test) }
        }
        val script = buildString {
            appendLine(source)
            statements.forEach(::appendLine)
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "symbolic-string-concat",
            timeoutMessage = "Node replay timed out",
            failureContext = script.take(1000),
        )
    }

    private fun replayStatement(name: String, index: Int, test: TsTest): String {
        val args = test.before.parameters.joinToString { value ->
            jsString(assertIs<TsTestValue.TsString>(value).value)
        }
        val expected = when (val result = test.returnValue) {
            is TsTestValue.TsString -> jsString(result.value)
            is TsTestValue.TsNumber -> result.number.toString()
            else -> error("Unexpected result for $name: $result")
        }

        return """
            if (new SymbolicStringConcat().$name($args) !== $expected) {
              throw Error('$name witness $index');
            }
        """.trimIndent()
    }
}
