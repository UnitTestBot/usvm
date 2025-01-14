package org.usvm.machine.state

import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsMethod
import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UState
import org.usvm.api.targets.TSTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.TSContext
import org.usvm.machine.expr.TSExprTransformer
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

class TSState(
    ctx: TSContext,
    ownership: MutabilityOwnership,
    override val entrypoint: EtsMethod,
    callStack: UCallStack<EtsMethod, EtsStmt> = UCallStack(),
    pathConstraints: UPathConstraints<EtsType> = UPathConstraints(ctx, ownership),
    memory: UMemory<EtsType, EtsMethod> = UMemory(ctx, ownership, pathConstraints.typeConstraints),
    models: List<UModelBase<EtsType>> = listOf(),
    pathNode: PathNode<EtsStmt> = PathNode.root(),
    forkPoints: PathNode<PathNode<EtsStmt>> = PathNode.root(),
    var methodResult: TSMethodResult = TSMethodResult.NoCall,
    targets: UTargetsSet<TSTarget, EtsStmt> = UTargetsSet.empty(),
    val exprTransformer: TSExprTransformer
) : UState<EtsType, EtsMethod, EtsStmt, TSContext, TSTarget, TSState>(
    ctx,
    ownership,
    callStack,
    pathConstraints,
    memory,
    models,
    pathNode,
    forkPoints,
    targets
) {
    override fun clone(newConstraints: UPathConstraints<EtsType>?): TSState {
        val newThisOwnership = MutabilityOwnership()
        val cloneOwnership = MutabilityOwnership()
        val clonedConstraints = newConstraints?.also {
            this.pathConstraints.changeOwnership(newThisOwnership)
            it.changeOwnership(cloneOwnership)
        } ?: pathConstraints.clone(newThisOwnership, cloneOwnership)
        this.ownership = newThisOwnership

        return TSState(
            ctx,
            cloneOwnership,
            entrypoint,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints, newThisOwnership, cloneOwnership),
            models,
            pathNode,
            forkPoints,
            methodResult,
            targets.clone(),
            exprTransformer
        )
    }

    override val isExceptional: Boolean
        get() = methodResult is TSMethodResult.TSException
}
