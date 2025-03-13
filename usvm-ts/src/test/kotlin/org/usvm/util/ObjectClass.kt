package org.usvm.util

import org.jacodb.ets.base.CONSTRUCTOR_NAME
import org.usvm.model.TsClass
import org.usvm.model.TsClassImpl
import org.usvm.model.TsClassSignature
import org.usvm.model.TsClassType
import org.usvm.model.TsFileSignature
import org.usvm.model.TsMethodImpl
import org.usvm.model.TsMethodSignature

fun createObjectClass(): TsClass {
    val cls = TsClassSignature("Object", TsFileSignature.UNKNOWN)
    val ctor = TsMethodImpl(
        signature = TsMethodSignature(
            enclosingClass = cls,
            name = CONSTRUCTOR_NAME,
            parameters = emptyList(),
            returnType = TsClassType(cls),
        )
    )
    return TsClassImpl(
        signature = cls,
        fields = emptyList(),
        methods = listOf(ctor),
    )
}
