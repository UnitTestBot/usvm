package org.usvm.machine.state

import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.GoCall
import org.usvm.machine.GoContext
import org.usvm.machine.GoInst
import org.usvm.machine.GoMethod
import org.usvm.machine.GoTarget
import org.usvm.machine.type.GoType
import org.usvm.memory.UMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.merging.MutableMergeGuard
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
    forkPoints: PathNode<PathNode<GoInst>> = PathNode.root(),
    targets: UTargetsSet<GoTarget, GoInst> = UTargetsSet.empty(),
    var methodResult: GoMethodResult = GoMethodResult.NoCall,
    var lastBlock: Int = -1,
) : UState<GoType, GoMethod, GoInst, GoContext, GoTarget, GoState>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    pathNode,
    forkPoints,
    targets
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
            forkPoints,
            targets.clone(),
            methodResult,
            lastBlock
        )
    }

    override fun mergeWith(other: GoState, by: Unit): GoState? {
        require(entrypoint == other.entrypoint) { "Cannot merge states with different entrypoints" }
        // TODO: copy-paste

        val mergedPathNode = pathNode.mergeWith(other.pathNode, Unit) ?: return null
        val mergedForkPoints = forkPoints.mergeWith(other.forkPoints, Unit) ?: return null

        val mergeGuard = MutableMergeGuard(ctx)
        val mergedCallStack = callStack.mergeWith(other.callStack, Unit) ?: return null
        val mergedPathConstraints = pathConstraints.mergeWith(other.pathConstraints, mergeGuard)
            ?: return null
        val mergedMemory = memory.clone(mergedPathConstraints.typeConstraints).mergeWith(other.memory, mergeGuard)
            ?: return null
        val mergedModels = models + other.models
        val methodResult = if (other.methodResult == GoMethodResult.NoCall && methodResult == GoMethodResult.NoCall) {
            GoMethodResult.NoCall
        } else {
            return null
        }
        val mergedTargets = targets.takeIf { it == other.targets } ?: return null
        mergedPathConstraints += ctx.mkOr(mergeGuard.thisConstraint, mergeGuard.otherConstraint)

        return GoState(
            ctx,
            entrypoint,
            mergedCallStack,
            mergedPathConstraints,
            mergedMemory,
            mergedModels,
            mergedPathNode,
            mergedForkPoints,
            mergedTargets,
            methodResult,
            lastBlock
        )
    }

    override val isExceptional: Boolean get() = methodResult is GoMethodResult.Panic

    fun newInst(inst: GoInst) {
        pathNode += inst
    }

    fun returnValue(valueToReturn: UExpr<USort>) {
        val returnFromMethod = lastEnteredMethod
        val returnSite = callStack.pop()
        if (callStack.isNotEmpty()) {
            memory.stack.pop()
        }

        methodResult = GoMethodResult.Success(returnFromMethod, valueToReturn)

        if (returnSite != null) {
            newInst(returnSite)
        }
    }

    fun handlePanic(): Boolean {
        require(methodResult is GoMethodResult.Panic)

        if (runDefers()) {
            return true
        }

        val returnSite = callStack.pop()
        if (callStack.isNotEmpty()) {
            memory.stack.pop()
        }

        if (returnSite != null) {
            newInst(returnSite)
        }

        return false
    }

    fun runDefers(): Boolean = with(ctx) {
        val deferred = getDeferred(lastEnteredMethod)

        if (!deferred.isEmpty()) {
            addCall(deferred.removeLast(), currentStatement)

            return true
        }

        return false
    }

    fun addCall(call: GoCall, returnInst: GoInst? = null) = with(ctx) {
        val methodInfo = getMethodInfo(call.method)

        callStack.push(call.method, returnInst)
        memory.stack.push(call.parameters, methodInfo.variablesCount)

        getFreeVariables(call.method)?.forEachIndexed { index, variable ->
            val lvalue = URegisterStackLValue(variable.sort, index + freeVariableOffset(call.method))
            memory.write(lvalue, variable, trueExpr)
        }

        newInst(call.entrypoint)
    }

    override fun toString(): String = buildString {
        appendLine("Instruction: $currentStatement")
        if (isExceptional) appendLine("Exception: $methodResult")
        appendLine(callStack)
    }
}