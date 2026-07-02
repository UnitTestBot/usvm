package org.usvm.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.encodeToString
import org.usvm.mcp.McpToolException
import org.usvm.mcp.json.McpJson
import org.usvm.mcp.json.ReachabilityResultDto
import org.usvm.mcp.json.toTestCaseDto
import org.usvm.mcp.json.unresolvedTestCase
import org.usvm.mcp.runTool
import org.usvm.mcp.scene.MethodLookup
import org.usvm.mcp.scene.MethodLookup.qualifiedName
import org.usvm.mcp.textResult

fun Server.registerCheckReachabilityTool(ctx: UsvmToolContext) {
    addTool(
        name = "check_reachability",
        description = """
            Checks whether a specific IR statement of a TypeScript method is reachable and,
            if so, returns a witness: concrete input values that drive execution to it.
            'stmtIndex' is the statement index from the get_method_ir tool output — call it
            first to pick the target (e.g. a particular 'return' or a branch of an 'if').
            Status NOT_REACHED_WITHIN_BUDGET means the directed search failed within the time
            budget; it is strong evidence but NOT a proof of unreachability.
        """.trimIndent(),
        inputSchema = toolSchema(required = listOf("file", "method", "stmtIndex")) {
            fileProp()
            classProp()
            methodProp()
            intProp("stmtIndex", "Index of the target statement in the method CFG (see get_method_ir).")
            timeoutProp()
        },
    ) { request ->
        runTool {
            val args = request.arguments
            val scene = ctx.scenes.getScene(args.requireStringArg("file"))
            val method = MethodLookup.findMethod(scene, args.stringArg("class"), args.requireStringArg("method"))
            val stmts = method.cfg.stmts
            val stmtIndex = args.intArg("stmtIndex")
                ?: throw McpToolException("Missing required integer argument 'stmtIndex'.")
            if (stmtIndex !in stmts.indices) {
                throw McpToolException(
                    "stmtIndex $stmtIndex is out of range: method '${method.name}' has ${stmts.size} " +
                        "statements (valid indices 0..${stmts.size - 1}). Use get_method_ir to list them."
                )
            }
            val target = stmts[stmtIndex]
            val outcome = ctx.runner.runReachability(scene, method, target, args.timeoutArg())
            val witness = outcome.witness?.let { case ->
                case.test?.toTestCaseDto() ?: unresolvedTestCase(case.error ?: "unknown resolution error")
            }
            textResult(
                McpJson.encodeToString(
                    ReachabilityResultDto(
                        method = method.qualifiedName(),
                        stmtIndex = stmtIndex,
                        statement = target.toString(),
                        status = if (outcome.reached) "REACHABLE" else "NOT_REACHED_WITHIN_BUDGET",
                        witness = witness,
                        note = if (outcome.reached) {
                            "The witness inputs drive execution to the target statement " +
                                "according to the symbolic model. The model over-approximates " +
                                "JavaScript semantics in places; validate the witness by actually " +
                                "running the method with these inputs (e.g. via node/ts-node)."
                        } else {
                            "The target was not reached within the budget. This suggests, " +
                                "but does not prove, that it is unreachable. Try a larger timeoutMs."
                        },
                    )
                )
            )
        }
    }
}
