package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
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

private const val REPLAY_FAILURE_CONTEXT_LIMIT = 1000

class TsSymbolicStringInputTest {
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
}
