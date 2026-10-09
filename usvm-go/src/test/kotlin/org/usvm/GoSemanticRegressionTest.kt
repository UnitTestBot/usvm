package org.usvm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.usvm.interpreter.SuccessfulExecutionResult
import org.usvm.interpreter.UnsuccessfulExecutionResult
import org.usvm.model.Converter
import org.usvm.model.Parser
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class GoSemanticRegressionTest {
    @TestFactory
    fun replayGeneratedInputsWithNativeGo(): Collection<DynamicTest> = listOf(
        "symbolicBranch" to 3,
        "symbolicSliceAlias" to 2,
    ).map { (name, count) -> DynamicTest.dynamicTest(name) { replayInputs(name, expectedExecutions = count) } }

    private fun replayInputs(methodName: String, expectedExecutions: Int) {
        val pkg = Converter.unpack(Parser().deserialize(generatedGoFile("regressions/usvm_regressions.json").path))
        val options = UMachineOptions(timeout = 5.seconds, solverTimeout = 2.seconds, typeOperationsTimeout = 2.seconds)
        val customOptions = GoMachineOptions(failOnNotFullCoverage = true, uncoveredMethods = emptyList())
        val executions = GoMachine(GoProgram(listOf(pkg)), options, customOptions).use { machine ->
            machine.analyzeAndResolve(pkg, methodName).map { assertIs<SuccessfulExecutionResult>(it) }
        }
        assertEquals(expectedExecutions, executions.size, message = "One witness per branch")
        val inputs = executions.map { (it.inputModel.arguments.single() as Number).toLong() }
        if (methodName == "symbolicBranch") {
            assertTrue(inputs.any { it < 0 } && inputs.any { it == 0L } && inputs.any { it > 0 })
        }

        val replayFile = Files.createTempFile("usvm-go-replay-", ".json").toFile()
        val logFile = Files.createTempFile("usvm-go-replay-", ".log").toFile()
        try {
            replayFile.writeText(inputs.joinToString(prefix = "[", postfix = "]"))
            val builder = ProcessBuilder(
                generatedGoFile("native-replay.test").path,
                "-test.run=^TestReplaySymbolicInputs$"
            )
                .redirectErrorStream(true).redirectOutput(logFile)
            builder.environment()["USVM_GO_REPLAY_FILE"] = replayFile.path
            builder.environment()["USVM_GO_REPLAY_METHOD"] = methodName
            val process = builder.start()
            try {
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Native replay timed out")
                assertEquals(expected = 0, actual = process.exitValue(), message = logFile.readText())
            } finally {
                process.destroyForcibly()
            }
            val nativeOutputs = Json.parseToJsonElement(replayFile.readText()).jsonArray.map { it.jsonPrimitive.int }
            val symbolicOutputs = executions.map { (it.outputModel.returnExpr as Number).toInt() }
            assertEquals(nativeOutputs, symbolicOutputs, "Replay of generated inputs")
        } finally {
            replayFile.delete()
            logFile.delete()
        }
    }

    @TestFactory
    fun compareWithNativeGo(): Collection<DynamicTest> {
        val nativeResults = Json.parseToJsonElement(
            File(generatedGoFile("native-oracle.json").path).readText()
        ).jsonObject
        val pkg = Converter.unpack(
            Parser().deserialize(filename = generatedGoFile("regressions/usvm_regressions.json").path)
        )
        val expectedNames = pkg.methods
            .filter { it.parameters.isEmpty() && it.metName != "init" }
            .map { it.metName }.toSet()
        assertEquals(expectedNames, nativeResults.keys, "Every zero-argument fixture must have a native oracle")
        val program = GoProgram(listOf(pkg))
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.FORK_DEPTH),
            coverageZone = CoverageZone.TRANSITIVE,
            exceptionsPropagation = true,
            timeout = 5.seconds,
            solverTimeout = 2.seconds,
            typeOperationsTimeout = 2.seconds,
        )

        return nativeResults.map { (name, nativeResult) ->
            DynamicTest.dynamicTest(name) {
                val expected = nativeResult.jsonPrimitive.content
                val customOptions = GoMachineOptions(
                    failOnNotFullCoverage = expected != "panic",
                    uncoveredMethods = emptyList(),
                )

                val actual = GoMachine(program, options, customOptions).use { machine ->
                    val executions = machine.analyzeAndResolve(pkg, name)
                    assertEquals(expected = 1, actual = executions.size, message = "Expected one complete execution")
                    val execution = executions.single()
                    if (expected == "panic") {
                        assertIs<UnsuccessfulExecutionResult>(execution)
                        "panic"
                    } else {
                        assertIs<SuccessfulExecutionResult>(execution).outputModel.returnExpr.toString()
                    }
                }

                assertEquals(expected, actual, message = "Native Go result for $name")
            }
        }
    }
}
