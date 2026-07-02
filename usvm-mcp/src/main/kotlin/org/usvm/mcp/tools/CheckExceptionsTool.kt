package org.usvm.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.encodeToString
import org.usvm.api.TsTestValue
import org.usvm.mcp.json.CheckExceptionsResultDto
import org.usvm.mcp.json.McpJson
import org.usvm.mcp.json.toTestCaseDto
import org.usvm.mcp.runTool
import org.usvm.mcp.scene.MethodLookup
import org.usvm.mcp.scene.MethodLookup.qualifiedName
import org.usvm.mcp.textResult

fun Server.registerCheckExceptionsTool(ctx: UsvmToolContext) {
    addTool(
        name = "check_exceptions",
        description = """
            Searches for concrete inputs that make a TypeScript method throw an exception
            (crash). Runs the same path exploration as generate_tests but reports only the
            failing paths, together with the input values that trigger them. An empty result
            means no crashing path was found within the budget. $BUDGET_NOTE
        """.trimIndent(),
        inputSchema = toolSchema(required = listOf("file", "method")) {
            fileProp()
            classProp()
            methodProp()
            timeoutProp()
        },
    ) { request ->
        runTool {
            val args = request.arguments
            val scene = ctx.scenes.getScene(args.requireStringArg("file"))
            val method = MethodLookup.findMethod(scene, args.stringArg("class"), args.requireStringArg("method"))
            val cases = ctx.runner.runTests(scene, method, args.timeoutArg())
            val exceptional = cases
                .mapNotNull { it.test }
                .filter { it.returnValue is TsTestValue.TsException }
                .map { it.toTestCaseDto() }
            textResult(
                McpJson.encodeToString(
                    CheckExceptionsResultDto(
                        method = method.qualifiedName(),
                        exceptionalCases = exceptional,
                        totalPathsExplored = cases.size,
                        note = BUDGET_NOTE,
                    )
                )
            )
        }
    }
}
