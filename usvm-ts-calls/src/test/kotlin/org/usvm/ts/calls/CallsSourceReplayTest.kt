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
    fun `completed return marks only after its full expression evaluates`() {
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
        assertEquals("returned", replay.invocation?.invocation)
        assertEquals(true, replay.invocation?.targetHit)
    }

    @Test
    fun `completed return rejects when its expression throws`() {
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
        assertEquals("threw", replay.invocation?.invocation)
        assertEquals(false, replay.invocation?.targetHit)
        assertEquals("expression failed", replay.invocation?.errorMessage)
    }

    @Test
    fun `completed return rejects when a finally block throws`() {
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
        assertEquals("threw", replay.invocation?.invocation)
        assertEquals(false, replay.invocation?.targetHit)
        assertEquals("finally failed", replay.invocation?.errorMessage)
    }

    @Test
    fun `completed bare return tolerates comments and omitted semicolon`() {
        val fixture = fixture()
        val target = fixture.target(
            functionName = "completesBareReturn",
            statement = "return /* no expression */",
            mode = CallsSourceTargetMode.COMPLETED_RETURN,
        )

        val replay = fixture.replay(exportName = "completesBareReturn", inputs = emptyList(), target = target)

        assertEquals(CallsReplayStatus.CONFIRMED, replay.status, replay.toString())
        assertEquals("returned", replay.invocation?.invocation)
        assertEquals(true, replay.invocation?.targetHit)
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
