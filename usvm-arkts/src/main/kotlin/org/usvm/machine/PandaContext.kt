package org.usvm.machine

import io.ksmt.utils.mkConst
import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaClass
import org.jacodb.panda.dynamic.api.PandaField
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaPrimitiveType
import org.jacodb.panda.dynamic.api.PandaRefType
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaTypeName
import org.jacodb.panda.dynamic.api.PandaUndefinedType
import org.jacodb.panda.dynamic.api.PandaVoidType
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.field.UFieldLValue

class PandaContext(components: PandaComponents) : UContext<PandaNumberSort>(components) {
    val anySort: PandaAnySort by lazy { PandaAnySort(this) }
    val voidSort: PandaVoidSort by lazy { PandaVoidSort(this) }
    val undefinedSort: PandaUndefinedSort by lazy { PandaUndefinedSort(this) }

    val undefinedObject: UExpr<PandaUndefinedSort> = undefinedSort.mkConst("UndefinedObject") // TODO replace?

    fun typeToSort(type: PandaType): USort = when (type) {
        is PandaAnyType -> addressSort // TODO("?????????") can we replace it with address sort????
        is PandaVoidType -> voidSort
        is PandaUndefinedType -> undefinedSort
        is PandaRefType -> addressSort
        is PandaBoolType -> boolSort
        is PandaNumberType -> fp64Sort
        else -> error("Unknown type: $type")
    }

    fun nonRefSortToType(sort: USort): PandaPrimitiveType = when (sort) {
        boolSort -> PandaBoolType
        fp64Sort -> PandaNumberType
        // TODO string
        else -> error("TODO")
    }

    private val auxiliaryClass by lazy {
        PandaClass(
            name = "#Number",
            superClassName = "GLOBAL",
            methods = emptyList()
        )
    }

    fun constructAuxiliaryFieldLValue(ref: UHeapRef, sort: USort) = UFieldLValue(
        sort,
        ref,
        PandaField(
            name = "#value$sort",
            type = nonRefSortToType(sort).typeNameInstance,
            signature = null, // TODO ?????
            enclosingClass = auxiliaryClass
        )
    )

    val PandaType.typeNameInstance: PandaTypeName
        get() = PandaTypeName(typeName)
}
