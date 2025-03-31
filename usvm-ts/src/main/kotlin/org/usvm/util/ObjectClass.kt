package org.usvm.util

import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.utils.createConstructor
import org.usvm.machine.expr.*

fun createObjectClass(): TsClass {
    val cls = TsClassSignature("Object", TsFileSignature.UNKNOWN)
    val ctor = createConstructor(cls)
    return EtsClassImpl(
        signature = cls,
        fields = emptyList(),
        methods = listOf(ctor),
    )
}
