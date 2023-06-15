package org.usvm.state

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jacodb.api.JcField
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.locals
import org.usvm.JcApplicationGraph
import org.usvm.JcContext
import org.usvm.UCallStack
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModelBase

class JcState(
    override val ctx: JcContext,
    callStack: UCallStack<JcTypedMethod, JcInst> = UCallStack(),
    pathConstraints: UPathConstraints<JcType> = UPathConstraints(ctx),
    memory: UMemoryBase<JcField, JcType, JcTypedMethod> = UMemoryBase(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<JcField, JcType>> = listOf(),
    path: PersistentList<JcInst> = persistentListOf(),
    var methodResult: JcMethodResult = JcMethodResult.NoCall,
) : UState<JcType, JcField, JcTypedMethod, JcInst>(
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

fun JcState.returnValue(valueToReturn: UExpr<out USort>) {
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) {
        memory.stack.pop()
    }

    methodResult = JcMethodResult.Success(valueToReturn)

    if (returnSite != null) {
        newStmt(returnSite)
    }
}

fun JcState.throwException(exception: Exception) {
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) {
        memory.stack.pop()
    }

    methodResult = JcMethodResult.Exception(exception)

    if (returnSite != null) {
        newStmt(returnSite)
    }
}


fun JcState.addEntryMethodCall(
    applicationGraph: JcApplicationGraph,
    method: JcTypedMethod,
) {
    val entryPoint = applicationGraph.entryPoint(method).single()
    callStack.push(method, returnSite = null)
    memory.stack.push(method.parametersWithThisCount, method.localsCount)
    newStmt(entryPoint)
}

fun JcState.addNewMethodCall(
    applicationGraph: JcApplicationGraph,
    method: JcTypedMethod,
    arguments: List<UExpr<out USort>>,
) {
    if (method.enclosingType.jcClass.name == "java.lang.Throwable") { // TODO: skipping construction of throwables
        val nextStmt = applicationGraph.successors(lastStmt).single()
        newStmt(nextStmt)
        return
    }

    val entryPoint = applicationGraph.entryPoint(method).single()
    val returnSite = lastStmt
    callStack.push(method, returnSite)
    memory.stack.push(arguments.toTypedArray(), method.localsCount)
    newStmt(entryPoint)
}


inline val JcTypedMethod.parametersWithThisCount get() = method.parameters.size + if (method.isStatic) 0 else 1

inline val JcTypedMethod.localsCount get() = method.instList.locals.size - parameters.size