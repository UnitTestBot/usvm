package org.usvm.machine.state

import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcInst
import org.usvm.PathsTrieNode
import org.usvm.UCallStack
import org.usvm.UState
import org.usvm.api.targets.JcTarget
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcContext
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase

class JcState(
    ctx: JcContext,
    callStack: UCallStack<JcMethod, JcInst> = UCallStack(),
    pathConstraints: UPathConstraints<JcType, JcContext> = UPathConstraints(ctx),
    memory: UMemory<JcType, JcMethod> = UMemory(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<JcType>> = listOf(),
    override var pathLocation: PathsTrieNode<JcState, JcInst> = ctx.mkInitialLocation(),
    // TODO: should set be public?
    var methodResult: JcMethodResult = JcMethodResult.NoCall,
    targets: List<JcTarget> = emptyList()
) : UState<JcType, JcMethod, JcInst, JcContext, JcTarget, JcState>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    pathLocation,
    targets
) {
    override fun clone(newConstraints: UPathConstraints<JcType, JcContext>?): JcState {
        val clonedConstraints = newConstraints ?: pathConstraints.clone()
        return JcState(
            pathConstraints.ctx,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints),
            models,
            pathLocation,
            methodResult,
            targets.toList()
        )
    }

    override val isExceptional: Boolean
        get() = methodResult is JcMethodResult.JcException

    override fun toString(): String = buildString {
        appendLine("Instruction: $lastStmt")
        if (isExceptional) appendLine("Exception: $methodResult")
        appendLine(callStack)
    }
}
