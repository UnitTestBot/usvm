package org.usvm.api.targets

import org.usvm.UBoolExpr
import org.usvm.UHeapRef
import org.usvm.machine.JcContext
import org.usvm.machine.interpreter.JcStepScope


sealed interface TaintActionVisitor {
    fun visit(action: CopyAllMarks, stepScope: JcStepScope, condition: UBoolExpr?)
    fun visit(action: AssignMark, stepScope: JcStepScope, condition: UBoolExpr?)
    fun visit(action: RemoveAllMarks, stepScope: JcStepScope, condition: UBoolExpr?)
    fun visit(action: RemoveMark, stepScope: JcStepScope, condition: UBoolExpr?)
}

class TaintActionResolver(
    private val ctx: JcContext,
    private val positionResolver: PositionResolver,
    private val readMark: (ref: UHeapRef, mark: JcTaintMark, JcStepScope) -> UBoolExpr,
    private val writeMark: (ref: UHeapRef, mark: JcTaintMark, UBoolExpr, JcStepScope) -> Unit,
    private val removeMark: (ref: UHeapRef, JcTaintMark, UBoolExpr, JcStepScope) -> Unit,
    private val allMarks: Set<JcTaintMark>,
) : TaintActionVisitor {

    override fun visit(action: CopyAllMarks, stepScope: JcStepScope, condition: UBoolExpr?) {
        val resolvedFrom = positionResolver.resolve(action.from) as? ResolvedRefPosition ?: return
        val resolvedTo = positionResolver.resolve(action.to) as? ResolvedRefPosition ?: return

        allMarks.forEach {
            val fromValue = readMark(resolvedFrom.resolved, it, stepScope)
            writeMark(resolvedTo.resolved, it, ctx.mkAnd(condition ?: ctx.trueExpr, fromValue), stepScope)
        }
    }

    override fun visit(action: AssignMark, stepScope: JcStepScope, condition: UBoolExpr?) {
        val resolvedRef = positionResolver.resolve(action.position) as? ResolvedRefPosition ?: return
        writeMark(resolvedRef.resolved, action.mark, condition ?: ctx.trueExpr, stepScope)
    }

    override fun visit(action: RemoveAllMarks, stepScope: JcStepScope, condition: UBoolExpr?) {
        val resolvedRef = positionResolver.resolve(action.position) as? ResolvedRefPosition ?: return
        allMarks.forEach {
            removeMark(resolvedRef.resolved, it, condition ?: ctx.trueExpr, stepScope)
        }
    }

    override fun visit(action: RemoveMark, stepScope: JcStepScope, condition: UBoolExpr?) {
        val resolvedRef = positionResolver.resolve(action.position) as? ResolvedRefPosition ?: return
        removeMark(resolvedRef.resolved, action.mark, condition ?: ctx.trueExpr, stepScope)
    }
}

sealed interface Action {
    fun accept(visitor: TaintActionVisitor, stepScope: JcStepScope, condition: UBoolExpr?)
}

// TODO add marks for aliases (if you pass an object and return it from the function)
class CopyAllMarks(val from: Position, val to: Position) : Action {
    override fun accept(visitor: TaintActionVisitor, stepScope: JcStepScope, condition: UBoolExpr?) {
        visitor.visit(this, stepScope, condition)
    }
}

class AssignMark(val position: Position, val mark: JcTaintMark) : Action {
    override fun accept(visitor: TaintActionVisitor, stepScope: JcStepScope, condition: UBoolExpr?) {
        visitor.visit(this, stepScope, condition)
    }
}

class RemoveAllMarks(val position: Position) : Action {
    override fun accept(visitor: TaintActionVisitor, stepScope: JcStepScope, condition: UBoolExpr?) {
        visitor.visit(this, stepScope, condition)
    }
}

class RemoveMark(val position: Position, val mark: JcTaintMark) : Action {
    override fun accept(visitor: TaintActionVisitor, stepScope: JcStepScope, condition: UBoolExpr?) {
        visitor.visit(this, stepScope, condition)
    }
}
