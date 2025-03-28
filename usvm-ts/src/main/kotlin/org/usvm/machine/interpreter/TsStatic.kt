package org.usvm.machine.interpreter

import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.usvm.UBoolSort
import org.usvm.UHeapRef
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.state.TsState
import org.usvm.model.TsClass
import org.usvm.util.mkFieldLValue

internal fun TsState.isInitialized(clazz: TsClass): Boolean {
    val instance = staticStorage[clazz] ?: error("Static instance for $clazz is not allocated")
    val initializedFlag = mkStaticFieldsInitializedFlag(instance)
    return memory.read(initializedFlag).isTrue
}

internal fun TsState.markInitialized(clazz: TsClass) {
    val instance = staticStorage[clazz] ?: error("Static instance for $clazz is not allocated")
    val initializedFlag = mkStaticFieldsInitializedFlag(instance)
    memory.write(initializedFlag, ctx.trueExpr, guard = ctx.trueExpr)
}

private fun mkStaticFieldsInitializedFlag(
    instance: UHeapRef,
): UFieldLValue<String, UBoolSort> {
    val field = EtsFieldSignature(
        enclosingClass = clazz,
        name = "__initialized__",
        type = EtsBooleanType,
    )
    return mkFieldLValue(instance.ctx.boolSort, instance, field)
}
