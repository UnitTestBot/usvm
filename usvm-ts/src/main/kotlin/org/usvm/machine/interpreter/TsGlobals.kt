package org.usvm.machine.interpreter

import mu.KotlinLogging
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.usvm.UBoolSort
import org.usvm.UHeapRef
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.util.mkFieldLValue

private val logger = KotlinLogging.logger {}

fun EtsFile.getDfltClass(): EtsClass {
    return classes.first { it.name == DEFAULT_ARK_CLASS_NAME }
}

fun EtsClass.getDfltMethod(): EtsMethod {
    return methods.first { it.name == DEFAULT_ARK_METHOD_NAME }
}

fun EtsFile.getDfltMethod(): EtsMethod {
    val dfltClass = getDfltClass()
    return dfltClass.getDfltMethod()
}

fun EtsFile.getGlobals(): List<EtsLocal> {
    val dfltMethod = getDfltMethod()
    return dfltMethod.cfg.stmts
        .filterIsInstance<EtsAssignStmt>()
        .mapNotNull { it.lhv as? EtsLocal }
        .distinct()
}

private fun TsContext.mkGlobalsInitializedFlag(
    instance: UHeapRef,
): UFieldLValue<String, UBoolSort> {
    return mkFieldLValue(boolSort, instance, "__initialized__")
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

internal fun TsState.initializeGlobals(file: EtsFile) {
    markGlobalsInitialized(file)
    val dfltObject = getDfltObject(file)
    val dfltMethod = file.getDfltMethod()
    pushSortsForArguments(instance = null, args = emptyList()) { null }
    registerCallee(currentStatement, dfltMethod.cfg)
    callStack.push(dfltMethod, currentStatement)
    memory.stack.push(arrayOf(dfltObject), dfltMethod.localsCount)
    newStmt(dfltMethod.cfg.stmts.first())
}

internal fun TsContext.ensureGlobalsInitialized(
    scope: TsStepScope,
    file: EtsFile,
): Unit? = scope.calcOnState {
    // Initialize globals in `file` if necessary
    if (!isGlobalsInitialized(file)) {
        logger.info { "Globals are not initialized for file: $file" }
        initializeGlobals(file)
        return@calcOnState null
    }

    // TODO: handle methodResult
    if (methodResult is TsMethodResult.Success) {
        methodResult = TsMethodResult.NoCall
    }
}
