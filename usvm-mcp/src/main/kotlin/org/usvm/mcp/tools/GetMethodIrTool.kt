package org.usvm.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.encodeToString
import org.usvm.mcp.json.McpJson
import org.usvm.mcp.json.MethodIrResultDto
import org.usvm.mcp.json.StmtDto
import org.usvm.mcp.runTool
import org.usvm.mcp.scene.MethodLookup
import org.usvm.mcp.scene.MethodLookup.qualifiedName
import org.usvm.mcp.textResult

fun Server.registerGetMethodIrTool(ctx: UsvmToolContext) {
    addTool(
        name = "get_method_ir",
        description = """
            Shows the control-flow graph (CFG) of a method as a list of IR statements with
            their indices and successor indices. Source line numbers are NOT available in the IR,
            so statement 'index' is the only way to address a statement: pass it as 'stmtIndex'
            to check_reachability. Statements are rendered in an assembly-like form
            (conditions of 'if' statements, return statements, assignments etc.), which lets you
            match them back to the source code.
        """.trimIndent(),
        inputSchema = toolSchema(required = listOf("file", "method")) {
            fileProp()
            classProp()
            methodProp()
        },
    ) { request ->
        runTool {
            val args = request.arguments
            val scene = ctx.scenes.getScene(args.requireStringArg("file"))
            val method = MethodLookup.findMethod(scene, args.stringArg("class"), args.requireStringArg("method"))
            val stmts = method.cfg.stmts
            val statements = stmts.mapIndexed { index, stmt ->
                StmtDto(
                    index = index,
                    kind = stmt::class.simpleName ?: "EtsStmt",
                    text = stmt.toString(),
                    successors = method.cfg.successors(stmt).map { stmts.indexOf(it) }.sorted(),
                )
            }
            textResult(
                McpJson.encodeToString(
                    MethodIrResultDto(method = method.qualifiedName(), statements = statements)
                )
            )
        }
    }
}
