package org.usvm.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent

/**
 * An expected, user-facing tool failure. Its message is written as an
 * actionable instruction for the calling LLM/user (what went wrong and
 * how to fix the call or the environment).
 */
class McpToolException(message: String) : RuntimeException(message)

fun textResult(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)))

fun errorResult(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), isError = true)

/**
 * Wraps a tool handler body: expected errors become `isError` results with
 * actionable messages, unexpected ones become `isError` results with a short
 * diagnostic. The server never crashes because of a single tool call.
 */
suspend fun runTool(block: suspend () -> CallToolResult): CallToolResult =
    try {
        block()
    } catch (e: McpToolException) {
        errorResult(e.message ?: "Tool failed")
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        val frames = e.stackTrace.take(5).joinToString("\n") { "  at $it" }
        errorResult("Internal error: ${e::class.simpleName}: ${e.message}\n$frames")
    }
