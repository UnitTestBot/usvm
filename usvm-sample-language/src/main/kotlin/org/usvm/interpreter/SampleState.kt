package org.usvm.interpreter

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.UCallStack
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.ProgramException
import org.usvm.language.SampleType
import org.usvm.language.Stmt
import org.usvm.language.arity
import org.usvm.language.registersCount
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModel

class SampleState(
    ctx: UContext,
    callStack: UCallStack<Method<*>, Stmt> = UCallStack(),
    pathConstraints: UPathConstraints<SampleType> = UPathConstraints(ctx),
    memory: UMemoryBase<Field<*>, SampleType, Method<*>> = UMemoryBase(ctx, pathConstraints.typeConstraints),
    models: List<UModel> = listOf(),
    path: PersistentList<Stmt> = persistentListOf(),
    var returnRegister: UExpr<out USort>? = null,
    var exceptionRegister: ProgramException? = null,
) : UState<SampleType, Field<*>, Method<*>, Stmt>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models, path
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
fun SampleState.addNewStmt(stmt: Stmt) {
    path = path.add(stmt)
}

fun SampleState.popMethodCall(valueToReturn: UExpr<out USort>?) {
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) { // TODO: looks like hack
        memory.stack.pop()
    }

    returnRegister = valueToReturn

    if (returnSite != null) {
        addNewStmt(returnSite)
    }
}

fun SampleState.addEntryMethodCall(applicationGraph: SampleApplicationGraph, method: Method<*>) {
    addNewMethodCall(applicationGraph, method, List(method.arity) { null })
}

fun SampleState.addNewMethodCall(
    applicationGraph: SampleApplicationGraph,
    method: Method<*>,
    arguments: List<UExpr<out USort>?>,
) {
    // TODO: verify inputRegisters size and values
    val entryPoint = applicationGraph.entryPoint(method).single() // TODO: handle native calls
    val returnSite = path.lastOrNull() // TODO: verify is not null
    val registers = arguments + List(method.registersCount - method.arity) { null }
    callStack.push(method, returnSite)
    memory.stack.push(registers.toTypedArray())
    addNewStmt(entryPoint)
}