package org.usvm.machine.mocks

import io.ksmt.utils.asExpr
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.void
import org.usvm.UMocker
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcMethodCall
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.logger
import org.usvm.machine.state.skipMethodInvocationWithValue

/**
 * Mocks this [methodCall] with its return type according to the [applicationGraph].
 */
fun mockMethod(scope: JcStepScope, methodCall: JcMethodCall, applicationGraph: JcApplicationGraph) {
    val returnType = with(applicationGraph) { methodCall.method.typed }.returnType
    mockMethod(scope, methodCall, returnType)
}

/**
 * Mocks this [methodCall] using [UMocker] of this [scope] with a value of corresponding [returnType],
 * and moves to the next stmt.
 */
fun mockMethod(scope: JcStepScope, methodCall: JcMethodCall, returnType: JcType) = with(methodCall) {
    logger.warn { "Mocked: ${method.enclosingClass.name}::${method.name}" }

    val ctx = scope.calcOnState { ctx }

    if (returnType == ctx.cp.void) {
        scope.doWithState { skipMethodInvocationWithValue(methodCall, ctx.voidValue) }
        return@with
    }

    val mockSort = ctx.typeToSort(returnType)
    val mockValue = scope.calcOnState {
        memory.mocker.call(method, arguments.asSequence(), mockSort, memory.ownership)
    }

    if (mockSort == ctx.addressSort) {
        val constraint = scope.calcOnState {
            memory.types.evalIsSubtype(mockValue.asExpr(ctx.addressSort), returnType)
        }
        scope.assert(constraint) ?: return
    }

    scope.doWithState {
        skipMethodInvocationWithValue(methodCall, mockValue)
    }
}
