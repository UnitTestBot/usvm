package org.usvm.api.checkers

import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCallExpr
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcCatchInst
import org.jacodb.api.jvm.cfg.JcEnterMonitorInst
import org.jacodb.api.jvm.cfg.JcExitMonitorInst
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInstVisitor
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcSwitchInst
import org.jacodb.api.jvm.cfg.JcThrowInst
import org.jacodb.api.jvm.cfg.JcValue
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.machine.JcContext
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMethodCallBaseInst
import org.usvm.machine.JcMethodEntrypointInst
import org.usvm.machine.interpreter.JcSimpleValueResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.memory.UReadOnlyMemory

internal class JcCheckerApiImpl : JcCheckerApi {
    // TODO How to retrieve it properly?
    private var internalStepScope: JcStepScope? = null
    private var internalExprResolver: JcSimpleValueResolver? = null

    private val stepScope: JcStepScope
        get() = ensureProcessing(internalStepScope)

    private val exprResolver: JcSimpleValueResolver
        get() = ensureProcessing(internalExprResolver)

    private fun <T> ensureProcessing(e: T?): T =
        e ?: error("Checker API can be used during instruction processing only")

    override val ctx: JcContext
        get() = stepScope.calcOnState { ctx }

    override val memory: UReadOnlyMemory<JcType>
        get() = stepScope.calcOnState { memory }

    override fun resolveValue(value: JcValue): UExpr<*> = value.accept(exprResolver)

    override fun checkSat(condition: UBoolExpr): JcCheckerResult =
        stepScope.checkSat(condition)?.let {
            JcCheckerSatResultImpl
        } ?: JcCheckerUnsatResultImpl

    internal fun setResolverAndScope(simpleValueResolver: JcSimpleValueResolver, stepScope: JcStepScope) {
        internalExprResolver = simpleValueResolver
        internalStepScope = stepScope
    }

    internal fun resetResolverAndScope() {
        internalStepScope = null
        internalExprResolver = null
    }
}

internal object JcCheckerSatResultImpl : JcCheckerSatResult
internal object JcCheckerUnsatResultImpl : JcCheckerUnsatResult
internal object JcCheckerUnknownSatResultImpl : JcCheckerUnknownResult

internal class JcCheckerObserver<T>(
    private val visitor: JcInstVisitor<T>,
    private val jcCheckerApi: JcCheckerApiImpl,
) : JcInterpreterObserver {
    override fun onAssignStatement(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcAssignInst,
        stepScope: JcStepScope,
    ) {
        withScopeAndResolver(simpleValueResolver, stepScope) { visitor.visitJcAssignInst(stmt) }
    }

    override fun onEntryPoint(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcMethodEntrypointInst,
        stepScope: JcStepScope,
    ) {
        withScopeAndResolver(simpleValueResolver, stepScope) {
            // Looks like we do not need this signal in the ast-based checker
        }
    }

    override fun onMethodCallWithUnresolvedArguments(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcCallExpr,
        stepScope: JcStepScope,
    ) {
        withScopeAndResolver(simpleValueResolver, stepScope) {
            // Looks like we do not need this signal in the ast-based checker
        }
    }

    override fun onMethodCallWithResolvedArguments(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcMethodCallBaseInst,
        stepScope: JcStepScope,
    ) {
        withScopeAndResolver(simpleValueResolver, stepScope) {
            // Looks like we do not need this signal in the ast-based checker
        }
    }

    override fun onIfStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcIfInst, stepScope: JcStepScope) {
        withScopeAndResolver(simpleValueResolver, stepScope) { visitor.visitJcIfInst(stmt) }
    }

    override fun onReturnStatement(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcReturnInst,
        stepScope: JcStepScope,
    ) {
        withScopeAndResolver(simpleValueResolver, stepScope) { visitor.visitJcReturnInst(stmt) }
    }

    override fun onGotoStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcGotoInst, stepScope: JcStepScope) {
        withScopeAndResolver(simpleValueResolver, stepScope) { visitor.visitJcGotoInst(stmt) }
    }

    override fun onCatchStatement(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcCatchInst,
        stepScope: JcStepScope
    ) {
        withScopeAndResolver(simpleValueResolver, stepScope) { visitor.visitJcCatchInst(stmt) }
    }

    override fun onSwitchStatement(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcSwitchInst,
        stepScope: JcStepScope,
    ) {
        withScopeAndResolver(simpleValueResolver, stepScope) { visitor.visitJcSwitchInst(stmt) }
    }

    override fun onThrowStatement(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcThrowInst,
        stepScope: JcStepScope,
    ) {
        withScopeAndResolver(simpleValueResolver, stepScope) { visitor.visitJcThrowInst(stmt) }
    }

    override fun onCallStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcCallInst, stepScope: JcStepScope) {
        withScopeAndResolver(simpleValueResolver, stepScope) { visitor.visitJcCallInst(stmt) }
    }

    override fun onEnterMonitorStatement(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcEnterMonitorInst,
        stepScope: JcStepScope,
    ) {
        withScopeAndResolver(simpleValueResolver, stepScope) { visitor.visitJcEnterMonitorInst(stmt) }
    }

    override fun onExitMonitorStatement(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcExitMonitorInst,
        stepScope: JcStepScope,
    ) {
        withScopeAndResolver(simpleValueResolver, stepScope) { visitor.visitJcExitMonitorInst(stmt) }
    }

    // TODO it's very dirty way to get required fields, rewrite
    private inline fun withScopeAndResolver(
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
        blockOnStmt: () -> Unit
    ) = try {
        jcCheckerApi.setResolverAndScope(simpleValueResolver, stepScope)

        blockOnStmt()
    } finally {
        jcCheckerApi.resetResolverAndScope()
    }
}
