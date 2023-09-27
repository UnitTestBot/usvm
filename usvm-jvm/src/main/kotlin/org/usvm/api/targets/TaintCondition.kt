package org.usvm.api.targets

import org.usvm.UBoolExpr
import org.usvm.UHeapRef
import org.usvm.machine.JcContext
import org.usvm.machine.interpreter.JcSimpleValueResolver
import org.usvm.machine.interpreter.JcStepScope


interface ConditionVisitor<R> {
    fun visit(condition: ConstantTrue, simpleValueResolver: JcSimpleValueResolver, stepScope: JcStepScope): R
    fun visit(condition: BooleanFromArgument, simpleValueResolver: JcSimpleValueResolver, stepScope: JcStepScope): R
    fun visit(
        condition: CallParameterContainsMark,
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
    ): R

    fun visit(condition: Negation, simpleValueResolver: JcSimpleValueResolver, stepScope: JcStepScope): R
}

class ConditionResolver(
    private val ctx: JcContext,
    private val positionResolver: PositionResolver,
    private val readMark: (ref: UHeapRef, mark: JcTaintMark, JcStepScope) -> UBoolExpr,
) : ConditionVisitor<UBoolExpr?> {
    override fun visit(
        condition: ConstantTrue,
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
    ): UBoolExpr = ctx.trueExpr

    override fun visit(
        condition: BooleanFromArgument,
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
    ): UBoolExpr? = (positionResolver.resolve(condition.argument) as? ResolvedBoolPosition)?.resolved

    override fun visit(
        condition: CallParameterContainsMark,
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
    ): UBoolExpr? {
        val resolvedPosition = positionResolver.resolve(condition.position) as? ResolvedRefPosition

        return resolvedPosition?.let {
            readMark(it.resolved, condition.mark, stepScope)
        }
    }

    override fun visit(
        condition: Negation,
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
    ): UBoolExpr? = condition.condition.accept(this, simpleValueResolver, stepScope)?.let {
        ctx.mkNot(it)
    }
}

interface Condition {
    fun <R> accept(
        conditionVisitor: ConditionVisitor<R>,
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
    ): R
}

class Negation(val condition: Condition) : Condition {
    override fun <R> accept(
        conditionVisitor: ConditionVisitor<R>,
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
    ): R = conditionVisitor.visit(this, simpleValueResolver, stepScope)
}

object ConstantTrue : Condition {
    override fun <R> accept(
        conditionVisitor: ConditionVisitor<R>,
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
    ): R = conditionVisitor.visit(this, simpleValueResolver, stepScope)
}

class BooleanFromArgument(val argument: Argument) : Condition {
    override fun <R> accept(
        conditionVisitor: ConditionVisitor<R>,
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
    ): R = conditionVisitor.visit(this, simpleValueResolver, stepScope)
}

class CallParameterContainsMark(val position: Position, val mark: JcTaintMark) : Condition {
    override fun <R> accept(
        conditionVisitor: ConditionVisitor<R>,
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
    ): R = conditionVisitor.visit(this, simpleValueResolver, stepScope)
}
