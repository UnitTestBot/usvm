package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsSourceSpan
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsUndefinedType
import org.junit.jupiter.api.Test
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsvmRuntimeLimitationObserverTest {
    @Test
    fun `deduplicates identical events and retains the original statement location`() {
        val observer = UsvmRuntimeLimitationObserver()
        val event = event(detail = "sparse growth", withOrigin = true)

        repeat(times = 100) { observer.onRuntimeFeatureLimitation(event) }

        val diagnostic = observer.diagnostics.single()
        assertEquals("usvm.runtime.array-index-growth", diagnostic.code)
        assertEquals("original.ts:3:5-3:12", diagnostic.path)
        assertTrue(diagnostic.message.contains("ARRAY_INDEX_GROWTH: sparse growth"))
        assertTrue(diagnostic.message.contains("statement: ${event.statement}"))
    }

    @Test
    fun `bounds unique diagnostics and reports truncation without losing retained records`() {
        val observer = UsvmRuntimeLimitationObserver()
        val first = event(detail = "first", withOrigin = false)
        observer.onRuntimeFeatureLimitation(first)
        val retained = observer.diagnostics.single()

        repeat(times = 100) { index ->
            observer.onRuntimeFeatureLimitation(first.copy(detail = "limitation $index"))
        }

        val diagnostics = observer.diagnostics
        assertEquals(65, diagnostics.size)
        assertEquals(retained, diagnostics.first())
        assertEquals("generated.ts#example[0]", retained.path)
        assertEquals("usvm.runtime.diagnostics-truncated", diagnostics.last().code)
        assertEquals(diagnostics.size, diagnostics.distinct().size)
    }

    private fun event(detail: String, withOrigin: Boolean): TsRuntimeFeatureLimitationEvent {
        val owner = EtsClassSignature(
            name = "Example",
            file = EtsFileSignature(projectName = "fixture", fileName = "generated.ts"),
        )
        val method = EtsMethodImpl(
            EtsMethodSignature(
                enclosingClass = owner,
                name = "example",
                parameters = emptyList(),
                returnType = EtsUndefinedType,
            ),
        )
        val location = EtsStmtLocation(method = method, index = 0)
        if (withOrigin) {
            location.origin = EtsSourceSpan(
                fileName = "original.ts",
                startOffset = 20,
                endOffset = 27,
                startLine = 2,
                startColumn = 4,
                endLine = 2,
                endColumn = 11,
                nodeKind = "ReturnStatement",
            )
        }

        return TsRuntimeFeatureLimitationEvent(
            statement = EtsReturnStmt(location = location, returnValue = null),
            reason = TsRuntimeFeatureLimitationReason.ARRAY_INDEX_GROWTH,
            detail = detail,
        )
    }
}
