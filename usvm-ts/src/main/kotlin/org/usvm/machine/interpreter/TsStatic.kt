package org.usvm.machine.interpreter

import mu.KotlinLogging
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.usvm.UBoolSort
import org.usvm.UExpr
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

private fun mkStaticFieldsInitializedFlag(
    instance: UHeapRef,
    clazz: EtsClassSignature,
): UFieldLValue<String, UBoolSort> {
    return mkFieldLValue(instance.ctx.boolSort, instance, "__initialized__")
}

internal fun TsState.isInitialized(clazz: EtsClass): Boolean {
    val instance = getStaticInstance(clazz)
    val initializedFlag = mkStaticFieldsInitializedFlag(instance, clazz.signature)
    return memory.read(initializedFlag).isTrue
}

internal fun TsState.markStaticsInitialized(clazz: EtsClass) {
    val instance = getStaticInstance(clazz)
    val initializedFlag = mkStaticFieldsInitializedFlag(instance, clazz.signature)
    memory.write(initializedFlag, ctx.trueExpr, guard = ctx.trueExpr)
}

private fun TsState.initializeStatics(clazz: EtsClass, initializer: EtsMethod) {
    markStaticsInitialized(clazz)
    val instance = getStaticInstance(clazz)
    pushSortsForArguments(0) { null }
    registerCallee(currentStatement, initializer.cfg)
    callStack.push(initializer, currentStatement)
    memory.stack.push(arrayOf(instance), initializer.localsCount)
    newStmt(initializer.cfg.stmts.first())
}

internal fun TsContext.ensureStaticsInitialized(
    scope: TsStepScope,
    clazz: EtsClass,
): Unit? = scope.calcOnState {
    val initializer = clazz.methods.singleOrNull { it.name == STATIC_INIT_METHOD_NAME }
    if (initializer == null) {
        return@calcOnState Unit
    }

    // Initialize statics in `clazz` if necessary
    if (!isInitialized(clazz)) {
        logger.info { "Statics are not initialized for class: $clazz" }
        initializeStatics(clazz, initializer)
        return@calcOnState null
    }

    // TODO: Handle static initializer result
    val result = methodResult
    // TODO: Why this signature check is needed?
    // TODO: Why we need to reset methodResult here? Double-check that it is even set anywhere.
    if (result is TsMethodResult.Success && result.methodSignature == initializer.signature) {
        methodResult = TsMethodResult.NoCall
    }
}
