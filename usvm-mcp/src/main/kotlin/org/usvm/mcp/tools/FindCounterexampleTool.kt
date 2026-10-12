package org.usvm.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import org.jacodb.ets.model.EtsMethod
import org.usvm.mcp.exec.AnalysisOutcome
import org.usvm.mcp.exec.ResolvedCase
import org.usvm.mcp.json.CounterexampleResultDto
import org.usvm.mcp.json.McpJson
import org.usvm.mcp.json.incomplete
import org.usvm.mcp.json.toDiagnosticsDto
import org.usvm.mcp.json.toTestCaseDto
import org.usvm.mcp.runTool
import org.usvm.mcp.scene.MethodLookup
import org.usvm.mcp.scene.MethodLookup.qualifiedName
import org.usvm.mcp.textResult

fun Server.registerFindCounterexampleTool(ctx: UsvmToolContext) {
    addTool(
        name = "find_counterexample",
        description = """
            Tries to FALSIFY a hypothesis about the code. Write a boolean property function in
            a .ts file yourself, then point this tool at it: symbolic execution searches for
            inputs on which the property returns false ('counterexamples') or crashes ('crashes').

            Hybrid workflows:
            - Hypothesis checking: "abs(x) is always >= 0" -> write
              `function prop(x: number): boolean { return abs(x) >= 0; }` (with 'abs' defined
              in the same file) and analyze 'prop'.
            - Equivalence of two implementations (e.g. original vs your refactoring): write
              `function equiv(x: number): boolean { return f(x) === g(x); }` and analyze 'equiv'.

            The property and everything it calls must live in one .ts file. Stopped search,
            unsupported paths, engine errors or unresolved witnesses produce INCOMPLETE_ANALYSIS when no
            concrete failure was found. NO_COUNTEREXAMPLE_WITHIN_BUDGET is NOT a proof.
        """.trimIndent(),
        inputSchema = toolSchema(required = listOf("file", "method")) {
            fileProp()
            classProp()
            stringProp("method", "Name of the boolean property function to falsify.")
            timeoutProp()
        },
    ) { request ->
        runTool {
            val args = request.arguments
            val scene = ctx.scenes.getScene(args.requireStringArg("file"))
            val method = MethodLookup.findMethod(scene, args.stringArg("class"), args.requireStringArg("method"))
            val outcome = ctx.runner.runTests(scene, method, args.timeoutArg())

            textResult(
                McpJson.encodeToString(outcome.toCounterexampleResult(method))
            )
        }
    }
}

internal fun AnalysisOutcome<List<ResolvedCase>>.toCounterexampleResult(method: EtsMethod): CounterexampleResultDto {
    val cases = result.map { it.toTestCaseDto() }
    val counterexamples = cases.filter { it.kind == "SUCCESS" && it.returnValue == JsonPrimitive(false) }
    val crashes = cases.filter { it.kind == "EXCEPTION" }
    val resolutionErrors = cases.mapNotNull { it.resolutionError }
    val diagnostics = toDiagnosticsDto(resolutionErrors)
    val hasCounterexamples = counterexamples.isNotEmpty()
    val hasCrashes = crashes.isNotEmpty()
    val incompleteAnalysis = diagnostics.incomplete
    val verdict = when {
        hasCounterexamples -> "COUNTEREXAMPLE_FOUND"
        hasCrashes -> "PROPERTY_CRASHED"
        incompleteAnalysis -> "INCOMPLETE_ANALYSIS"
        else -> "NO_COUNTEREXAMPLE_WITHIN_BUDGET"
    }

    return CounterexampleResultDto(
        method = method.qualifiedName(),
        verdict = verdict,
        counterexamples = counterexamples,
        crashes = crashes,
        totalPathsExplored = result.size,
        analysis = diagnostics,
        note = "A counterexample gives concrete inputs falsifying the property. " +
            "'crashes' are inputs on which the property itself threw. " +
            "Inspect analysis diagnostics for stopped search, unsupported paths, engine errors or witness errors. " +
            "No counterexample within budget is evidence, not a proof.",
    )
}
