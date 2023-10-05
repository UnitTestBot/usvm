package org.usvm.api.targets

import org.jacodb.configuration.Action
import org.jacodb.configuration.AssignMark
import org.jacodb.configuration.CopyAllMarks
import org.jacodb.configuration.CopyMark
import org.jacodb.configuration.PositionResolver
import org.jacodb.configuration.RemoveAllMarks
import org.jacodb.configuration.RemoveMark
import org.jacodb.configuration.TaintActionVisitor
import org.jacodb.configuration.TaintMark
import org.usvm.UBoolExpr
import org.usvm.UHeapRef
import org.usvm.machine.JcContext
import org.usvm.machine.interpreter.JcStepScope

class TaintActionResolver(
    private val ctx: JcContext,
    private val positionResolver: PositionResolver<ResolvedPosition<*>?>,
    private val readMark: (ref: UHeapRef, mark: TaintMark, JcStepScope) -> UBoolExpr,
    private val writeMark: (ref: UHeapRef, mark: TaintMark, UBoolExpr, JcStepScope) -> Unit,
    private val removeMark: (ref: UHeapRef, TaintMark, UBoolExpr, JcStepScope) -> Unit,
    private val allMarks: Set<TaintMark>,
) : TaintActionVisitor<Unit> {
    private var stepScope: JcStepScope? = null
    private var condition: UBoolExpr? = null

    fun resolve(action: Action, stepScope: JcStepScope, condition: UBoolExpr?) {
        try {
            this.stepScope = stepScope
            this.condition = condition

            action.accept(this)
        } finally {
            this.stepScope = null
            this.condition = null
        }
    }

    override fun visit(action: CopyAllMarks) {
        val resolvedFrom = positionResolver.resolve(action.from) as? ResolvedRefPosition ?: return
        val resolvedTo = positionResolver.resolve(action.to) as? ResolvedRefPosition ?: return

        val stepScope = requireNotNull(stepScope)

        allMarks.forEach {
            val fromValue = readMark(resolvedFrom.resolved, it, stepScope)
            writeMark(resolvedTo.resolved, it, ctx.mkAnd(condition ?: ctx.trueExpr, fromValue), stepScope)
        }
    }

    override fun visit(action: CopyMark) {
        val resolvedFrom = positionResolver.resolve(action.from) as? ResolvedRefPosition ?: return
        val resolvedTo = positionResolver.resolve(action.to) as? ResolvedRefPosition ?: return

        val stepScope = requireNotNull(stepScope)

        val fromValue = readMark(resolvedFrom.resolved, action.mark, stepScope)
        writeMark(resolvedTo.resolved, action.mark, ctx.mkAnd(condition ?: ctx.trueExpr, fromValue), stepScope)
    }

    override fun visit(action: AssignMark) {
        val resolvedRef = positionResolver.resolve(action.position) as? ResolvedRefPosition ?: return
        writeMark(resolvedRef.resolved, action.mark, condition ?: ctx.trueExpr, requireNotNull(stepScope))
    }

    override fun visit(action: RemoveAllMarks) {
        val resolvedRef = positionResolver.resolve(action.position) as? ResolvedRefPosition ?: return
        allMarks.forEach {
            removeMark(resolvedRef.resolved, it, condition ?: ctx.trueExpr, requireNotNull(stepScope))
        }
    }

    override fun visit(action: RemoveMark) {
        val resolvedRef = positionResolver.resolve(action.position) as? ResolvedRefPosition ?: return
        removeMark(resolvedRef.resolved, action.mark, condition ?: ctx.trueExpr, requireNotNull(stepScope))
    }
}
