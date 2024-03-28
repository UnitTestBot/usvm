package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaRefType
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaUndefinedType
import org.jacodb.panda.dynamic.api.PandaVoidType
import org.usvm.UContext
import org.usvm.USort

class PandaContext(components: PandaComponents) : UContext<PandaNumberSort>(components) {
    val anySort: PandaAnySort by lazy { PandaAnySort(this) }
    val voidSort: PandaVoidSort by lazy { PandaVoidSort(this) }
    val undefinedSort: PandaUndefinedSort by lazy { PandaUndefinedSort(this) }

    fun typeToSort(type: PandaType): USort = when (type) {
        is PandaAnyType -> addressSort // TODO("?????????") can we replace it with address sort????
        is PandaVoidType -> voidSort
        is PandaUndefinedType -> undefinedSort
        is PandaRefType -> addressSort
        is PandaBoolType -> boolSort
        is PandaNumberType -> fp64Sort
        else -> error("Unknown type: $type")
    }
}
