package org.usvm.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.encodeToString
import org.usvm.mcp.McpToolException
import org.usvm.mcp.json.McpJson
import org.usvm.mcp.json.StmtRefDto
import org.usvm.mcp.json.UnreachableCodeResultDto
import org.usvm.mcp.json.UnreachableFindingDto
import org.usvm.mcp.runTool
import org.usvm.mcp.scene.MethodLookup
import org.usvm.mcp.scene.MethodLookup.isAnalyzable
import org.usvm.mcp.scene.MethodLookup.qualifiedName
import org.usvm.mcp.textResult

fun Server.registerFindUnreachableCodeTool(ctx: UsvmToolContext) {
    addTool(
        name = "find_unreachable_code",
        description = """
            Detects likely dead code: branches of 'if' statements that were never taken during
            symbolic exploration of the given method (or of all methods in the file/class when
            'method' is omitted). Reports the 'if' statement and its uncovered successor
            statements (CFG indices, usable with get_method_ir). Findings mean 'uncovered
            within budget' — verify suspicious ones with check_reachability using a larger budget.
        """.trimIndent(),
        inputSchema = toolSchema(required = listOf("file")) {
            fileProp()
            classProp()
            stringProp("method", "Optional method name; when omitted, all analyzable methods are checked.")
            timeoutProp()
        },
    ) { request ->
        runTool {
            val args = request.arguments
            val scene = ctx.scenes.getScene(args.requireStringArg("file"))
            val methodName = args.stringArg("method")
            val methods = if (methodName != null) {
                listOf(MethodLookup.findMethod(scene, args.stringArg("class"), methodName))
            } else {
                MethodLookup.findClasses(scene, args.stringArg("class"))
                    .flatMap { it.methods }
                    .filter { it.isAnalyzable() }
            }
            if (methods.isEmpty()) {
                throw McpToolException("No analyzable methods found. Use list_methods to inspect the file.")
            }

            val result = ctx.runner.runUnreachableDetection(scene, methods, args.timeoutArg())
            val findings = result.flatMap { (method, uncovered) ->
                val stmts = method.cfg.stmts
                uncovered.map { finding ->
                    UnreachableFindingDto(
                        method = method.qualifiedName(),
                        ifStmtIndex = stmts.indexOf(finding.ifStmt),
                        ifStmt = finding.ifStmt.toString(),
                        uncoveredSuccessors = finding.successors.map { successor ->
                            StmtRefDto(index = stmts.indexOf(successor), text = successor.toString())
                        },
                    )
                }
            }
            textResult(
                McpJson.encodeToString(
                    UnreachableCodeResultDto(
                        findings = findings,
                        analyzedMethods = methods.map { it.qualifiedName() },
                        note = "Findings are branches uncovered within the budget, not proofs of unreachability. " +
                            "Confirm each one with check_reachability on the successor statement index.",
                    )
                )
            )
        }
    }
}
