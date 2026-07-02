package org.usvm.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.encodeToString
import org.usvm.mcp.json.GenerateTestsResultDto
import org.usvm.mcp.json.McpJson
import org.usvm.mcp.json.toTestCaseDto
import org.usvm.mcp.json.unresolvedTestCase
import org.usvm.mcp.runTool
import org.usvm.mcp.scene.MethodLookup
import org.usvm.mcp.scene.MethodLookup.qualifiedName
import org.usvm.mcp.textResult

fun Server.registerGenerateTestsTool(ctx: UsvmToolContext) {
    addTool(
        name = "generate_tests",
        description = """
            Symbolically executes a TypeScript method and returns one concrete test case per
            explored execution path: input values ('thisInstance' and 'parameters') plus the
            expected outcome (a return value, or an exception for failing paths).
            Use the results to write unit tests with real assertions. $BUDGET_NOTE
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
            val dtos = cases.map { case ->
                case.test?.toTestCaseDto() ?: unresolvedTestCase(case.error ?: "unknown resolution error")
            }
            textResult(
                McpJson.encodeToString(
                    GenerateTestsResultDto(
                        method = method.qualifiedName(),
                        testCases = dtos,
                        note = "Each test case corresponds to a distinct symbolic execution path. $BUDGET_NOTE",
                    )
                )
            )
        }
    }
}
