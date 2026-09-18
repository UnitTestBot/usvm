package org.usvm.machine.call

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsArrayShiftMatrixTest {
    @TempDir
    lateinit var directory: Path

    @TestFactory
    fun `concrete array matrix agrees with JavaScript`(): List<DynamicTest> = concreteArrayMatrix(methodName = "shift")

    @TestFactory
    fun `concrete pop matrix agrees with JavaScript`(): List<DynamicTest> = concreteArrayMatrix(methodName = "pop")

    private fun concreteArrayMatrix(methodName: String): List<DynamicTest> {
        val cases = concreteCases()
        val source = directory.resolve("ArrayRemovalMatrix-$methodName.ts")
        source.writeText(renderSource(cases, typed = true, methodName = methodName))
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val methods = scene.projectClasses.single { it.name == "ArrayShiftMatrix" }.methods.associateBy { it.name }
        val invocations = cases.indices.joinToString(separator = ",") { "new ArrayShiftMatrix().case$it()" }
        val oracleSource = renderSource(cases, typed = false, methodName = methodName) +
            "\nconsole.log([$invocations].join('\\n'));\n"
        val expected = runJavaScript(oracleSource)

        assertEquals(cases.size, expected.size)
        assertTrue(expected.all { it == "1" }, "Generated invariants must hold in native JavaScript")

        return cases.mapIndexed { index, case ->
            DynamicTest.dynamicTest(case.label) {
                val events = mutableListOf<TsUnknownCallEvent>()
                val observer = object : TsInterpreterObserver {
                    override fun onUnknownCall(event: TsUnknownCallEvent) {
                        events += event
                    }
                }
                val method = methods.getValue("case$index")

                val values = TsMachine(scene, options = machineOptions, tsOptions = TsOptions(), observer = observer)
                    .use { machine ->
                        machine.analyze(listOf(method)).map { state ->
                            TsTestResolver().resolve(method, state).returnValue
                        }
                    }

                val actual = values.map { assertIs<TsTestValue.TsNumber>(it).number }
                assertEquals(listOf(expected[index].toDouble()), actual)
                assertEquals(case.shiftCount, events.size)
                assertTrue(events.all { it.decision == TsUnknownCallDecision.ModelApplied("ts.array.$methodName") })
            }
        }
    }

    private fun concreteCases(): List<ShiftCase> {
        val families = listOf(
            Family(name = "numbers", type = "number", values = listOf("-3", "0", "17")),
            Family(name = "booleans", type = "boolean", values = listOf("true", "false", "true")),
            Family(name = "strings", type = "string", values = listOf("'left'", "''", "'right'")),
            Family(name = "references", type = "ShiftElement", values = listOf("first", "second", "first")),
            Family(name = "nulls", type = "null", values = listOf("null", "null")),
            Family(name = "undefineds", type = "undefined", values = listOf("undefined", "undefined")),
            Family(name = "mixed", type = "any", values = listOf("17", "true", "first", "'left'", "null", "undefined")),
            Family(name = "union", type = "(number | boolean)", values = listOf("17", "true", "-3", "false")),
            Family(
                name = "nullable",
                type = "(ShiftElement | null | undefined)",
                values = listOf("null", "first", "undefined"),
            ),
            Family(name = "nested values", type = "any", values = listOf("true", "nested", "first")),
            Family(name = "function values", type = "any", values = listOf("false", "callback", "undefined")),
            Family(name = "special numbers", type = "number", values = listOf("NaN", "Infinity", "-Infinity", "-0.0")),
        )

        return buildList {
            for (family in families) {
                for (size in listOf(0, 1, family.values.size).distinct()) {
                    for (type in listOf(family.type, "any", "unknown").distinct()) {
                        val values = family.values.take(size)
                        for (drain in listOf(false, true)) {
                            add(
                                ShiftCase(
                                    label = "${family.name}, $type[], size=$size, drain=$drain",
                                    type = type,
                                    values = values,
                                    drain = drain,
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun renderSource(cases: List<ShiftCase>, typed: Boolean, methodName: String): String = buildString {
        appendLine("class ShiftElement {}")
        appendLine("class ArrayShiftMatrix {")
        cases.forEachIndexed { index, case ->
            appendLine("case$index() {")
            appendLine("const first = new ShiftElement(); const second = new ShiftElement();")
            appendLine("const nested = [7, 8]; const callback = () => 1;")
            val annotation = if (typed) ": ${case.type}[]" else ""
            appendLine("const original$annotation = [${case.values.joinToString()}];")
            appendLine("const values$annotation = original;")

            repeat(case.shiftCount) { shift ->
                appendLine("const removed$shift = values.$methodName();")
                val removedIndex = if (methodName == "pop") case.values.lastIndex - shift else shift
                val value = case.values.getOrElse(removedIndex) { "undefined" }
                appendLine("if (!(${sameValue("removed$shift", value)})) return -1;")
                val tail = if (methodName == "pop") {
                    case.values.dropLast(shift + 1)
                } else {
                    case.values.drop(shift + 1)
                }
                appendLine("if (original.length !== ${tail.size} || values.length !== ${tail.size}) return -2;")
                tail.forEachIndexed { tailIndex, tailValue ->
                    appendLine("if (!(${sameValue("original[$tailIndex]", tailValue)})) return -3;")
                }
            }
            appendLine("return 1;")
            appendLine("}")
        }
        appendLine("}")
    }

    private fun sameValue(actual: String, expected: String): String = when (expected) {
        "NaN" -> "Number.isNaN($actual)"
        "-0.0" -> "1 / $actual === -Infinity"
        else -> "$actual === $expected"
    }

    private fun runJavaScript(source: String): List<String> {
        val script = directory.resolve("oracle.js")
        val output = directory.resolve("oracle.out")
        script.writeText(source)
        val process = ProcessBuilder("node", script.toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start()

        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "JavaScript oracle timed out")
            assertEquals(0, process.exitValue(), output.readText())
            return output.readText().trim().lines()
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private data class Family(val name: String, val type: String, val values: List<String>)

    private data class ShiftCase(val label: String, val type: String, val values: List<String>, val drain: Boolean) {
        val shiftCount: Int get() = if (drain) values.size + 1 else 1
    }

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
