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
    callStack: UCallStack<PandaMethod, PandaInst>,
    pathConstraints: UPathConstraints<PandaType>,
    memory: UMemory<PandaType, PandaMethod>,
    models: List<UModelBase<PandaType>>,
    pathNode: PathNode<PandaInst>,
    forkPoints: PathNode<PathNode<PandaInst>>,
    targets: UTargetsSet<PandaTarget, PandaInst>,
) : UState<PandaType, PandaMethod, PandaInst, PandaContext, PandaTarget, PandaState>(
    ctx, callStack, pathConstraints, memory, models, pathNode, forkPoints, targets
) {
    override fun clone(newConstraints: UPathConstraints<PandaType>?): PandaState {
        TODO("Not yet implemented")
    }

    override val entrypoint: PandaMethod
        get() = TODO("Not yet implemented")
    override val isExceptional: Boolean
        get() = TODO("Not yet implemented")
}