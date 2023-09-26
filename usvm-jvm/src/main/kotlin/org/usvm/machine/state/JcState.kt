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
import org.usvm.targets.UTargetController

class JcState(
    ctx: JcContext,
    callStack: UCallStack<JcMethod, JcInst> = UCallStack(),
    pathConstraints: UPathConstraints<JcType> = UPathConstraints(ctx),
    memory: UMemory<JcType, JcMethod> = UMemory(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<JcType>> = listOf(),
    override var pathLocation: PathsTrieNode<JcState, JcInst> = ctx.mkInitialLocation(),
    var methodResult: JcMethodResult = JcMethodResult.NoCall,
    targets: List<JcTarget<UTargetController>> = emptyList(),
) : UState<JcType, JcMethod, JcInst, JcContext, JcTarget<UTargetController>, JcState>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    pathLocation,
    targets
) {
    override fun clone(newConstraints: UPathConstraints<JcType>?): JcState {
        val clonedConstraints = newConstraints ?: pathConstraints.clone()
        return JcState(
            ctx,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints),
            models,
            pathLocation,
            methodResult,
            targetsImpl,
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
