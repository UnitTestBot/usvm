package org.usvm.ts.calls

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceStatementEntryTest {
    @Test
    fun `throw entry precedes constructor lowering and exact throw instruction`() {
        val fixture = loadFixture()
        val sourceStatement = "throw new Error('negative');"
        val target = fixture.target(sourceStatement)
        val exactThrow = fixture.method("nestedLowering").exactStatement<EtsThrowStmt>(target)

        val entry = assertNotNull(sourceStatementEntry(fixture.method("nestedLowering"), target))

        assertTrue(entry.loweringSize > 1, "Throw statement must contain nested constructor lowering")
        assertNotEquals(exactThrow, entry.statement, "Entry must precede the exact throw instruction")
        assertTrue(
            fixture.method("nestedLowering").reachesWithinTarget(entry.statement, exactThrow, target),
            "Exact throw instruction must be reachable from the selected entry inside the statement",
        )
    }

    @Test
    fun `return entry precedes nested call lowering and exact return instruction`() {
        val fixture = loadFixture()
        val sourceStatement = "return Math.round(value) / 2;"
        val target = fixture.target(sourceStatement)
        val exactReturn = fixture.method("nestedLowering").exactStatement<EtsReturnStmt>(target)

        val entry = assertNotNull(sourceStatementEntry(fixture.method("nestedLowering"), target))

        assertTrue(entry.loweringSize > 1, "Return statement must contain nested call lowering")
        assertNotEquals(exactReturn, entry.statement, "Entry must precede the exact return instruction")
        assertTrue(
            fixture.method("nestedLowering").reachesWithinTarget(entry.statement, exactReturn, target),
            "Exact return instruction must be reachable from the selected entry inside the statement",
        )
    }

    @Test
    fun `multiple CFG entries into a candidate source range are rejected`() {
        val fixture = loadFixture()
        val source = fixture.source
        val firstBranch = source.indexOf("return -value;")
        val secondBranchEnd = source.indexOf("return value;") + "return value;".length
        val target = fixture.target(startOffset = firstBranch, endOffset = secondBranchEnd)

        val entry = sourceStatementEntry(fixture.method("separateBranches"), target)

        assertNull(entry)
    }

    private fun loadFixture(): Fixture {
        val path = resourcePath("/calls/SourceStatementEntryFixture.ts")
        val source = Files.readString(path)
        val file = loadEtsFileAutoConvert(path, provider = EtsIrProvider.TS_FRONTEND)
        val methods = EtsScene(projectFiles = listOf(file)).projectClasses
            .flatMap { projectClass -> projectClass.methods }
            .associateBy { method -> method.name }

        return Fixture(source = source, methods = methods)
    }

    private fun resourcePath(name: String): Path {
        val resource = assertNotNull(javaClass.getResource(name), "Missing test resource $name")
        return Paths.get(resource.toURI())
    }

    private data class Fixture(
        val source: String,
        val methods: Map<String, EtsMethod>,
    ) {
        fun method(name: String): EtsMethod = assertNotNull(methods[name], "Missing method $name")

        fun target(sourceStatement: String): CallsSourceTarget {
            val startOffset = source.indexOf(sourceStatement)
            assertTrue(startOffset >= 0, "Missing source statement: $sourceStatement")

            return target(startOffset = startOffset, endOffset = startOffset + sourceStatement.length)
        }

        fun target(startOffset: Int, endOffset: Int): CallsSourceTarget = CallsSourceTarget(
            targetId = "test-target",
            siteId = "test-site",
            sourcePath = "SourceStatementEntryFixture.ts",
            startOffset = startOffset,
            endOffset = endOffset,
            start = source.positionAt(startOffset),
            end = source.positionAt(endOffset),
        )
    }
}

private inline fun <reified T : EtsStmt> EtsMethod.exactStatement(target: CallsSourceTarget): T {
    val matches = cfg.stmts.filterIsInstance<T>().filter { statement ->
        val origin = statement.location.origin ?: return@filter false
        origin.startOffset == target.startOffset && origin.endOffset == target.endOffset
    }

    return assertEquals(1, matches.size, "Expected one exact ${T::class.simpleName} statement").let {
        matches.single()
    }
}

private fun EtsMethod.reachesWithinTarget(
    start: EtsStmt,
    targetStatement: EtsStmt,
    target: CallsSourceTarget,
): Boolean {
    val pending = ArrayDeque<EtsStmt>()
    val visited = mutableSetOf<EtsStmt>()
    pending += start
    while (pending.isNotEmpty()) {
        val statement = pending.removeFirst()
        if (!visited.add(statement)) {
            continue
        }
        if (statement == targetStatement) {
            return true
        }

        cfg.successors(statement).filterTo(pending) { successor ->
            val origin = successor.location.origin ?: return@filterTo false
            origin.startOffset >= target.startOffset && origin.endOffset <= target.endOffset
        }
    }

    return false
}

private fun String.positionAt(offset: Int): CallsSourcePosition {
    val prefix = substring(startIndex = 0, endIndex = offset)
    val line = prefix.count { character -> character == '\n' }
    val lastLineBreak = prefix.lastIndexOf('\n')
    val column = offset - lastLineBreak - 1

    return CallsSourcePosition(line = line, column = column)
}
