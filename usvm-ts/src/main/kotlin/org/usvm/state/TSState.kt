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
    callStack: UCallStack<EtsMethod, EtsStmt> = UCallStack(),
    pathConstraints: UPathConstraints<EtsType> = UPathConstraints(ctx),
    memory: UMemory<EtsType, EtsMethod> = UMemory(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<EtsType>> = listOf(),
    pathNode: PathNode<EtsStmt> = PathNode.root(),
    forkPoints: PathNode<PathNode<EtsStmt>> = PathNode.root(),
    var methodResult: TSMethodResult = TSMethodResult.NoCall,
    targets: UTargetsSet<TSTarget, EtsStmt> = UTargetsSet.empty(),
) : UState<EtsType, EtsMethod, EtsStmt, TSContext, TSTarget, TSState>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    pathNode,
    forkPoints,
    targets
) {
    override fun clone(newConstraints: UPathConstraints<EtsType>?): TSState {
        val clonedConstraints = newConstraints ?: pathConstraints.clone()

        return TSState(
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
        get() = methodResult is TSMethodResult.TSException
}
