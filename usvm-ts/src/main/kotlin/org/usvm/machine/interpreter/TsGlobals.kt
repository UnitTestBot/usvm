package org.usvm.machine.interpreter

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.getDeclaredLocals
import org.usvm.UBoolSort
import org.usvm.UHeapRef
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.state.TsState
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.util.mkFieldLValue

fun EtsFile.getGlobals(): List<EtsLocal> {
    val dfltClass = classes.first { it.name == DEFAULT_ARK_CLASS_NAME }
    val dfltMethod = dfltClass.methods.first { it.name == DEFAULT_ARK_METHOD_NAME }
    return dfltMethod.cfg.stmts
        .filterIsInstance<EtsAssignStmt>()
        .mapNotNull { it.lhv as? EtsLocal }
        .distinct()
}

internal fun TsState.isGlobalsInitialized(file: EtsFile): Boolean {
    val instance = getDfltObject(file)
    val initializedFlag = ctx.mkGlobalsInitializedFlag(instance)
    return memory.read(initializedFlag).isTrue
}

internal fun TsState.markGlobalsInitialized(file: EtsFile) {
    val instance = getDfltObject(file)
    val initializedFlag = ctx.mkGlobalsInitializedFlag(instance)
    memory.write(initializedFlag, ctx.trueExpr, guard = ctx.trueExpr)
}

private fun TsContext.mkGlobalsInitializedFlag(
    instance: UHeapRef,
): UFieldLValue<String, UBoolSort> {
    return mkFieldLValue(boolSort, instance, "__initialized__")
}

internal fun TsState.initializeGlobals(file: EtsFile) {
    markGlobalsInitialized(file)
    val dfltClass = file.classes.first { it.name == DEFAULT_ARK_CLASS_NAME }
    val dfltMethod = dfltClass.methods.first { it.name == DEFAULT_ARK_METHOD_NAME }
    val dfltObject = getDfltObject(file)
    pushSortsForArguments(instance = null, args = emptyList()) { null }
    registerCallee(currentStatement, dfltMethod.cfg)
    callStack.push(dfltMethod, currentStatement)
    memory.stack.push(arrayOf(dfltObject), dfltMethod.localsCount)
    newStmt(dfltMethod.cfg.stmts.first())
}
