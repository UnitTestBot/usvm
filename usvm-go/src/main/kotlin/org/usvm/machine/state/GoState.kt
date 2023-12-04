package org.usvm.machine.state

import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.*
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

class GoState(
    ctx: GoContext,
    override val entrypoint: GoMethod,
    callStack: UCallStack<GoMethod, GoInst> = UCallStack(),
    pathConstraints: UPathConstraints<GoType> = UPathConstraints(ctx),
    memory: UMemory<GoType, GoMethod> = UMemory(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<GoType>> = listOf(),
    pathNode: PathNode<GoInst> = PathNode.root(),
    targets: UTargetsSet<GoTarget, GoInst> = UTargetsSet.empty(),
    var panicked: Boolean = false,
) : UState<GoType, GoMethod, GoInst, GoContext, GoTarget, GoState>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    pathNode,
    targets,
) {
    override fun clone(newConstraints: UPathConstraints<GoType>?): GoState {
        val clonedConstraints = newConstraints ?: pathConstraints.clone()
        return GoState(
            ctx,
            entrypoint,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints),
            models,
            pathNode,
            targets.clone(),
            panicked,
        )
    }

    override val isExceptional: Boolean get() = panicked

    val lastStmt get() = pathNode.statement
    fun newInst(inst: GoInst) {
        pathNode += inst
    }
}