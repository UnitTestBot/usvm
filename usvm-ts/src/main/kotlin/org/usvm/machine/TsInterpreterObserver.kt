package org.usvm.machine

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsThrowStmt
import org.usvm.UBoolExpr
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.statistics.UInterpreterObserver

@Suppress("unused")
interface TsInterpreterObserver : UInterpreterObserver {
    /** Each completed selector turn, including the final partial turn and repeated turns of one entry point. */
    fun onEntryPointTimeQuantum(event: TsEntryPointTimeQuantumEvent) {
        // default empty implementation
    }

    /** Actual SMT checks, including retries with soft assumptions; setup queries are also reported. */
    fun onSolverQuery(event: TsSolverQueryEvent) {
        // default empty implementation
    }

    /**
     * Called after the dispatcher completes a model or fallback decision for an unknown call.
     * A model decision is reported once after all its satisfiable successor callbacks complete.
     */
    fun onUnknownCall(event: TsUnknownCallEvent) {
        // default empty implementation
    }

    /** Reports a satisfiable path stopped at an explicit runtime modeling boundary. */
    fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
        // default empty implementation
    }

    fun onAssignStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsAssignStmt,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    /** Called after a completed assignment stores its value, before execution advances. */
    fun onAssignmentCompleted(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsAssignStmt,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    // TODO on entry point

    fun onCallWithUnresolvedArguments(
        simpleValueResolver: TsSimpleValueResolver,
        expr: EtsCallExpr,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    /** Called before a standalone call starts, while its arguments denote the current state. */
    fun onCallStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsCallStmt,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    // TODO onCallWithResolvedArguments

    fun onIfStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsIfStmt,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    fun onIfStatementWithResolvedCondition(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsIfStmt,
        condition: UBoolExpr,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    fun onReturnStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsReturnStmt,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }

    fun onThrowStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsThrowStmt,
        scope: TsStepScope,
    ) {
        // default empty implementation
    }
}

/** A feasible execution path stopped because the runtime model deliberately omits [reason]. */
data class TsRuntimeFeatureLimitationEvent(
    val statement: EtsStmt,
    val reason: TsRuntimeFeatureLimitationReason,
    val detail: String,
)

/** Stable identifiers for bounded runtime features reported by [TsRuntimeFeatureLimitationEvent]. */
enum class TsRuntimeFeatureLimitationReason {
    REGULAR_EXPRESSION_LITERAL,
    FRONTEND_UNSUPPORTED_VALUE,
    STRING_CONCAT_OPERAND_CONVERSION,
    STRING_PRIMITIVE_CONVERSION,
    ARRAY_STORAGE_TYPE,
    ARRAY_NAMED_PROPERTY_READ,
    ARRAY_NAMED_PROPERTY_WRITE,
    ARRAY_INDEX_GROWTH,
    ARRAY_ITERATOR_UNSUPPORTED,
    ARRAY_ELEMENT_KIND_WRITE,
    ARRAY_ELEMENT_DELETE,
    ARRAY_LENGTH_CAPACITY,
    ARRAY_LENGTH_GROWTH,
    STRING_ITERATOR_UNSUPPORTED,
}
