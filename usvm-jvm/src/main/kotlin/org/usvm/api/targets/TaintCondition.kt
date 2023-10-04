package org.usvm.api.targets

import org.jacodb.configuration.And
import org.jacodb.configuration.AnnotationType
import org.jacodb.configuration.CallParameterContainsMark
import org.jacodb.configuration.Condition
import org.jacodb.configuration.ConditionVisitor
import org.jacodb.configuration.ConstantEq
import org.jacodb.configuration.ConstantGt
import org.jacodb.configuration.ConstantLt
import org.jacodb.configuration.ConstantMatches
import org.jacodb.configuration.ConstantTrue
import org.jacodb.configuration.IsConstant
import org.jacodb.configuration.IsType
import org.jacodb.configuration.Not
import org.jacodb.configuration.Or
import org.jacodb.configuration.PositionResolver
import org.jacodb.configuration.SourceFunctionMatches
import org.jacodb.configuration.TaintMark
import org.jacodb.configuration.TypeMatches
import org.usvm.UBoolExpr
import org.usvm.UHeapRef
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.JcContext
import org.usvm.machine.interpreter.JcSimpleValueResolver
import org.usvm.machine.interpreter.JcStepScope

class ConditionResolver(
    private val ctx: JcContext,
    private val positionResolver: PositionResolver<ResolvedPosition<*>?>,
    private val readMark: (ref: UHeapRef, mark: TaintMark, JcStepScope) -> UBoolExpr,
) : ConditionVisitor<UBoolExpr?> {
    private var simpleValueResolver: JcSimpleValueResolver? = null
    private var stepScope: JcStepScope? = null

    fun resolve(condition: Condition, simpleValueResolver: JcSimpleValueResolver, stepScope: JcStepScope): UBoolExpr? {
        try {
            this.simpleValueResolver = simpleValueResolver
            this.stepScope = stepScope

            return condition.accept(this)
        } finally {
            this.simpleValueResolver = null
            this.stepScope = null
        }
    }

    override fun visit(condition: ConstantTrue): UBoolExpr = ctx.trueExpr

    override fun visit(condition: IsConstant): UBoolExpr? {
        val position = positionResolver.resolve(condition.position) ?: return null
        val result = when (position) {
            is ResolvedBoolPosition -> position.resolved.isTrue || position.resolved.isFalse
            is ResolvedRefPosition -> isAllocatedConcreteHeapRef(position.resolved)
        }

        return ctx.mkBool(result)
    }

    override fun visit(condition: IsType): UBoolExpr? {
        error("Unexpected expression")
    }

    override fun visit(condition: Not): UBoolExpr? =
        condition.condition.accept(this)?.let {
            ctx.mkNot(it)
        }

    override fun visit(condition: Or): UBoolExpr = condition
        .conditions
        .mapNotNull { it.accept(this) }
        .let { ctx.mkOr(it) }

    override fun visit(condition: SourceFunctionMatches): UBoolExpr? {
        TODO("Not yet implemented")
    }

    override fun visit(condition: TypeMatches): UBoolExpr? {
        TODO("Not yet implemented")
    }

    override fun visit(condition: And): UBoolExpr? {
        return condition
            .conditions
            .map { it.accept(this) ?: return null }
            .let { ctx.mkAnd(it) }
    }

    override fun visit(condition: AnnotationType): UBoolExpr? {
        TODO("Not yet implemented")
    }

//    override fun visit(condition: BooleanFromArgument): UBoolExpr? =
//        (positionResolver.resolve(condition.argument) as? ResolvedBoolPosition)?.resolved

    override fun visit(condition: CallParameterContainsMark): UBoolExpr? {
        val resolvedPosition = positionResolver.resolve(condition.position) as? ResolvedRefPosition

        return resolvedPosition?.let {
            readMark(it.resolved, condition.mark, requireNotNull(stepScope))
        }
    }

    override fun visit(condition: ConstantEq): UBoolExpr? {
        TODO("Not yet implemented")
    }

    override fun visit(condition: ConstantGt): UBoolExpr? {
        TODO("Not yet implemented")
    }

    override fun visit(condition: ConstantLt): UBoolExpr? {
        TODO("Not yet implemented")
    }

    override fun visit(condition: ConstantMatches): UBoolExpr? {
        TODO("Not yet implemented")
    }
}
