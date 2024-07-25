package org.usvm.machine

import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.language.Method
import org.usvm.language.ProgramException
import org.usvm.language.SampleType
import org.usvm.language.Stmt
import org.usvm.language.argumentCount
import org.usvm.language.localsCount
import org.usvm.memory.UMemory
import org.usvm.merging.MutableMergeGuard
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

class SampleState(
    ctx: UContext<USizeSort>,
    ownership: MutabilityOwnership,
    override val entrypoint: Method<*>,
    callStack: UCallStack<Method<*>, Stmt> = UCallStack(),
    pathConstraints: UPathConstraints<SampleType> = UPathConstraints(ctx, ownership),
    memory: UMemory<SampleType, Method<*>> = UMemory(ctx, ownership, pathConstraints.typeConstraints),
    models: List<UModelBase<SampleType>> = listOf(),
    pathNode: PathNode<Stmt> = PathNode.root(),
    forkPoints: PathNode<PathNode<Stmt>> = PathNode.root(),
    var returnRegister: UExpr<out USort>? = null,
    var exceptionRegister: ProgramException? = null,
    targets: UTargetsSet<SampleTarget, Stmt> = UTargetsSet.empty(),
) : UState<SampleType, Method<*>, Stmt, UContext<USizeSort>, SampleTarget, SampleState>(
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
    override fun clone(newConstraints: UPathConstraints<SampleType>?): SampleState {
        val newThisOwnership = MutabilityOwnership()
        val cloneOwnership = MutabilityOwnership()
        val clonedConstraints = newConstraints?.also {
            this.pathConstraints.changeOwnership(newThisOwnership)
            it.changeOwnership(cloneOwnership)
        } ?: pathConstraints.clone(newThisOwnership, cloneOwnership)
        return SampleState(
            ctx,
            cloneOwnership,
            entrypoint,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints, newThisOwnership, cloneOwnership),
            models,
            pathNode,
            forkPoints,
            returnRegister,
            exceptionRegister,
            targets
        )
    }

    /**
     * Check if this [SampleState] can be merged with [other] state.
     *
     * @return the merged state. TODO: Now it may reuse some of the internal components of the former states.
     */
    override fun mergeWith(other: SampleState, by: Unit): SampleState? {
        require(entrypoint == other.entrypoint) { "Cannot merge states with different entrypoints" }
        val thisOwnership = MutabilityOwnership()
        val otherOwnership = MutabilityOwnership()
        val mergedOwnership = MutabilityOwnership()
        val mergedPathNode = pathNode.mergeWith(other.pathNode, Unit) ?: return null
        val mergedForkPoints = forkPoints.mergeWith(other.forkPoints, Unit) ?: return null

        val mergeGuard = MutableMergeGuard(ctx)
        val mergedCallStack = callStack.mergeWith(other.callStack, Unit) ?: return null
        val mergedPathConstraints =
            pathConstraints.mergeWith(
                other.pathConstraints, mergeGuard, thisOwnership, otherOwnership, mergedOwnership
            ) ?: return null
        val mergedMemory = memory.clone(mergedPathConstraints.typeConstraints, thisOwnership, otherOwnership)
            .mergeWith(other.memory, mergeGuard, thisOwnership, otherOwnership, mergedOwnership)
            ?: return null
        val mergedModels = models + other.models
        val mergedReturnRegister = if (returnRegister == null && other.returnRegister == null) {
            null
        } else {
            return null
        }
        val mergedExceptionRegister = if (exceptionRegister == null && other.exceptionRegister == null) {
            null
        } else {
            return null
        }
        val mergedTargets = targets.takeIf { it == other.targets } ?: return null
        mergedPathConstraints += ctx.mkOr(mergeGuard.thisConstraint, mergeGuard.otherConstraint)

        this.ownership = thisOwnership
        other.ownership = otherOwnership
        return SampleState(
            ctx,
            mergedOwnership,
            entrypoint,
            mergedCallStack,
            mergedPathConstraints,
            mergedMemory,
            mergedModels,
            mergedPathNode,
            mergedForkPoints,
            mergedReturnRegister,
            mergedExceptionRegister,
            mergedTargets
        )

    }

    override val isExceptional: Boolean
        get() = exceptionRegister != null
}

val SampleState.lastStmt: Stmt get() = pathNode.statement
fun SampleState.newStmt(stmt: Stmt) {
    pathNode += stmt
}

fun SampleState.popMethodCall(valueToReturn: UExpr<out USort>?) {
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) {
        memory.stack.pop()
    }

    returnRegister = valueToReturn

    if (returnSite != null) {
        newStmt(returnSite)
    }
}

fun SampleState.addEntryMethodCall(
    applicationGraph: SampleApplicationGraph,
    method: Method<*>,
) {
    val entryPoint = applicationGraph.entryPoints(method).single()
    callStack.push(method, returnSite = null)
    memory.stack.push(method.argumentCount, method.localsCount)
    newStmt(entryPoint)
}

fun SampleState.addNewMethodCall(
    applicationGraph: SampleApplicationGraph,
    method: Method<*>,
    arguments: List<UExpr<out USort>>,
) {
    val entryPoint = applicationGraph.entryPoints(method).single()
    val returnSite = lastStmt
    callStack.push(method, returnSite)
    memory.stack.push(arguments.toTypedArray(), method.localsCount)
    newStmt(entryPoint)
}
