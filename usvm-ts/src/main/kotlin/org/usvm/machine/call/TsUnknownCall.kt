package org.usvm.machine.call

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.UExpr
import org.usvm.api.mockMethodCall
import org.usvm.api.typeStreamOf
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.newStmt
import org.usvm.types.singleOrNull

/**
 * A call that the regular TypeScript execution pipeline could not execute.
 *
 * Instance receivers are normalized under their runtime-kind and conditional-reference guards before method
 * lookup and receiver-dependent approximations. Null and undefined receivers fail at property access.
 * Frontend call resolution and the existing built-in approximations run before this boundary. A call reaches the
 * dispatcher only after one of those stages cannot continue normally. Successful compatibility approximations such
 * as `toString`, `valueOf`, `Math.floor`, and `$r` therefore remain outside this boundary until they are classified
 * and migrated as semantic models. Failures that happen while evaluating a callee or allocating its receiver also
 * remain pre-call failures and are not dispatched.
 */
data class TsUnknownCall(
    val callee: EtsMethodSignature,
    val receiver: TsUnknownCallValue?,
    val arguments: List<TsUnknownCallValue>,
    val resultType: EtsType,
    val callSite: EtsStmt,
    val failureReason: TsUnknownCallFailureReason,
)

/**
 * Keeps the frontend value and the symbolic value, when the latter was available at the point of failure.
 *
 * Compatibility behavior deliberately does not resolve missing values eagerly: doing so could evaluate argument
 * expressions that the old implementation never evaluated before stopping or mocking the call.
 */
data class TsUnknownCallValue(
    val source: EtsValue,
    val resolved: UExpr<*>?,
)

/** Identifies the execution stage that prevented a TypeScript call from continuing normally. */
enum class TsUnknownCallFailureReason {
    STATIC_METHOD_NOT_FOUND,
    NON_REFERENCE_RECEIVER,
    RECEIVER_CLASS_NOT_FOUND,
    UNSUPPORTED_RECEIVER_TYPE,
    VIRTUAL_METHOD_NOT_FOUND,
    RECEIVER_TYPE_STREAM_UNAVAILABLE,
    ANY_RECEIVER,
    NO_SUITABLE_VIRTUAL_TARGET,
    POINTER_TARGET_NOT_FOUND,
    NON_REFERENCE_POINTER,
    METHOD_BODY_UNAVAILABLE,
    INTERPROCEDURAL_ANALYSIS_DISABLED,
    LOGGING_CALL,
    PARTIAL_APPROXIMATION,
}

