package org.usvm.machine.interpreter

import mu.KotlinLogging
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.usvm.UBoolSort
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.captureInitialEnvironment
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt

private val logger = KotlinLogging.logger {}
private object StaticFieldsInitializedFlag
private object StaticFieldsInitializingFlag

private fun TsState.staticInitializationFlag(clazz: EtsClass, flag: Any): UFieldLValue<*, UBoolSort> {
    val instance = getStaticInstance(clazz)
    return UFieldLValue(ctx.boolSort, instance, flag)
}

internal fun TsState.isInitialized(clazz: EtsClass): Boolean =
    memory.read(staticInitializationFlag(clazz, flag = StaticFieldsInitializedFlag)).isTrue

internal fun TsState.isInitializing(clazz: EtsClass): Boolean =
    memory.read(staticInitializationFlag(clazz, flag = StaticFieldsInitializingFlag)).isTrue

/** A recursive read/write may use its current initializer, but completion is recorded only on return. */
internal fun TsState.completeStaticsInitialization(method: EtsMethod) {
    val clazz = method.enclosingClass ?: return
    val isModuleInitializer = clazz.name == DEFAULT_ARK_CLASS_NAME && method.name == DEFAULT_ARK_METHOD_NAME
    val isInitializer = method.name == STATIC_INIT_METHOD_NAME || isModuleInitializer
    val pending = isInitializer && isInitializing(clazz)
    if (!pending) return

    val initializedFlag = staticInitializationFlag(clazz, flag = StaticFieldsInitializedFlag)
    val initializingFlag = staticInitializationFlag(clazz, flag = StaticFieldsInitializingFlag)

    memory.write(initializedFlag, ctx.trueExpr, guard = ctx.trueExpr)
    memory.write(initializingFlag, ctx.falseExpr, guard = ctx.trueExpr)
    captureInitialEnvironment(clazz)
}

internal fun TsState.failStaticsInitialization(method: EtsMethod, exception: TsMethodResult.TsException) {
    val clazz = method.enclosingClass ?: return
    val isModuleInitializer = clazz.name == DEFAULT_ARK_CLASS_NAME && method.name == DEFAULT_ARK_METHOD_NAME
    val isInitializer = method.name == STATIC_INIT_METHOD_NAME || isModuleInitializer
    val pending = isInitializer && isInitializing(clazz)
    if (!pending) return

    val initializingFlag = staticInitializationFlag(clazz, flag = StaticFieldsInitializingFlag)

    staticInitializationFailures = staticInitializationFailures + (clazz to exception)
    memory.write(initializingFlag, ctx.falseExpr, guard = ctx.trueExpr)
}

private fun TsState.initializeStatics(clazz: EtsClass, initializer: EtsMethod) {
    val initializingFlag = staticInitializationFlag(clazz, flag = StaticFieldsInitializingFlag)
    memory.write(initializingFlag, ctx.trueExpr, guard = ctx.trueExpr)

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
    val failure = staticInitializationFailures[clazz]
    if (failure != null) {
        methodResult = failure
        return@calcOnState null
    }

    val initializer = clazz.methods.singleOrNull { it.name == STATIC_INIT_METHOD_NAME }
        ?: clazz.takeIf { it.name == DEFAULT_ARK_CLASS_NAME }
            ?.methods
            ?.singleOrNull { it.name == DEFAULT_ARK_METHOD_NAME }
    if (initializer == null) {
        captureInitialEnvironment(clazz)
        return@calcOnState Unit
    }

    // Initialize statics in `clazz` if necessary
    val requiresInitialization = !isInitialized(clazz) && !isInitializing(clazz)
    if (requiresInitialization) {
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
