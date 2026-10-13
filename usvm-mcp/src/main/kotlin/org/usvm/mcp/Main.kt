package org.usvm.mcp

import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream

/**
 * Entry point of the USVM MCP server (stdio transport).
 *
 * Stdout is the JSON-RPC channel, so before anything else we redirect
 * [System.out] to stderr: any stray `println` from USVM/ksmt/ArkAnalyzer
 * integration must not corrupt the protocol stream.
 */
fun main() {
    val realStdout = System.out
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.err), true))

    val server = buildUsvmMcpServer()
    val transport = StdioServerTransport(
        System.`in`.asSource().buffered(),
        realStdout.asSink().buffered(),
    )

    runBlocking {
        val session = server.createSession(transport)
        val done = CompletableDeferred<Unit>()
        session.onClose { done.complete(Unit) }
        done.await()
    }
}
