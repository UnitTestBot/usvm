package org.usvm.machine.state

import io.ksmt.utils.cast
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
    var data: GoStateData = GoStateData()
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
            lastBlock,
            data.clone()
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

        val mergedData = data.mergeWith(other.data)

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
            lastBlock,
            mergedData
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

        if (!isExceptional) {
            methodResult = GoMethodResult.Success(returnFromMethod, valueToReturn)
        }

        if (returnSite != null) {
            newInst(returnSite)
        }
    }

    fun handlePanic() {
        require(methodResult is GoMethodResult.Panic)

        val returnSite = callStack.pop()
        if (callStack.isNotEmpty()) {
            memory.stack.pop()
        }

        if (returnSite != null) {
            newInst(returnSite)
        }
    }

    fun panic(expr: UExpr<out USort>, type: GoType) {
        methodResult = GoMethodResult.Panic(expr.cast(), type)
    }

    fun recover(method: GoMethod, inst: GoInst): UExpr<out USort> = with(ctx) {
        if (methodResult is GoMethodResult.Panic) {
            val result = (methodResult as GoMethodResult.Panic).value
            methodResult = GoMethodResult.NoCall
            data.setRecover(method, inst)
            return result
        }
        return voidValue
    }

    fun getRecoverInst(method: GoMethod): GoInst? {
        val methodRecover = data.getRecover(method)
        if (methodRecover != null && data.recoverInst == null) {
            data.recoverInst = methodRecover
            return methodRecover
        }
        return null
    }

    fun getDeferInst(method: GoMethod, inst: GoInst): GoInst? {
        val deferInst = data.getDeferInst(method)
        if (deferInst == inst) {
            if (addDeferredCall()) {
                return 0L
            }
            return data.getDeferNextInst(method)
        }
        return null
    }

    fun runDefers(method: GoMethod, inst: GoInst) {
        data.setDeferInst(method, currentStatement)
        data.setDeferNextInst(method, inst)
        data.flowStatus = GoFlowStatus.DEFER
    }

    private fun addDeferredCall(): Boolean {
        val deferred = data.getDeferredCalls(lastEnteredMethod)
        if (!deferred.isEmpty()) {
            addCall(deferred.removeLast(), currentStatement)

            return true
        }

        data.flowStatus = GoFlowStatus.NORMAL
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