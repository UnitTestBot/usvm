package org.usvm.machine.interpreter

import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFieldSubSignature
import org.usvm.UBoolSort
import org.usvm.UHeapRef
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.state.TsState
import org.usvm.util.mkFieldLValue

internal fun TsState.isInitialized(clazz: EtsClass): Boolean {
    val instance = staticStorage[clazz] ?: error("Static instance for $clazz is not allocated")
    val initializedFlag = ctx.mkStaticFieldsInitializedFlag(instance, clazz.signature)
    return memory.read(initializedFlag).isTrue
}

internal fun TsState.markInitialized(clazz: EtsClass) {
    val instance = staticStorage[clazz] ?: error("Static instance for $clazz is not allocated")
    val initializedFlag = ctx.mkStaticFieldsInitializedFlag(instance, clazz.signature)
    memory.write(initializedFlag, ctx.trueExpr, guard = ctx.trueExpr)
}

private fun TsContext.mkStaticFieldsInitializedFlag(
    instance: UHeapRef,
    clazz: EtsClassSignature,
): UFieldLValue<String, UBoolSort> {
    val field = EtsFieldSignature(
        enclosingClass = clazz,
        sub = EtsFieldSubSignature(
            name = "__initialized__",
            type = EtsBooleanType,
        ),
    )
    return mkFieldLValue(boolSort, instance, field)
}
