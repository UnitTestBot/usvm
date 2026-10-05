package org.usvm.util

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsStringWitnessResolverTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/lang/SymbolicStringInput.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `string inferred from any has replayable length witnesses`() {
        val method = getMethod(methodName = "anyStringLength", className = "SymbolicStringInput")
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )

        val states = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyze(methods = listOf(method))
        }
        val tests = states.map { state -> TsTestResolver().resolve(method, state) }

        assertTrue(states.isNotEmpty())
        assertEquals(
            setOf(0.0, 1.0, 2.0),
            tests.map { test -> assertIs<TsTestValue.TsNumber>(test.returnValue).number }.toSet(),
        )
        tests.forEach { test ->
            val input = test.before.parameters.single()
            val expected = when {
                input !is TsTestValue.TsString -> 0.0
                input.value.length == 1 -> 1.0
                else -> 2.0
            }

            assertEquals(expected, assertIs<TsTestValue.TsNumber>(test.returnValue).number, message = "$test")
        }

        val script = buildString {
            appendLine(getResourcePath(tsPath).readText())
            appendLine("if (new SymbolicStringInput().anyStringLength(\"\") !== 2) throw Error('empty string');")
            appendLine("if (new SymbolicStringInput().anyStringLength(\"x\") !== 1) throw Error('one-char string');")
            tests.forEachIndexed { index, test ->
                val input = when (val value = test.before.parameters.single()) {
                    TsTestValue.TsUndefined -> "undefined"
                    TsTestValue.TsNull -> "null"
                    is TsTestValue.TsBoolean -> value.value.toString()
                    is TsTestValue.TsNumber -> value.number.toString()
                    is TsTestValue.TsString -> jsString(value.value)
                    is TsTestValue.TsClass -> "{}"
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
            timeoutMessage = "Any-string replay timed out",
        )
    }
}