/** Handles TypeScript calls that could not be executed by the regular call pipeline. */
fun interface TsUnknownCallDispatcher {
    fun dispatch(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallOutcome
}

/** Marks dispatchers that replace migrated compatibility approximations with semantic models. */
interface TsUnknownCallModelDispatcher : TsUnknownCallDispatcher

/** Preserves the pruning and opaque-return behavior that existed before the common dispatch boundary. */
object TsCompatibilityUnknownCallDispatcher : TsUnknownCallDispatcher {
    override fun dispatch(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallOutcome {
        val isUnresolvedConstructor = call.failureReason == TsUnknownCallFailureReason.RECEIVER_CLASS_NOT_FOUND &&
            call.callee.name == CONSTRUCTOR_NAME

        if (isUnresolvedConstructor) {
            val receiver = requireNotNull(call.receiver?.resolved) {
                "An unresolved constructor must have a resolved receiver"
            }
            scope.doWithState {
                methodResult = TsMethodResult.Success.MockedCall(receiver, call.callee)
                newStmt(call.callSite)
            }
            return TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN
        }

        when (call.failureReason) {
            TsUnknownCallFailureReason.ANY_RECEIVER,
            TsUnknownCallFailureReason.NO_SUITABLE_VIRTUAL_TARGET,
            TsUnknownCallFailureReason.NON_REFERENCE_POINTER,
            TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
            TsUnknownCallFailureReason.INTERPROCEDURAL_ANALYSIS_DISABLED,
            TsUnknownCallFailureReason.LOGGING_CALL,
            -> {
                mockMethodCall(scope, call.callee)
                scope.doWithState { newStmt(call.callSite) }
                return TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN
            }

            TsUnknownCallFailureReason.STATIC_METHOD_NOT_FOUND,
            TsUnknownCallFailureReason.NON_REFERENCE_RECEIVER,
            TsUnknownCallFailureReason.RECEIVER_CLASS_NOT_FOUND,
            TsUnknownCallFailureReason.UNSUPPORTED_RECEIVER_TYPE,
            TsUnknownCallFailureReason.VIRTUAL_METHOD_NOT_FOUND,
            TsUnknownCallFailureReason.RECEIVER_TYPE_STREAM_UNAVAILABLE,
            TsUnknownCallFailureReason.POINTER_TARGET_NOT_FOUND,
            -> {
                val falseExpr = scope.calcOnState { ctx.falseExpr }
                scope.assert(falseExpr)
                return TsUnknownCallOutcome.PATH_STOPPED
            }

            TsUnknownCallFailureReason.PARTIAL_APPROXIMATION -> {
                error("Migrated approximations must not be sent to the compatibility dispatcher")
            }
        }
    }
}

internal fun TsUnknownCallDispatcher.dispatch(
    scope: TsStepScope,
    call: EtsCallExpr,
    callSite: EtsStmt,
    failureReason: TsUnknownCallFailureReason,
    callee: EtsMethodSignature = call.callee,
    resolvedReceiver: UExpr<*>? = null,
    resolvedArguments: List<UExpr<*>?> = List(call.args.size) { null },
): TsUnknownCallOutcome {
    require(resolvedArguments.size == call.args.size) {
        "Expected ${call.args.size} resolved argument slots, got ${resolvedArguments.size}"
    }

    val receiverSource = when (call) {
        is EtsInstanceCallExpr -> call.instance
        is EtsPtrCallExpr -> call.ptr
        else -> null
    }
    val receiverIsDate = (call as? EtsInstanceCallExpr)?.let { instanceCall ->
        scope.calcOnState { isDateReceiver(instanceCall, resolvedReceiver) }
    } ?: false
    val normalizedCallee = call.canonicalizeDateCallee(callee, receiverIsDate)
    val unknownCall = TsUnknownCall(
        callee = normalizedCallee,
        receiver = receiverSource?.let { TsUnknownCallValue(it, resolvedReceiver) },
        arguments = call.args.zip(resolvedArguments) { source, resolved ->
            TsUnknownCallValue(source, resolved)
        },
        resultType = call.type,
        callSite = callSite,
        failureReason = failureReason,
    )

    return dispatch(scope, unknownCall)
}

internal fun EtsInstanceCallExpr.hasDateReceiver(): Boolean =
    instance.name == "Date" || when (val type = instance.type) {
        is EtsClassType -> type.signature.name == "Date"
        is EtsUnclearRefType -> type.typeName == "Date"
        else -> false
    }

internal fun TsState.isDateReceiver(
    call: EtsInstanceCallExpr,
    receiver: UExpr<*>?,
): Boolean {
    if (call.hasDateReceiver()) {
        return true
    }
    if (receiver?.sort != ctx.addressSort) {
        return false
    }

    val runtimeType = memory.typeStreamOf(receiver.asExpr(ctx.addressSort)).singleOrNull()
    return (runtimeType as? EtsClassType)?.signature?.name == "Date"
}

private fun EtsCallExpr.canonicalizeDateCallee(
    callee: EtsMethodSignature,
    receiverIsDate: Boolean,
): EtsMethodSignature {
    if (callee.enclosingClass.name == "Date") {
        return callee
    }

    if (!receiverIsDate) {
        return callee
    }

    return callee.copy(enclosingClass = EtsClassSignature.UNKNOWN.copy(name = "Date"))
}

internal fun TsUnknownCallDispatcher.dispatch(
    scope: TsStepScope,
    call: TsVirtualMethodCallStmt,
    failureReason: TsUnknownCallFailureReason,
    resolvedReceiver: UExpr<*>,
) = dispatch(
    scope,
    call.call,
    call.returnSite,
    failureReason,
    resolvedReceiver = resolvedReceiver,
    resolvedArguments = call.args,
)

internal fun TsUnknownCallDispatcher.dispatch(
    scope: TsStepScope,
    call: TsConcreteMethodCallStmt,
    failureReason: TsUnknownCallFailureReason,
    callee: EtsMethodSignature,
) = dispatch(
    scope,
    call.call,
    call.returnSite,
    failureReason,
    callee,
    resolvedReceiver = call.resolvedReceiver,
    resolvedArguments = call.args.takeLast(call.call.args.size),
)
