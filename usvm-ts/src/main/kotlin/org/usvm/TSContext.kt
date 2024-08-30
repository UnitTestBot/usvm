package org.usvm

import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsRefType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsUnknownType

typealias TSSizeSort = UBv32Sort

class TSContext(components: TSComponents) : UContext<TSSizeSort>(components) {

    val undefinedSort: TSUndefinedSort by lazy { TSUndefinedSort(this) }

    private val undefinedValue by lazy { TSUndefinedValue(this) }

    fun typeToSort(type: EtsType): USort = when (type) {
        is EtsBooleanType -> boolSort
        is EtsNumberType -> fp64Sort
        is EtsRefType -> addressSort
        is EtsUnknownType -> addressSort
        else -> TODO("Support all JacoDB types")
    }

    fun nonRefSortToType(sort: USort): EtsType = when (sort) {
        boolSort -> EtsBooleanType
        fp64Sort -> EtsNumberType
        else -> TODO("Support all non-ref JacoDB types")
    }

    fun mkUndefinedValue(): TSUndefinedValue = undefinedValue
}
