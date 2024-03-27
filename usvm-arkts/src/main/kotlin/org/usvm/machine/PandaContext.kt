package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaArrayType
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaClassType
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaUndefinedType
import org.jacodb.panda.dynamic.api.PandaVoidType
import org.usvm.UContext
import org.usvm.USort

class PandaContext(components: PandaComponents) : UContext<PandaFp64Sort>(components) {
    fun typeToSort(type: PandaType): USort = when (type) {
        is PandaAnyType -> fp64Sort // TODO("?????????")
        is PandaVoidType -> TODO()
        is PandaUndefinedType -> TODO()
        is PandaClassType -> TODO()
        is PandaArrayType -> TODO()
        is PandaBoolType -> TODO()
        is PandaNumberType -> TODO()
        else -> error("Unknown type: $type")
    }
}
