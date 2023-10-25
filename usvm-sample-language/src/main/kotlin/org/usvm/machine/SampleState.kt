package org.usvm.machine

import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UState
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
    callStack: UCallStack<Method<*>, Stmt> = UCallStack(),
    pathConstraints: UPathConstraints<SampleType> = UPathConstraints(ctx),
    memory: UMemory<SampleType, Method<*>> = UMemory(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<SampleType>> = listOf(),
    pathNode: PathNode<Stmt> = PathNode.root(),
    var returnRegister: UExpr<out USort>? = null,
    var exceptionRegister: ProgramException? = null,
    targets: UTargetsSet<SampleTarget, Stmt> = UTargetsSet.empty(),
) : UState<SampleType, Method<*>, Stmt, UContext<USizeSort>, SampleTarget, SampleState>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    pathNode,
    targets
) {
    override fun clone(newConstraints: UPathConstraints<SampleType>?): SampleState {
        val clonedConstraints = newConstraints ?: pathConstraints.clone()
        return SampleState(
            ctx,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints),
            models,
            pathNode,
            returnRegister,
            exceptionRegister,
            targets
        )
    }

    override fun mergeWith(other: SampleState, by: Unit): SampleState? {
        val mergedPathNode = pathNode.mergeWith(other.pathNode, Unit) ?: return null

        val mergeGuard = MutableMergeGuard(ctx)
        val mergedCallStack = callStack.mergeWith(other.callStack, Unit) ?: return null
        val mergedPathConstraints = pathConstraints.mergeWith(other.pathConstraints, mergeGuard)
            ?: return null
        val mergedMemory = memory.clone(mergedPathConstraints.typeConstraints).mergeWith(other.memory, mergeGuard)
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

        return SampleState(
            ctx,
            mergedCallStack,
            mergedPathConstraints,
            mergedMemory,
            mergedModels,
            mergedPathNode,
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
