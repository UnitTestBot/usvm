package org.usvm.machine.call

import io.mockk.every
import io.mockk.mockk
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStringType
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TsUnknownCallDateRoutingTest {
    @Test
    fun `a metadata miss prepares a genuine Date owner for one model application`() {
        val call = dateAliasCall()
        val dateModel = RecordingRejectingModel(id = "date-model", enclosingClassName = "Date")
        val dispatcher = TsModelUnknownCallDispatcher(
            models = TsUnknownCallModelCatalog(models = listOf(dateModel)),
            fallback = TsResidualCallPolicy.STOP_PATH,
        )
        val scope = scope(state = state())

        val outcome = dispatcher.dispatch(scope, call)

        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, outcome)
        val preparedCall = dateModel.calls.single()
        assertEquals(EtsClassSignature.UNKNOWN.copy(name = "Date"), preparedCall.callee.enclosingClass)
        assertSame(call.receiver, preparedCall.receiver)
        assertSame(call.callSite, preparedCall.callSite)
    }

    @Test
    fun `a rejected or active direct target never retries a Date target`() {
        for (directModelActive in listOf(false, true)) {
            val call = dateAliasCall()
            val directModel = RecordingRejectingModel(
                id = "direct-model",
                enclosingClassName = call.callee.enclosingClass.name,
            )
            val dateModel = RecordingRejectingModel(id = "date-model", enclosingClassName = "Date")
            val dispatcher = TsModelUnknownCallDispatcher(
                models = TsUnknownCallModelCatalog(models = listOf(directModel, dateModel)),
                fallback = TsResidualCallPolicy.STOP_PATH,
            )
            val activeModelId = if (directModelActive) directModel.id else null
            val scope = scope(state = state(activeModelId = activeModelId))

            val outcome = dispatcher.dispatch(scope, call)

            assertEquals(TsUnknownCallOutcome.PATH_STOPPED, outcome)
            assertTrue(dateModel.calls.isEmpty())
            if (directModelActive) {
                assertTrue(directModel.calls.isEmpty())
            } else {
                assertSame(call, directModel.calls.single())
            }
        }
    }

    private fun dateAliasCall(): TsUnknownCall {
        val dateGlobalType = EtsFunctionType(
            signature = EtsMethodSignature(
                enclosingClass = EtsClassSignature.UNKNOWN,
                name = "",
                parameters = emptyList(),
                returnType = EtsStringType,
            )
        )

        return TsUnknownCall(
            callee = EtsMethodSignature(
                enclosingClass = EtsClassSignature.UNKNOWN.copy(name = "DateAlias"),
                name = "now",
                parameters = emptyList(),
                returnType = EtsNumberType,
            ),
            receiver = TsUnknownCallValue(
                source = EtsLocal(name = "Date", type = dateGlobalType),
                resolved = null,
            ),
            arguments = emptyList(),
            resultType = EtsNumberType,
            callSite = mockk<EtsStmt>(relaxed = true),
            failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
        )
    }

    private fun state(activeModelId: String? = null): TsState {
        val state = mockk<TsState>()
        every { state.isUnknownCallModelActive(any()) } answers { firstArg<String>() == activeModelId }
        every { state.ctx.falseExpr } returns mockk()

        return state
    }

    private fun scope(state: TsState): TsStepScope {
        val scope = mockk<TsStepScope>()
        every { scope.calcOnState<Any?>(any()) } answers {
            val block = firstArg<TsState.() -> Any?>()
            block(state)
        }
        every { scope.assert(any(), any()) } returns null

        return scope
    }

    private class RecordingRejectingModel(
        override val id: String,
        enclosingClassName: String,
    ) : TsUnknownCallModel {
        override val target = TsUnknownCallTarget(methodName = "now", enclosingClassName = enclosingClassName)
        val calls = mutableListOf<TsUnknownCall>()

        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
            calls += call

            return null
        }
    }
}
