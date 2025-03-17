package org.usvm.util

import org.jacodb.ets.base.CONSTRUCTOR_NAME
import org.usvm.model.TsClassSignature
import org.usvm.model.TsClassType
import org.usvm.model.TsMethod
import org.usvm.model.TsMethodImpl
import org.usvm.model.TsMethodSignature

fun createConstructor(
    cls: TsClassSignature,
): TsMethod {
    return TsMethodImpl(
        signature = TsMethodSignature(
            enclosingClass = cls,
            name = CONSTRUCTOR_NAME,
            parameters = emptyList(),
            returnType = TsClassType(cls),
        )
    )
}
