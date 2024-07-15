package org.usvm.state

import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsMethod
import org.usvm.PathNode
import org.usvm.TSContext
import org.usvm.TSTarget
import org.usvm.UCallStack
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

class TSState(
    ctx: TSContext,
    override val entrypoint: EtsMethod,
    callStack: UCallStack<EtsMethod, EtsStmt>,
    pathConstraints: UPathConstraints<EtsType> = UPathConstraints(ctx),
    memory: UMemory<EtsType, EtsMethod> = UMemory(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<EtsType>> = listOf(),
    pathNode: PathNode<EtsStmt> = PathNode.root(),
    forkPoints: PathNode<PathNode<EtsStmt>> = PathNode.root(),
    var methodResult: TSMethodResult = TSMethodResult.NoCall,
    targets: UTargetsSet<TSTarget, EtsStmt> = UTargetsSet.empty(),
) : UState<EtsType, EtsMethod, EtsStmt, TSContext, TSTarget, TSState>(
    ctx, callStack, pathConstraints, memory, models, pathNode, forkPoints, targets
) {
    override fun clone(newConstraints: UPathConstraints<EtsType>?): TSState {
        TODO("Not yet implemented")
    }

    override val isExceptional: Boolean
        get() = TODO("Not yet implemented")
}
