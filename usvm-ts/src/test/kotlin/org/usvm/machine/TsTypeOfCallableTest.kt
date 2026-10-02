package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsTypeOfCallableTest {
    @TempDir
    lateinit var directory: Path

    private val machineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
    )

    @TestFactory
    fun `typeof results replay in Node`(): List<DynamicTest> {
        val source = getResourcePath("/models/TypeOfCallable.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val methods = scene.projectClasses.single { it.name == "TypeOfCallable" }.methods.associateBy { it.name }

        return expectedResults.map { (name, expected) ->
            DynamicTest.dynamicTest(name) {
                val method = methods.getValue(name)
                val events = mutableListOf<TsUnknownCallEvent>()
                val observer = object : TsInterpreterObserver {
                    override fun onUnknownCall(event: TsUnknownCallEvent) {
                        events += event
                    }
                }

                val tests = TsMachine(scene, options = machineOptions, tsOptions = TsOptions(), observer = observer)
                    .use { machine ->
                        machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
                    }

                assertTrue(tests.isNotEmpty(), "$name produced no witnesses; unknown calls: $events")
                assertTrue(events.isEmpty(), "$name reached an unsupported call: $events")
                tests.forEach { test ->
                    assertEquals(expected, assertIs<TsTestValue.TsString>(test.returnValue).value)
                }

                val script = buildString {
                    appendLine(source.readText())
                    tests.forEachIndexed { index, test ->
                        val args = test.before.parameters.joinToString { value ->
                            assertIs<TsTestValue.TsBoolean>(value).value.toString()
                        }
                        val result = assertIs<TsTestValue.TsString>(test.returnValue).value
                        appendLine("if (new TypeOfCallable().$name($args) !== '$result') {")
                        appendLine("  throw Error('$name witness $index');")
                        appendLine("}")
                    }
                }
                assertReplay(script, name)
            }
        }
    }

    @Test
    fun `typeof distinguishes a conditional callable from an object`() {
        val source = getResourcePath("/models/TypeOfCallable.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val method = scene.projectClasses.single { it.name == "TypeOfCallable" }.methods
            .single { it.name == "conditionalValue" }

        val tests = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
            machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }

        assertTrue(tests.isNotEmpty(), "conditionalValue produced no witnesses")
        val inputs = tests.map { test ->
            val flag = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
            val result = assertIs<TsTestValue.TsString>(test.returnValue).value
            assertEquals(if (flag) "function" else "object", result)
            flag
        }.toSet()
        assertEquals(setOf(false, true), inputs)

        val script = buildString {
            appendLine(source.readText())
            tests.forEachIndexed { index, test ->
                val flag = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
                val result = assertIs<TsTestValue.TsString>(test.returnValue).value
                appendLine("if (new TypeOfCallable().conditionalValue($flag) !== '$result') {")
                appendLine("  throw Error('conditionalValue witness $index');")
                appendLine("}")
            }
        }
        assertReplay(script, name = "conditionalValue")
    }

    private fun assertReplay(source: String, name: String) {
        val script = directory.resolve("$name.ts")
        val output = directory.resolve("$name.out")
        script.writeText(source)

        val process = ProcessBuilder("node", "--experimental-strip-types", script.toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start()

        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "$name replay timed out")
            assertEquals(0, process.exitValue(), "${output.readText()}\n$source")
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private companion object {
        val expectedResults = mapOf(
            "arrow" to "function",
            "functionExpression" to "function",
            "storedArrow" to "function",
            "storedInObject" to "function",
            "namedFunction" to "function",
            "ordinaryObject" to "object",
            "nullValue" to "object",
            "stringValue" to "string",
            "numberValue" to "number",
            "booleanValue" to "boolean",
            "undefinedValue" to "undefined",
        )
    }
}
