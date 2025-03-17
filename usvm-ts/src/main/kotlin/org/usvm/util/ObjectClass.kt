package org.usvm.util

import org.usvm.model.TsClass
import org.usvm.model.TsClassImpl
import org.usvm.model.TsClassSignature
import org.usvm.model.TsFileSignature

fun createObjectClass(): TsClass {
    val cls = TsClassSignature("Object", TsFileSignature.UNKNOWN)
    val ctor = createConstructor(cls)
    return TsClassImpl(
        signature = cls,
        fields = emptyList(),
        methods = listOf(ctor),
    )
}
