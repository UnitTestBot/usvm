package org.usvm.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import org.usvm.mcp.exec.TsAnalysisRunner
import org.usvm.mcp.scene.SceneCache
import org.usvm.mcp.tools.UsvmToolContext
import org.usvm.mcp.tools.registerCheckExceptionsTool
import org.usvm.mcp.tools.registerCheckReachabilityTool
import org.usvm.mcp.tools.registerFindCounterexampleTool
import org.usvm.mcp.tools.registerFindUnreachableCodeTool
import org.usvm.mcp.tools.registerGenerateTestsTool
import org.usvm.mcp.tools.registerGetMethodIrTool
import org.usvm.mcp.tools.registerListMethodsTool

const val SERVER_NAME = "usvm-mcp"
const val SERVER_VERSION = "0.1.0"

private const val SERVER_INSTRUCTIONS = """
USVM symbolic execution tools for TypeScript.

Typical hybrid workflow:
1. `list_methods` to see what is analyzable in a .ts file;
2. `get_method_ir` to inspect the CFG of a method (statement indices are used as targets);
3. `generate_tests` / `check_exceptions` to obtain concrete inputs per execution path;
4. `check_reachability` with a statement index from `get_method_ir`;
5. `find_unreachable_code` for dead branches;
6. `find_counterexample` to falsify a boolean property function you wrote yourself.

Analysis budgets are limited: a negative answer means "not found within budget",
not a proof, unless stated otherwise.
"""

/**
 * Builds the MCP server and registers all USVM tools on it.
 */
fun buildUsvmMcpServer(): Server {
    val server = Server(
        serverInfo = Implementation(name = SERVER_NAME, version = SERVER_VERSION),
        options = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
        ),
        instructions = SERVER_INSTRUCTIONS.trimIndent(),
    )

    val ctx = UsvmToolContext(scenes = SceneCache(), runner = TsAnalysisRunner())
    server.registerListMethodsTool(ctx)
    server.registerGetMethodIrTool(ctx)
    server.registerGenerateTestsTool(ctx)
    server.registerCheckExceptionsTool(ctx)
    server.registerCheckReachabilityTool(ctx)
    server.registerFindUnreachableCodeTool(ctx)
    server.registerFindCounterexampleTool(ctx)

    return server
}
