package org.usvm.samples

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.usvm.generatedGoFile
import org.usvm.test.util.checkers.eq
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

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
        assertTrue(executions.all { it.result.isSuccess }, message = "Native replay expects successful executions")
        assertEquals(expectedExecutions, executions.size, message = "One witness per branch")
        val inputs = executions.map { (it.arguments.single() as Number).toLong() }
        if (methodName == "symbolicBranch") {
            assertTrue(inputs.any { it < 0 } && inputs.any { it == 0L } && inputs.any { it > 0 })
        }

        val nativeOutputs = replayWithNativeGo(
            executable = generatedGoFile("native-replay.test"),
            testName = "TestReplaySymbolicInputs",
            request = JsonArray(inputs.map(::JsonPrimitive)),
            timeout = 5.seconds,
            environment = mapOf("USVM_GO_REPLAY_METHOD" to methodName),
        ).jsonArray.map { it.jsonPrimitive.long }
        val symbolicOutputs = executions.map { (it.result.value as Number).toLong() }

        assertEquals(nativeOutputs, symbolicOutputs, message = "Replay of generated inputs")
    }
}
