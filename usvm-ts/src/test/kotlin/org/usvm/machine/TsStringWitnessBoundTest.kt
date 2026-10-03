package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
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
import kotlin.time.Duration

private const val REPLAY_FAILURE_CONTEXT_LIMIT = 1000

class TsStringWitnessBoundTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/lang/SymbolicStringInput.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `configured string bound is shared with concrete test extraction`() {
        val method = getMethod(methodName = "lengthIs10001", className = "SymbolicStringInput")
        val maxStringLength = 10_001
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )

        val tests = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(maxArraySize = maxStringLength),
        ).use { machine ->
            machine.analyze(methods = listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }

        assertEquals(
            setOf(0.0, 1.0),
            tests.map { test -> assertIs<TsTestValue.TsNumber>(test.returnValue).number }.toSet(),
        )
        val longWitness = tests.single { test ->
            assertIs<TsTestValue.TsNumber>(test.returnValue).number == 1.0
        }
        assertEquals(
            maxStringLength,
            assertIs<TsTestValue.TsString>(longWitness.before.parameters.single()).value.length,
        )

        val script = buildString {
            appendLine(getResourcePath(tsPath).readText())
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
            timeoutMessage = "String bound replay timed out",
            failureContext = script.take(REPLAY_FAILURE_CONTEXT_LIMIT),
        )
    }
}
