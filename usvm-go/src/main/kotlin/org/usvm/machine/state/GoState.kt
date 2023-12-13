package org.usvm.machine.state

import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.domain.GoInst
import org.usvm.domain.GoMethod
import org.usvm.domain.GoType
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
    var methodResult: GoMethodResult = GoMethodResult.NoCall,
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
            methodResult,
        )
    }

    override val isExceptional: Boolean get() = methodResult is GoMethodResult.Panic

    val lastInst get() = pathNode.statement

    fun newInst(inst: GoInst) {
        pathNode += inst
    }

    fun returnValue(valueToReturn: UExpr<out USort>) {
        val returnFromMethod = callStack.lastMethod()
        // TODO: think about it later
        val returnSite = callStack.pop()
        if (callStack.isNotEmpty()) {
            memory.stack.pop()
        }

        methodResult = GoMethodResult.Success(returnFromMethod, valueToReturn)

        if (returnSite != null) {
            newInst(returnSite)
        }
    }

    override fun toString(): String = buildString {
        appendLine("Instruction: $lastInst")
        if (isExceptional) appendLine("Exception: $methodResult")
        appendLine(callStack)
    }
}