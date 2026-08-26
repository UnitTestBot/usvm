package org.usvm.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.usvm.mcp.McpToolException
import org.usvm.mcp.exec.TsAnalysisRunner
import org.usvm.mcp.scene.SceneCache
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Shared dependencies injected into every tool. */
class UsvmToolContext(
    val scenes: SceneCache,
    val runner: TsAnalysisRunner,
)

// --- Input schema helpers -------------------------------------------------

fun toolSchema(required: List<String>, block: JsonObjectBuilder.() -> Unit): ToolSchema =
    ToolSchema(properties = buildJsonObject(block), required = required)

fun JsonObjectBuilder.stringProp(name: String, description: String) {
    putJsonObject(name) {
        put("type", "string")
        put("description", description)
    }
}

fun JsonObjectBuilder.intProp(name: String, description: String) {
    putJsonObject(name) {
        put("type", "integer")
        put("description", description)
    }
}

const val DEFAULT_TIMEOUT_MS = 30_000
const val MIN_TIMEOUT_MS = 1_000
const val MAX_TIMEOUT_MS = 300_000

const val FILE_DESC =
    "Path to the TypeScript (.ts) source file to analyze " +
        "(absolute, or relative to the MCP server working directory)."

const val CLASS_DESC =
    "Optional class name. Omit it for top-level functions or when the method name is unique in the file."

const val METHOD_DESC = "Name of the method/function to analyze."

const val TIMEOUT_DESC =
    "Analysis budget in milliseconds (default $DEFAULT_TIMEOUT_MS, min $MIN_TIMEOUT_MS, max $MAX_TIMEOUT_MS). " +
        "Larger budgets explore more paths."

const val BUDGET_NOTE =
    "Exploration is bounded by a time budget: absence of a result means 'not found within budget', " +
        "not a proof of absence."

fun JsonObjectBuilder.fileProp() = stringProp("file", FILE_DESC)
fun JsonObjectBuilder.classProp() = stringProp("class", CLASS_DESC)
fun JsonObjectBuilder.methodProp() = stringProp("method", METHOD_DESC)
fun JsonObjectBuilder.timeoutProp() = intProp("timeoutMs", TIMEOUT_DESC)

// --- Argument extraction --------------------------------------------------

fun JsonObject?.stringArg(name: String): String? =
    (this?.get(name) as? JsonPrimitive)?.contentOrNull

fun JsonObject?.requireStringArg(name: String): String =
    stringArg(name)
        ?: throw McpToolException("Missing required string argument '$name'.")

fun JsonObject?.intArg(name: String): Int? =
    (this?.get(name) as? JsonPrimitive)?.intOrNull

fun JsonObject?.timeoutArg(): Duration {
    val ms = intArg("timeoutMs") ?: DEFAULT_TIMEOUT_MS
    return ms.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS).milliseconds
}
