package org.usvm.samples

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mu.KotlinLogging
import org.junit.jupiter.api.Test
import org.usvm.generatedGoFile
import org.usvm.interpreter.GoPointer
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoExamplesReplayTest : GoMethodTestRunner() {
    @Test
    fun sliceInputAndMutation() = replay(method = "sliceOverwrite")

    @Test
    fun sliceInputAndCopy() = replay(method = "sliceCopySimple")

    @Test
    fun arrayInput() = replay(method = "arrayIndex")

    @Test
    fun pointerInputAndConversion() = replay(method = "pointerChangeType")

    @Test
    fun objectInputAndMutation() = replay(method = "(*usvm/examples.Object).Set")

    private fun replay(method: String) {
        GoExamplesReplay.replay(method = method, executions = runner(method, options))
    }
}

internal object GoExamplesReplay {
    private val logger = KotlinLogging.logger {}

    fun replay(method: String, executions: List<GoExecution>) {
        assertTrue(executions.isNotEmpty(), "Replay needs generated inputs")
        val request = buildJsonObject {
            put("method", method)
            put(
                "inputs",
                buildJsonArray { executions.forEach { add(toJson(it.arguments)) } }
            )
        }

        val replayFile = Files.createTempFile("usvm-go-examples-replay-", ".json").toFile()
        val logFile = Files.createTempFile("usvm-go-examples-replay-", ".log").toFile()
        try {
            replayFile.writeText(request.toString())
            val builder = ProcessBuilder(
                generatedGoFile("native-examples-replay.test").path,
                "-test.run=^TestReplayExamples$"
            ).redirectErrorStream(true).redirectOutput(logFile)
            builder.environment()["USVM_GO_REPLAY_FILE"] = replayFile.path
            val process = builder.start()
            try {
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Native replay timed out")
                assertEquals(expected = 0, actual = process.exitValue(), message = logFile.readText())
            } finally {
                process.destroyForcibly()
            }

            val nativeResults = Json.parseToJsonElement(replayFile.readText()).jsonArray
            assertEquals(executions.size, nativeResults.size)
            executions.zip(nativeResults).forEachIndexed { index, (execution, native) ->
                val result = native.jsonObject
                assertEquals(
                    expected = execution.result.isPanic,
                    actual = result.getValue("isPanic").jsonPrimitive.boolean,
                    message = "$method witness $index panic"
                )
                if (execution.result.isSuccess) {
                    assertEquals(
                        expected = toJson(execution.result.value),
                        actual = result.getValue("value"),
                        message = "$method witness $index result"
                    )
                }
                assertEquals(
                    expected = toJson(execution.argumentsAfter),
                    actual = result.getValue("argumentsAfter"),
                    message = "$method witness $index arguments after"
                )
            }
            logger.info { "Replayed ${executions.size} generated inputs for $method with native Go" }
        } finally {
            replayFile.delete()
            logFile.delete()
        }
    }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is GoPointer -> toJson(value.value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map(::toJson))
        is Map<*, *> -> buildJsonObject { value.forEach { (key, element) -> put(key.toString(), toJson(element)) } }
        else -> error("Unsupported native replay value: $value")
    }
}
