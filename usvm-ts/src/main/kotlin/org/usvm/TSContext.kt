package org.usvm

import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsRefType
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsVoidType

typealias TSSizeSort = UBv32Sort

class TSContext(components: TSComponents) : UContext<TSSizeSort>(components) {

    val voidSort: TSVoidSort by lazy { TSVoidSort(this) }
    val undefinedSort: TSUndefinedSort by lazy { TSUndefinedSort(this) }
    val stringSort: TSStringSort by lazy { TSStringSort(this) }

    private val undefinedValue by lazy { TSUndefinedValue(this) }

    fun typeToSort(type: EtsType): USort = when (type) {
        is EtsAnyType -> addressSort
        is EtsVoidType -> voidSort
        is EtsUndefinedType -> undefinedSort
        is EtsRefType -> addressSort
        is EtsBooleanType -> boolSort
        is EtsNumberType -> fp64Sort
        is EtsStringType -> stringSort
        else -> error("Unknown type: $type")
    }

    fun mkUndefinedValue(): TSUndefinedValue = undefinedValue
}
