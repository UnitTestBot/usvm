package org.usvm.interpreter

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.UCallStack
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UPathCondition
import org.usvm.UPathConstraintsSet
import org.usvm.USort
import org.usvm.UState
import org.usvm.UTypeSystem
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.ProgramException
import org.usvm.language.SampleType
import org.usvm.language.Stmt
import org.usvm.language.arity
import org.usvm.language.registersCount
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModel

class ExecutionState(
    ctx: UContext,
    typeSystem: UTypeSystem<SampleType>,
    callStack: UCallStack<Method<*>, Stmt> = UCallStack(),
    pathCondition: UPathCondition = UPathConstraintsSet(),
    memory: UMemoryBase<Field<*>, SampleType, Method<*>> = UMemoryBase(ctx, typeSystem),
    models: List<UModel> = listOf(),
    path: PersistentList<Stmt> = persistentListOf(),
    var returnRegister: UExpr<out USort>? = null,
    var exceptionRegister: ProgramException? = null,
) : UState<SampleType, Field<*>, Method<*>, Stmt>(
    ctx,
    typeSystem,
    callStack,
    pathCondition,
    memory,
    models, path
) {
    override fun clone() =
        ExecutionState(
            ctx,
            typeSystem,
            callStack.clone(),
            pathCondition,
            memory.clone(),
            models,
            path,
            returnRegister,
            exceptionRegister
        )
}

val ExecutionState.lastStmt get() = path.last()
fun ExecutionState.addNewStmt(stmt: Stmt) {
    path = path.add(stmt)
}

fun ExecutionState.popMethodCall(valueToReturn: UExpr<out USort>?) {
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) { // TODO: looks like hack
        memory.stack.pop()
    }

    returnRegister = valueToReturn

    if (returnSite != null) {
        addNewStmt(returnSite)
    }
}

fun ExecutionState.addEntryMethodCall(applicationGraph: SampleApplicationGraph, method: Method<*>) {
    addNewMethodCall(applicationGraph, method, List(method.arity) { null })
}

fun ExecutionState.addNewMethodCall(
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