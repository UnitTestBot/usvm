package org.usvm.util

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.utils.createConstructor

fun createObjectClass(): EtsClass {
    val cls = EtsClassSignature("Object", EtsFileSignature.UNKNOWN)
    val ctor = createConstructor(cls)
    return EtsClassImpl(
        signature = cls,
        fields = emptyList(),
        methods = listOf(ctor),
    )
}
