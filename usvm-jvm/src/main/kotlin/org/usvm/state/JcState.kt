package org.usvm.state

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.locals
import org.usvm.JcApplicationGraph
import org.usvm.UCallStack
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModel

class JcState(
    ctx: UContext,
    callStack: UCallStack<JcMethod, JcInst> = UCallStack(),
    pathConstraints: UPathConstraints<JcType> = UPathConstraints(ctx),
    memory: UMemoryBase<JcField, JcType, JcMethod> = UMemoryBase(ctx, pathConstraints.typeConstraints),
    models: List<UModel> = listOf(),
    path: PersistentList<JcInst> = persistentListOf(),
    var methodResult: JcMethodResult = JcMethodResult.NoCall,
) : UState<JcType, JcField, JcMethod, JcInst>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    path
) {
    override fun clone(newConstraints: UPathConstraints<JcType>?): JcState {
        val clonedConstraints = newConstraints ?: pathConstraints.clone()
        return JcState(
            ctx,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints),
            models,
            path,
            methodResult,
        )
    }
}

val JcState.lastStmt get() = path.last()
fun JcState.newStmt(stmt: JcInst) {
    path = path.add(stmt)
}

fun JcState.returnValue(valueToReturn: UExpr<out USort>?) {
    val returnSite = callStack.pop()
    memory.stack.pop()

    methodResult = JcMethodResult.Success(valueToReturn)

    if (returnSite != null) {
        newStmt(returnSite)
    }
}

fun JcState.throwException(exception: Exception) {
    val returnSite = callStack.pop()
    memory.stack.pop()

    methodResult = JcMethodResult.Exception(exception)

    if (returnSite != null) {
        newStmt(returnSite)
    }
}



fun JcState.addEntryMethodCall(
    applicationGraph: JcApplicationGraph,
    method: JcMethod
) {
    val entryPoint = applicationGraph.entryPoint(method).single()
    callStack.push(method, returnSite = null)
    memory.stack.push(method.parameters.size, method.localsCount)
    newStmt(entryPoint)
}

fun JcState.addNewMethodCall(
    applicationGraph: JcApplicationGraph,
    method: JcMethod,
    arguments: List<UExpr<out USort>>,
) {
    val entryPoint = applicationGraph.entryPoint(method).single()
    val returnSite = lastStmt
    callStack.push(method, returnSite)
    memory.stack.push(arguments.toTypedArray(), method.localsCount)
    newStmt(entryPoint)
}

inline val JcMethod.localsCount get() = instList.locals.size - parameters.size