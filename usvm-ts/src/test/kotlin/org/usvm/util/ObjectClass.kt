package org.usvm.util

import org.jacodb.ets.base.CONSTRUCTOR_NAME
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature

fun createObjectClass(): EtsClass {
    val cls = EtsClassSignature("Object", EtsFileSignature.DEFAULT)
    return EtsClassImpl(
        signature = cls,
        fields = emptyList(),
        methods = emptyList(),
        ctor = EtsMethodImpl(
            EtsMethodSignature(
                enclosingClass = cls,
                name = CONSTRUCTOR_NAME,
                parameters = emptyList(),
                returnType = EtsClassType(cls),
            )
        ),
    )
}
