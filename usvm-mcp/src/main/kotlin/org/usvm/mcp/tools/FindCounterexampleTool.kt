package org.usvm.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.encodeToString
import org.usvm.api.TsTestValue
import org.usvm.mcp.json.CounterexampleResultDto
import org.usvm.mcp.json.McpJson
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

            The property and everything it calls must live in one .ts file. Verdict
            NO_COUNTEREXAMPLE_WITHIN_BUDGET is NOT a proof: exploration may have missed paths.
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
            val cases = ctx.runner.runTests(scene, method, args.timeoutArg())

            val tests = cases.mapNotNull { it.test }
            val counterexamples = tests
                .filter { (it.returnValue as? TsTestValue.TsBoolean)?.value == false }
                .map { it.toTestCaseDto() }
            val crashes = tests
                .filter { it.returnValue is TsTestValue.TsException }
                .map { it.toTestCaseDto() }

            textResult(
                McpJson.encodeToString(
                    CounterexampleResultDto(
                        method = method.qualifiedName(),
                        verdict = if (counterexamples.isNotEmpty()) {
                            "COUNTEREXAMPLE_FOUND"
                        } else {
                            "NO_COUNTEREXAMPLE_WITHIN_BUDGET"
                        },
                        counterexamples = counterexamples,
                        crashes = crashes,
                        totalPathsExplored = cases.size,
                        note = "A counterexample gives concrete inputs falsifying the property. " +
                            "'crashes' are inputs on which the property itself threw. " +
                            "No counterexample within budget is evidence, not a proof.",
                    )
                )
            )
        }
    }
}
