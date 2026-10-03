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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsStringWitnessResolverTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/lang/SymbolicStringInput.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `string inferred from any without backing cannot become an empty witness`() {
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
        val resolutions = states.map { state -> runCatching { TsTestResolver().resolve(method, state) } }

        assertTrue(states.isNotEmpty())
        val unsupported = resolutions.mapNotNull { result -> result.exceptionOrNull() }
        assertTrue(unsupported.isNotEmpty(), "Expected an unbacked symbolic string: $resolutions")
        unsupported.forEach { failure ->
            assertIs<TsUnsupportedWitnessException>(failure)
            assertTrue("missing backing array" in failure.message.orEmpty(), failure.toString())
        }

        val supported = resolutions.mapNotNull { result -> result.getOrNull() }
        assertTrue(supported.isNotEmpty())
        val script = buildString {
            appendLine(getResourcePath(tsPath).readText())
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
            timeoutMessage = "Any-string replay timed out",
        )
    }
}
