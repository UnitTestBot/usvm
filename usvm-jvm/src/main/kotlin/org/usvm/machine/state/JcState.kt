package org.usvm.machine.state

import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcInst
import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UState
import org.usvm.api.targets.JcTarget
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcContext
import org.usvm.memory.UMemory
import org.usvm.merging.MutableMergeGuard
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

class JcState(
    ctx: JcContext,
    callStack: UCallStack<JcMethod, JcInst> = UCallStack(),
    pathConstraints: UPathConstraints<JcType> = UPathConstraints(ctx),
    memory: UMemory<JcType, JcMethod> = UMemory(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<JcType>> = listOf(),
    pathNode: PathNode<JcInst> = PathNode.root(),
    var methodResult: JcMethodResult = JcMethodResult.NoCall,
    targets: UTargetsSet<JcTarget, JcInst> = UTargetsSet.empty(),
) : UState<JcType, JcMethod, JcInst, JcContext, JcTarget, JcState>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    pathNode,
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
            pathNode,
            methodResult,
            targets.clone(),
        )
    }

    /**
     * Check if this [JcState] can be merged with [other] state.
     *
     * @return the merged state. TODO: Now it may reuse some of the internal components of the former states.
     */
    override fun mergeWith(other: JcState, by: Unit): JcState? {
        // TODO: copy-paste

        val mergedPathNode = pathNode.mergeWith(other.pathNode, Unit) ?: return null

        val mergeGuard = MutableMergeGuard(ctx)
        val mergedCallStack = callStack.mergeWith(other.callStack, Unit) ?: return null
        val mergePathConstraints = pathConstraints.mergeWith(other.pathConstraints, mergeGuard)
            ?: return null
        val mergedMemory = memory.clone(mergePathConstraints.typeConstraints).mergeWith(other.memory, mergeGuard)
            ?: return null
        val mergedModels = models + other.models
        val methodResult = if (other.methodResult == JcMethodResult.NoCall && methodResult == JcMethodResult.NoCall) {
            JcMethodResult.NoCall
        } else {
            return null
        }
        val mergedTargets = targets.takeIf { it == other.targets } ?: return null

        return JcState(
            ctx,
            mergedCallStack,
            mergePathConstraints,
            mergedMemory,
            mergedModels,
            mergedPathNode,
            methodResult,
            mergedTargets
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
