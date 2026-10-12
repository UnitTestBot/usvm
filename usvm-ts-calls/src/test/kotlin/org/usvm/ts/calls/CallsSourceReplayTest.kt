package org.usvm.ts.calls

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CallsSourceReplayTest {
    @Test
    fun `replays a zero-input export without the FastCheck placeholder`() {
        val fixture = fixture()
        val zeroArguments = fixture.target(functionName = "checksArgumentCount", statement = "return 0;")
        val extraArgument = fixture.target(functionName = "checksArgumentCount", statement = "return 1;")

        val reached = fixture.replay(exportName = "checksArgumentCount", inputs = emptyList(), target = zeroArguments)
        val notReached = fixture.replay(
            exportName = "checksArgumentCount",
            inputs = emptyList(),
            target = extraArgument,
        )

        assertEquals(CallsReplayStatus.CONFIRMED, reached.status, reached.toString())
        assertEquals(CallsReplayStatus.REJECTED, notReached.status, notReached.toString())
    }

    @Test
    fun `completed return is rejected when finally overrides it or the branch is untaken`() {
        val fixture = fixture()
        val overridden = fixture.target(
            functionName = "finallyOverridesCompletedReturn",
            statement = "return 11;",
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "11",
        )
        val untaken = fixture.target(
            functionName = "bareConditionalCompletedReturn",
            statement = "return;",
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
        )

        val overriddenReplay = fixture.replay(
            exportName = "finallyOverridesCompletedReturn",
            inputs = emptyList(),
            target = overridden,
        )
        val untakenReplay = fixture.replay(
            exportName = "bareConditionalCompletedReturn",
            inputs = listOf(number(-1.0)),
            target = untaken,
        )

        assertEquals(CallsReplayStatus.REJECTED, overriddenReplay.status, overriddenReplay.toString())
        assertEquals(CallsReplayStatus.REJECTED, untakenReplay.status, untakenReplay.toString())
        assertEquals(false, overriddenReplay.invocation?.targetHit)
        assertEquals(false, untakenReplay.invocation?.targetHit)
    }

    @Test
    fun `confirms only the exact source statement reached by original TypeScript`() {
        val fixture = fixture()
        val taken = fixture.target(functionName = "inlineChoose", statement = "return 1;")
        val untaken = fixture.target(functionName = "inlineChoose", statement = "return 0;")

        val takenReplay = fixture.replay(exportName = "inlineChoose", inputs = listOf(number(1.0)), target = taken)
        val untakenReplay = fixture.replay(exportName = "inlineChoose", inputs = listOf(number(1.0)), target = untaken)

        assertEquals(CallsReplayStatus.CONFIRMED, takenReplay.status)
        assertEquals(true, takenReplay.invocation?.targetHit)
        assertEquals(CallsReplayStatus.REJECTED, untakenReplay.status)
        assertEquals(false, untakenReplay.invocation?.targetHit)
    }

    @Test
    fun `retains target confirmation when the original invocation throws`() {
        val fixture = fixture()
        val target = fixture.target(functionName = "throwsAtTarget", statement = "throw new Error('expected');")

        val replay = fixture.replay(exportName = "throwsAtTarget", inputs = emptyList(), target = target)

        assertEquals(CallsReplayStatus.CONFIRMED, replay.status, replay.toString())
        assertEquals("threw", replay.invocation?.invocation)
        assertEquals(true, replay.invocation?.targetHit)
        assertEquals("Error", replay.invocation?.errorName)
        assertEquals("expected", replay.invocation?.errorMessage)
    }

    @Test
    fun `counts target hits from the selected invocation rather than module import`() {
        val fixture = fixture()
        val target = fixture.target(functionName = "importOnlyTarget", statement = "return 7;")

        val importOnly = fixture.replay(exportName = "skipsImportOnlyTarget", inputs = emptyList(), target = target)
        val invoked = fixture.replay(exportName = "importOnlyTarget", inputs = emptyList(), target = target)

        assertEquals(CallsReplayStatus.REJECTED, importOnly.status, importOnly.toString())
        assertEquals(false, importOnly.invocation?.targetHit)
        assertEquals(CallsReplayStatus.CONFIRMED, invoked.status)
        assertEquals(true, invoked.invocation?.targetHit)
    }

    @Test
    fun `completed expression return is confirmed after evaluating its exact expression`() {
        val fixture = fixture()
        val target = fixture.target(
            functionName = "completesReturnExpression",
            statement = "return /* before expression */ value.trim() /* after expression */",
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "value.trim()",
        )

        val replay = fixture.replay(
            exportName = "completesReturnExpression",
            inputs = listOf(JsConcreteValue.String(" value ")),
            target = target,
        )

        assertEquals(CallsReplayStatus.CONFIRMED, replay.status, replay.toString())
        assertEquals(true, replay.invocation?.targetHit)
    }

    @Test
    fun `throwing expression never completes the selected return`() {
        val fixture = fixture()
        val target = fixture.target(
            functionName = "expressionThrowsBeforeReturnCompletes",
            statement = "return throwFromExpression();",
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "throwFromExpression()",
        )

        val replay = fixture.replay(
            exportName = "expressionThrowsBeforeReturnCompletes",
            inputs = emptyList(),
            target = target,
        )

        assertEquals(CallsReplayStatus.REJECTED, replay.status, replay.toString())
        assertEquals(false, replay.invocation?.targetHit)
    }

    @Test
    fun `throwing finally cancels the selected return completion`() {
        val fixture = fixture()
        val target = fixture.target(
            functionName = "finallyThrowsAfterReturnExpression",
            statement = "return 'value'.trim();",
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "'value'.trim()",
        )

        val replay = fixture.replay(
            exportName = "finallyThrowsAfterReturnExpression",
            inputs = emptyList(),
            target = target,
        )

        assertEquals(CallsReplayStatus.REJECTED, replay.status, replay.toString())
        assertEquals(false, replay.invocation?.targetHit)
    }

    @Test
    fun `completed bare return is confirmed without evaluating an expression`() {
        val fixture = fixture()
        val target = fixture.target(
            functionName = "completesBareReturn",
            statement = "return /* no expression */",
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
        )

        val replay = fixture.replay(exportName = "completesBareReturn", inputs = emptyList(), target = target)

        assertEquals(CallsReplayStatus.CONFIRMED, replay.status, replay.toString())
        assertEquals(true, replay.invocation?.targetHit)
    }

    @Test
    fun `entry instrumentation preserves unbraced conditional and loop bodies`() {
        val fixture = fixture()
        val cases = listOf(
            "unbracedConditional" to "return 1;",
            "unbracedLoop" to "value += 0;",
            "labeledLoop" to "continue outer;",
        )

        for ((functionName, statement) in cases) {
            val target = fixture.target(functionName = functionName, statement = statement)
            val untaken = fixture.replay(exportName = functionName, inputs = listOf(number(0.0)), target = target)
            val taken = fixture.replay(exportName = functionName, inputs = listOf(number(1.0)), target = target)

            assertEquals(CallsReplayStatus.REJECTED, untaken.status, "$functionName: $untaken")
            assertEquals(false, untaken.invocation?.targetHit)
            assertEquals(CallsReplayStatus.CONFIRMED, taken.status, "$functionName: $taken")
            assertEquals(true, taken.invocation?.targetHit)
        }
    }

    @Test
    fun `completed conditional return preserves its taken and untaken branches`() {
        val fixture = fixture()
        val target = fixture.target(
            functionName = "bareConditionalCompletedReturn",
            statement = "return;",
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
        )

        val taken = fixture.replay(
            exportName = "bareConditionalCompletedReturn",
            inputs = listOf(number(1.0)),
            target = target,
        )

        assertEquals(CallsReplayStatus.CONFIRMED, taken.status, taken.toString())
        assertEquals(true, taken.invocation?.targetHit)
    }

    @Test
    fun `completed return evaluates side effects once and survives normally completing finally`() {
        val fixture = fixture()
        val target = fixture.target(
            functionName = "returnExpressionRunsOnce",
            statement = "return once();",
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "once()",
        )

        val replay = fixture.replay(exportName = "returnExpressionRunsOnce", inputs = emptyList(), target = target)

        assertEquals(CallsReplayStatus.CONFIRMED, replay.status, replay.toString())
        assertEquals("returned", replay.invocation?.invocation)
    }

    @Test
    fun `finally break and escaping throw cancel pending return before implicit completion`() {
        val fixture = fixture()

        for (functionName in listOf("finallyBreakCancelsReturn", "finallyThrowCaughtOutside")) {
            val target = fixture.target(
                functionName = functionName,
                statement = "return 1;",
                mode = CallsSourceTargetMode.COMPLETED_RETURN,
                returnExpression = "1",
            )
            val replay = fixture.replay(exportName = functionName, inputs = emptyList(), target = target)

            assertEquals(CallsReplayStatus.REJECTED, replay.status, "$functionName: $replay")
            assertEquals("returned", replay.invocation?.invocation)
            assertEquals(false, replay.invocation?.targetHit)
        }
    }

    @Test
    fun `completed replay observes only the outer selected invocation during recursion`() {
        val fixture = fixture()
        val recursive = fixture.target(
            functionName = "recursiveOuterFallthrough",
            statement = "return 1;",
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "1",
        )
        val outerReturn = fixture.target(
            functionName = "recursiveOuterReturn",
            statement = "return 1;",
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "1",
        )

        val innerOnly = fixture.replay(
            exportName = "recursiveOuterFallthrough",
            inputs = listOf(number(1.0)),
            target = recursive,
        )
        val direct = fixture.replay(
            exportName = "recursiveOuterFallthrough",
            inputs = listOf(number(0.0)),
            target = recursive,
        )
        val completedOuter = fixture.replay(
            exportName = "recursiveOuterReturn",
            inputs = listOf(number(1.0)),
            target = outerReturn,
        )

        assertEquals(CallsReplayStatus.REJECTED, innerOnly.status, innerOnly.toString())
        assertEquals(false, innerOnly.invocation?.targetHit)
        assertEquals(CallsReplayStatus.CONFIRMED, direct.status, direct.toString())
        assertEquals(CallsReplayStatus.CONFIRMED, completedOuter.status, completedOuter.toString())
    }

    @Test
    fun `rejects a source target symlink that escapes its declared root`() {
        val workspace = Files.createTempDirectory("calls-replay-root-boundary-")
        try {
            val sourceRoot = Files.createDirectories(workspace.resolve("root"))
            val source = "export function target(): number { return 7; }"
            val outside = Files.writeString(workspace.resolve("outside.ts"), source)
            val sourcePath = Files.createSymbolicLink(sourceRoot.resolve("Target.ts"), outside)
            val fixture = Fixture(sourceRoot = sourceRoot, sourcePath = sourcePath, source = source)
            val target = fixture.target(functionName = "target", statement = "return 7;")

            val replay = fixture.replay(exportName = "target", inputs = emptyList(), target = target)

            assertEquals(CallsReplayStatus.TOOL_ERROR, replay.status, replay.toString())
            assertEquals("Target source path resolved to 0 files", replay.message)
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }

    private fun fixture(): Fixture {
        val sourcePath = resourcePath("/calls/SourceTargetReplayFixture.ts")

        return Fixture(
            sourceRoot = assertNotNull(sourcePath.parent?.parent),
            sourcePath = sourcePath,
            source = Files.readString(sourcePath),
        )
    }

    private fun resourcePath(name: String): Path {
        val resource = assertNotNull(javaClass.getResource(name), "Missing test resource $name")

        return Paths.get(resource.toURI())
    }

    private fun number(value: Double): JsConcreteValue = JsConcreteValue.number(value)

    private data class Fixture(
        val sourceRoot: Path,
        val sourcePath: Path,
        val source: String,
    ) {
        fun target(
            functionName: String,
            statement: String,
            mode: CallsSourceTargetMode = CallsSourceTargetMode.ENTRY,
            returnExpression: String? = null,
        ): CallsSourceTarget {
            val functionStart = source.indexOf("function $functionName")
            val startOffset = source.indexOf(statement, startIndex = functionStart)
            check(functionStart >= 0 && startOffset >= 0) { "Missing $statement in $functionName" }
            val endOffset = startOffset + statement.length
            val returnExpressionStartOffset = returnExpression?.let { expression ->
                source.indexOf(expression, startIndex = startOffset).also { expressionStart ->
                    check(expressionStart in startOffset until endOffset) {
                        "Missing return expression $expression in $statement"
                    }
                }
            }
            val returnExpressionEndOffset = returnExpressionStartOffset?.plus(requireNotNull(returnExpression).length)

            return CallsSourceTarget(
                targetId = "$functionName#$statement",
                siteId = "$functionName:$startOffset:$endOffset",
                sourcePath = sourceRoot.relativize(sourcePath).joinToString(separator = "/"),
                startOffset = startOffset,
                endOffset = endOffset,
                start = sourcePositionAt(source = source, offset = startOffset),
                end = sourcePositionAt(source = source, offset = endOffset),
                mode = mode,
                returnExpressionStartOffset = returnExpressionStartOffset,
                returnExpressionEndOffset = returnExpressionEndOffset,
            )
        }

        fun replay(
            exportName: String,
            inputs: List<JsConcreteValue>,
            target: CallsSourceTarget,
        ): CallsSourceReplayResult = OriginalTypeScriptTargetReplayer().replay(
            sourceRoots = listOf(sourceRoot),
            entryPoint = TypeScriptEntryPoint(module = target.sourcePath, exportName = exportName),
            inputs = inputs,
            target = target,
            timeoutMillis = 10_000L,
        )
    }
}
