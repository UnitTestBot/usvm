package org.usvm.machine.interpreter

import org.jacodb.ets.model.EtsClass
import org.usvm.UBoolSort
import org.usvm.UHeapRef
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.state.TsState
import org.usvm.util.mkFieldLValue

internal fun TsState.isInitialized(clazz: EtsClass): Boolean {
    val instance = staticStorage[clazz] ?: error("Static instance for $clazz is not allocated")
    val initializedFlag = mkStaticFieldsInitializedFlag(instance)
    return memory.read(initializedFlag).isTrue
}

internal fun TsState.markInitialized(clazz: EtsClass) {
    val instance = staticStorage[clazz] ?: error("Static instance for $clazz is not allocated")
    val initializedFlag = mkStaticFieldsInitializedFlag(instance)
    memory.write(initializedFlag, ctx.trueExpr, guard = ctx.trueExpr)
}

private fun mkStaticFieldsInitializedFlag(
    instance: UHeapRef,
): UFieldLValue<String, UBoolSort> {
    return mkFieldLValue(instance.ctx.boolSort, instance, "__initialized__")
}
