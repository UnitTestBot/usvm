package org.usvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcInst
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModel

class JcState(
    typeSystem: UTypeSystem<JcType>,
    callStack: UCallStack<JcMethod, JcInst> = UCallStack(),
    pathCondition: UPathCondition = UPathConstraintsSet(),
    memory: UMemoryBase<JcField, JcType, JcMethod> = UMemoryBase(ctx, typeSystem),
    models: List<UModel> = listOf(),
    path: PersistentList<JcInst> = persistentListOf(),
    var returnRegister: UExpr<out USort>? = null,
    var exceptionRegister: Any? = Unit,
) : UState<JcType, JcField, JcMethod, JcInst>(
    typeSystem,
    callStack,
    pathCondition,
    memory,
    models,
    path
) {
    override fun clone(): UState<JcType, JcField, JcMethod, JcInst> =
        JcState(typeSystem, callStack, pathCondition, memory, models, path, returnRegister, exceptionRegister)
}

val JcState.lastStmt get() = path.last()
fun JcState.addNewStmt(stmt: JcInst) {
    path = path.add(stmt)
}

fun JcState.popMethodCall(valueToReturn: UExpr<out USort>?) {
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) { // TODO: looks like hack
        memory.stack.pop()
    }

    returnRegister = valueToReturn

    if (returnSite != null) {
        addNewStmt(returnSite)
    }
}

fun JcState.addEntryMethodCall(applicationGraph: ApplicationGraph<JcMethod, JcInst>, method: JcMethod) {
    addNewMethodCall(applicationGraph, method, List(method.parameters.size) { null })
}

fun JcState.addNewMethodCall(
    applicationGraph: ApplicationGraph<JcMethod, JcInst>,
    method: JcMethod,
    arguments: List<UExpr<out USort>?>,
) {
    // TODO: verify inputRegisters size and values
    val entryPoint = applicationGraph.entryPoint(method).single() // TODO: handle native calls
    val returnSite = path.lastOrNull() // TODO: verify is not null
    callStack.push(method, returnSite)
    memory.stack.push(arguments.toTypedArray())
    addNewStmt(entryPoint)
}