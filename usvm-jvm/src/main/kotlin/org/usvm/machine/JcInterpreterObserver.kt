package org.usvm.machine

import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCallExpr
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcCatchInst
import org.jacodb.api.jvm.cfg.JcEnterMonitorInst
import org.jacodb.api.jvm.cfg.JcExitMonitorInst
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcSwitchInst
import org.jacodb.api.jvm.cfg.JcThrowInst
import org.usvm.machine.interpreter.JcSimpleValueResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.statistics.UInterpreterObserver

interface JcInterpreterObserver : UInterpreterObserver {
    fun onAssignStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcAssignInst, stepScope: JcStepScope) {}
    fun onEntryPoint(simpleValueResolver: JcSimpleValueResolver, stmt: JcMethodEntrypointInst, stepScope: JcStepScope)
    fun onMethodCallWithUnresolvedArguments(simpleValueResolver: JcSimpleValueResolver, stmt: JcCallExpr, stepScope: JcStepScope) {}
    fun onMethodCallWithResolvedArguments(simpleValueResolver: JcSimpleValueResolver, stmt: JcMethodCallBaseInst, stepScope: JcStepScope) {}
    fun onIfStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcIfInst, stepScope: JcStepScope) {}
    fun onReturnStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcReturnInst, stepScope: JcStepScope) {}
    fun onGotoStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcGotoInst, stepScope: JcStepScope) {}
    fun onCatchStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcCatchInst, stepScope: JcStepScope) {}
    fun onSwitchStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcSwitchInst, stepScope: JcStepScope) {}
    fun onThrowStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcThrowInst, stepScope: JcStepScope) {}
    fun onCallStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcCallInst, stepScope: JcStepScope) {}
    fun onEnterMonitorStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcEnterMonitorInst, stepScope: JcStepScope) {}
    fun onExitMonitorStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcExitMonitorInst, stepScope: JcStepScope) {}
}
