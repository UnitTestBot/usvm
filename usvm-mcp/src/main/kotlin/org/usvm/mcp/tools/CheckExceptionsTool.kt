package org.usvm.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.encodeToString
import org.jacodb.ets.model.EtsMethod
import org.usvm.mcp.exec.AnalysisOutcome
import org.usvm.mcp.exec.ResolvedCase
import org.usvm.mcp.json.CheckExceptionsResultDto
import org.usvm.mcp.json.McpJson
import org.usvm.mcp.json.toDiagnosticsDto
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
            must be read with analysis diagnostics: unsupported paths, engine errors and unresolved
            witnesses can prevent a concrete crashing input from being reported. $BUDGET_NOTE
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
            val outcome = ctx.runner.runTests(scene, method, args.timeoutArg())

            textResult(
                McpJson.encodeToString(outcome.toExceptionsResult(method))
            )
        }
    }
}

internal fun AnalysisOutcome<List<ResolvedCase>>.toExceptionsResult(method: EtsMethod): CheckExceptionsResultDto {
    val cases = result.map { it.toTestCaseDto() }
    val exceptional = cases.filter { it.kind == "EXCEPTION" }
    val resolutionErrors = cases.mapNotNull { it.resolutionError }

    return CheckExceptionsResultDto(
        method = method.qualifiedName(),
        exceptionalCases = exceptional,
        totalPathsExplored = result.size,
        analysis = toDiagnosticsDto(resolutionErrors),
        note = BUDGET_NOTE,
    )
}
