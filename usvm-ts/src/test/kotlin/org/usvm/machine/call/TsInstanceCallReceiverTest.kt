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
import org.usvm.util.getResourcePath
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsInstanceCallReceiverTest {
    @TempDir
    lateinit var directory: Path

    @TestFactory
    fun `normalized receivers preserve supported calls and exceptional branches`(): List<DynamicTest> {
        val source = getResourcePath("/models/InstanceCallReceiver.ts")
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val methods = scene.projectClasses.single { it.name == "InstanceCallReceiver" }.methods.associateBy { it.name }

        return cases.map { case ->
            DynamicTest.dynamicTest(case.method) {
                val method = methods.getValue(case.method)
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

                val results = tests.mapNotNull { (it.returnValue as? TsTestValue.TsNumber)?.number }.toSet()
                assertEquals(case.results, results)
                assertEquals(case.throws, tests.any { it.returnValue is TsTestValue.TsException })
                assertTrue(tests.isNotEmpty())
                if (method.parameters.singleOrNull()?.name == "index") {
                    val indices = tests.map { assertIs<TsTestValue.TsNumber>(it.before.parameters.single()).number }
                    assertTrue(indices.containsAll(listOf(0.0, 1.0)), "Both receiver alternatives must be explored")
                }

                if (case.method == "wrappedShift") {
                    assertEquals(TsUnknownCallDecision.ModelApplied("ts.array.shift"), events.single().decision)
                }
                if (case.method == "customShift") assertTrue(events.isEmpty())

                val replay = buildString {
                    appendLine(source.readText())
                    tests.forEachIndexed { index, test ->
                        val args = test.before.parameters.joinToString(transform = ::jsValue)
                        val expected = if (test.returnValue is TsTestValue.TsException) {
                            "'throws'"
                        } else {
                            assertIs<TsTestValue.TsNumber>(test.returnValue).number.toString()
                        }
                        appendLine("{")
                        appendLine("let actual;")
                        appendLine("try { actual = new InstanceCallReceiver().${case.method}($args); }")
                        appendLine("catch { actual = 'throws'; }")
                        appendLine("if (actual !== $expected) throw Error('case ${case.method}, state $index');")
                        appendLine("}")
                    }
                }
                assertReplay(replay, case.method)
            }
        }
    }

    private fun jsValue(value: TsTestValue): String = when (value) {
        TsTestValue.TsUndefined -> "undefined"
        TsTestValue.TsNull -> "null"
        is TsTestValue.TsBoolean -> value.value.toString()
        is TsTestValue.TsNumber -> value.number.toString()
        is TsTestValue.TsString -> jsString(value.value)
        is TsTestValue.TsClass -> value.properties.entries.joinToString(prefix = "({", postfix = "})") {
            "${jsString(it.key)}: ${jsValue(it.value)}"
        }
        else -> error("Unsupported receiver input: $value")
    }

    private fun jsString(value: String): String = value.map { "\\u%04x".format(it.code) }.joinToString(
        separator = "",
        prefix = "\"",
        postfix = "\"",
    )

    private fun assertReplay(source: String, name: String) {
        val script = directory.resolve("$name.ts")
        val output = directory.resolve("$name.out")
        script.writeText(source)
        val process = ProcessBuilder("node", "--experimental-strip-types", script.toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start()

        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Receiver replay timed out")
            assertEquals(0, process.exitValue(), "${output.readText()}\n$source")
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private data class Case(
        val method: String,
        val results: Set<Double>,
        val throws: Boolean = false,
    )

    private companion object {
        val cases = listOf(
            Case(method = "wrappedShift", results = setOf(1.0)),
            Case(method = "wrappedPop", results = setOf(1.0)),
            Case(method = "wrappedPush", results = setOf(1.0)),
            Case(method = "wrappedReverse", results = setOf(1.0)),
            Case(method = "wrappedFill", results = setOf(1.0)),
            Case(method = "wrappedUnshift", results = setOf(1.0)),
            Case(method = "wrappedSlice", results = setOf(1.0)),
            Case(method = "wrappedSliceReversed", results = setOf(1.0)),
            Case(method = "wrappedSlicePastEnd", results = setOf(1.0)),
            Case(method = "wrappedSlicePastStart", results = setOf(1.0)),
            Case(method = "wrappedSliceNegative", results = setOf(1.0)),
            Case(method = "wrappedSliceEmpty", results = setOf(1.0)),
            Case(method = "wrappedConcat", results = setOf(1.0)),
            Case(method = "wrappedUserMethod", results = setOf(1.0)),
            Case(method = "customShift", results = setOf(1.0)),
            Case(method = "conditionalArrays", results = setOf(0.0, 1.0)),
            Case(method = "conditionalEmptyArray", results = setOf(0.0, 1.0)),
            Case(method = "arrayOrUserMethod", results = setOf(0.0, 1.0)),
            Case(method = "primitiveValueOf", results = setOf(0.0, 1.0)),
            Case(method = "primitiveToString", results = setOf(0.0, 1.0)),
            Case(method = "constrainedFake", results = setOf(0.0, 1.0, 2.0)),
            Case(method = "nullableReceiver", results = setOf(0.0, 1.0), throws = true),
            Case(method = "undefinedReceiver", results = setOf(0.0, 1.0), throws = true),
            Case(method = "nullToString", results = emptySet(), throws = true),
            Case(method = "undefinedValueOf", results = emptySet(), throws = true),
            Case(method = "nullShift", results = emptySet(), throws = true),
            Case(method = "undefinedShift", results = emptySet(), throws = true),
        )

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
