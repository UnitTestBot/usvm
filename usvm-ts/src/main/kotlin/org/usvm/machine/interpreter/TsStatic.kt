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

internal fun TsState.isInitialized(clazz: EtsClass): Boolean {
    val instance = staticStorage[clazz]!!
    val initializedFlag = ctx.staticFieldsInitializedFlag(instance, clazz.signature)
    return memory.read(initializedFlag).isTrue
}

internal fun TsState.markInitialized(clazz: EtsClass) {
    val instance = staticStorage[clazz]!!
    val initializedFlag = ctx.staticFieldsInitializedFlag(instance, clazz.signature)
    memory.write(initializedFlag, ctx.trueExpr, guard = ctx.trueExpr)
}

private fun TsContext.staticFieldsInitializedFlag(
    instance: UHeapRef,
    clazz: EtsClassSignature,
): UFieldLValue<EtsFieldSignature, UBoolSort> {
    val field = EtsFieldSignature(
        enclosingClass = clazz,
        sub = EtsFieldSubSignature(
            name = "__initialized__",
            type = EtsBooleanType,
        ),
    )
    return UFieldLValue(boolSort, instance, field)
}
