package org.usvm.api.checkers

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcCallInst
import org.jacodb.api.cfg.JcCatchInst
import org.jacodb.api.cfg.JcEnterMonitorInst
import org.jacodb.api.cfg.JcExitMonitorInst
import org.jacodb.api.cfg.JcGotoInst
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInstVisitor
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcSwitchInst
import org.jacodb.api.cfg.JcThrowInst
import org.jacodb.api.cfg.JcValue
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UMachineOptions
import org.usvm.api.targets.JcTarget
import org.usvm.machine.JcContext
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMethodCallBaseInst
import org.usvm.machine.JcMethodEntrypointInst
import org.usvm.machine.interpreter.JcSimpleValueResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.memory.UReadOnlyMemory

internal class UCheckersApiImpl : UCheckersApi {
    // TODO How to retrieve it properly?
    internal lateinit var context: JcContext
    internal lateinit var exprResolver: JcSimpleValueResolver // TODO How to retrieve it  properly?
    internal lateinit var stepScope: JcStepScope // TODO How to retrieve it  properly?

    override val ctx: JcContext
        get() = context

    override val memory: UReadOnlyMemory<JcType>
        get() = stepScope.calcOnState { memory }

    override fun resolveValue(value: JcValue): UExpr<*> = value.accept(exprResolver)

    override fun checkSat(condition: UBoolExpr): UCheckResult {
        stepScope.checkSat(condition) {
            // Do nothing
        } ?: return UUnsatCheckResultImpl

        return USatCheckResultImpl
    }

    override fun <T> analyze(
        method: JcMethod,
        cp: JcClasspath,
        checkersVisitor: JcInstVisitor<T>,
        targets: List<JcTarget>,
        options: UMachineOptions,
    ) {
        val checkersObserver = VisitorWrapper(checkersVisitor, this)
        val machine = JcMachine(cp, options, checkersObserver)

        machine.analyze(method, targets)
    }
}

internal object USatCheckResultImpl : USatCheckResult
internal object UUnsatCheckResultImpl : UUnsatCheckResult
internal object UUnknownSatCheckResultImpl : UUnknownCheckResult

internal class VisitorWrapper<T>(
    private val visitor: JcInstVisitor<T>,
    private val usvmCheckersApi: UCheckersApiImpl,
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
    private fun withScopeAndResolver(
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
        blockOnStmt: () -> Unit
    ) {
        val ctx = stepScope.calcOnState { ctx }

        usvmCheckersApi.context = ctx
        usvmCheckersApi.exprResolver = simpleValueResolver
        usvmCheckersApi.stepScope = stepScope

        blockOnStmt()
    }
}
