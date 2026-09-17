package org.usvm.machine.state

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TsEntryPointGuardResultStmt
import org.usvm.util.type

val TsState.lastStmt: EtsStmt
    get() = currentStatement

fun TsState.newStmt(stmt: EtsStmt) {
    pathNode += stmt
}

fun TsState.returnValue(valueToReturn: UExpr<out USort>) {
    val returnFromMethod = callStack.lastMethod()
    val returnSite = callStack.pop()
    if (callStack.isNotEmpty()) {
        memory.stack.pop()
        popLocalToSortStack()
    }

    methodResult = TsMethodResult.Success.RegularCall(valueToReturn, returnFromMethod)

    if (returnSite != null) {
        newStmt(returnSite)
    }
}

/** Executes [guard] over [arguments] before resuming this state's original entry point. */
fun TsState.prependBooleanEntryPointGuard(
    guard: EtsMethod,
    arguments: List<UExpr<*>>,
) {
    require(!entryPointGuardActive) { "An entry-point guard is already active" }
    require(arguments.size == guard.parameters.size) {
        "Expected ${guard.parameters.size} guard arguments, got ${arguments.size}"
    }

    val originalEntryPoint = currentStatement
    val receiver = memory.allocConcrete(requireNotNull(guard.enclosingClass).type)
    val actualArguments = listOf(receiver) + arguments

    pushSortsForActualArguments(actualArguments)
    callStack.push(guard, TsEntryPointGuardResultStmt(originalEntryPoint))
    memory.stack.push(actualArguments.toTypedArray(), guard.localsCount)
    entryPointGuardActive = true
    newStmt(guard.cfg.instructions.first())
}

inline val EtsMethod.parametersWithThisCount: Int
    get() = parameters.size + 1

inline val EtsMethod.localsCount: Int
    get() = locals.size
