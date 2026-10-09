package org.usvm.samples

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.usvm.generatedGoFile
import org.usvm.test.util.checkers.eq
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

open class GoNativeTestRunner : GoMethodTestRunner(fixture = "regressions") {
    protected fun checkNative(method: String) {
        val expected = Json.parseToJsonElement(generatedGoFile("native-oracle.json").readText())
            .jsonObject.getValue(method).jsonPrimitive.content
        if (expected == "panic") machineOptions = machineOptions.copy(failOnNotFullCoverage = false)

        checkMatches(
            method = method,
            analysisResultsNumberMatcher = eq(count = 1),
            { result: GoResult ->
                if (expected == "panic") result.isPanic else result.isSuccess && result.value.toString() == expected
            },
        )
    }

    protected fun replayInputs(methodName: String, expectedExecutions: Int) {
        val executions = runner(methodName, options)
        assertTrue(executions.all { it.result.isSuccess }, "Native replay expects successful executions")
        assertEquals(expectedExecutions, executions.size, message = "One witness per branch")
        val inputs = executions.map { (it.arguments.single() as Number).toLong() }
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
            val nativeOutputs = Json.parseToJsonElement(replayFile.readText()).jsonArray.map { it.jsonPrimitive.long }
            val symbolicOutputs = executions.map { (it.result.value as Number).toLong() }
            assertEquals(nativeOutputs, symbolicOutputs, "Replay of generated inputs")
        } finally {
            replayFile.delete()
            logFile.delete()
        }
    }
}
