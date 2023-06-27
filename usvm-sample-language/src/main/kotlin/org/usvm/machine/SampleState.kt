package org.usvm.machine

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.language.*
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModelBase

class SampleState(
    ctx: UContext,
    callStack: UCallStack<Method<*>, Stmt> = UCallStack(),
    pathConstraints: UPathConstraints<SampleType> = UPathConstraints(ctx),
    memory: UMemoryBase<Field<*>, SampleType, Method<*>> = UMemoryBase(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<Field<*>, SampleType>> = listOf(),
    path: PersistentList<Stmt> = persistentListOf(),
    var returnRegister: UExpr<out USort>? = null,
    var exceptionRegister: ProgramException? = null,
) : UState<SampleType, Field<*>, Method<*>, Stmt>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    path
) {
    override fun clone(newConstraints: UPathConstraints<SampleType>?): SampleState {
        val clonedConstraints = newConstraints ?: pathConstraints.clone()
        return SampleState(
            ctx,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints),
            models,
            path,
            returnRegister,
            exceptionRegister
        )
    }
}

val SampleState.lastStmt get() = path.last()
fun SampleState.newStmt(stmt: Stmt) {
    path = path.add(stmt)
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
