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
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallDecision
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallFailureReason
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsvmRuntimeLimitationObserverTest {
    @Test
    fun `unknown call fallbacks retain their decisions without treating applied models as limitations`() {
        val observer = UsvmRuntimeLimitationObserver()
        val statement = event(detail = "unused", withOrigin = true).statement
        val stopped = TsUnknownCallEvent(
            callSite = statement,
            callee = statement.location.method.signature.copy(name = "missing"),
            failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
            decision = TsUnknownCallDecision.ResidualFallback(policy = TsResidualCallPolicy.STOP_PATH),
        )
        val fresh = stopped.copy(
            callSite = event(detail = "unused", withOrigin = false).statement,
            decision = TsUnknownCallDecision.ResidualFallback(policy = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN),
        )

        observer.onUnknownCall(stopped.copy(decision = TsUnknownCallDecision.ModelApplied(modelId = "modeled")))
        assertTrue(observer.diagnostics.isEmpty())
        repeat(times = 100) { observer.onUnknownCall(stopped) }
        observer.onUnknownCall(fresh)

        val diagnostics = observer.diagnostics
        assertEquals(2, diagnostics.size)
        assertEquals("usvm.unknown-call.path-stopped", diagnostics[0].code)
        assertEquals("original.ts:3:5-3:12", diagnostics[0].path)
        assertTrue("Example::missing" in diagnostics[0].message)
        assertTrue("reason=METHOD_BODY_UNAVAILABLE" in diagnostics[0].message)
        assertEquals("usvm.unknown-call.fresh-symbolic-return", diagnostics[1].code)
        assertEquals("generated.ts#example[0]", diagnostics[1].path)
        assertTrue("FRESH_SYMBOLIC_RETURN" in diagnostics[1].message)
    }

    @Test
    fun `unknown calls share the existing distinct diagnostic bound with runtime limits`() {
        val observer = UsvmRuntimeLimitationObserver()
        val first = event(detail = "first", withOrigin = false)
        observer.onRuntimeFeatureLimitation(first)
        val retained = observer.diagnostics.single()
        val callee = first.statement.location.method.signature

        repeat(times = 100) { index ->
            observer.onUnknownCall(
                TsUnknownCallEvent(
                    callSite = first.statement,
                    callee = callee.copy(name = "missing$index"),
                    failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
                    decision = TsUnknownCallDecision.ResidualFallback(policy = TsResidualCallPolicy.STOP_PATH),
                ),
            )
        }

        val diagnostics = observer.diagnostics
        assertEquals(65, diagnostics.size)
        assertEquals(retained, diagnostics.first())
        assertEquals("usvm.runtime.diagnostics-truncated", diagnostics.last().code)
        assertEquals(diagnostics.size, diagnostics.distinct().size)
    }

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
