package org.usvm.machine.state

import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.PandaContext
import org.usvm.machine.PandaTarget
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet


class PandaState(
    ctx: PandaContext,
    override val entrypoint: PandaMethod,
    callStack: UCallStack<PandaMethod, PandaInst> = UCallStack(),
    pathConstraints: UPathConstraints<PandaType> = UPathConstraints(ctx),
    memory: UMemory<PandaType, PandaMethod> = UMemory(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<PandaType>> = listOf(),
    pathNode: PathNode<PandaInst> = PathNode.root(),
    forkPoints: PathNode<PathNode<PandaInst>> = PathNode.root(),
    var methodResult: PandaMethodResult = PandaMethodResult.NoCall,
    targets: UTargetsSet<PandaTarget, PandaInst> = UTargetsSet.empty(),
) : UState<PandaType, PandaMethod, PandaInst, PandaContext, PandaTarget, PandaState>(
    ctx, callStack, pathConstraints, memory, models, pathNode, forkPoints, targets
) {
    override fun clone(newConstraints: UPathConstraints<PandaType>?): PandaState {
        val clonedConstraints = newConstraints ?: pathConstraints.clone()
        return PandaState(
            ctx,
            entrypoint,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints),
            models,
            pathNode,
            forkPoints,
            methodResult,
            targets.clone(),
        )
    }

    override val isExceptional: Boolean
        get() = methodResult is PandaMethodResult.PandaException
}
