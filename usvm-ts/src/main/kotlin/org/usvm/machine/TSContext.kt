package org.usvm.machine

import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsRefType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnknownType
import org.usvm.UBv32Sort
import org.usvm.UContext
import org.usvm.USort
import org.usvm.machine.expr.TSUndefinedSort
import org.usvm.machine.expr.TSUndefinedValue
import org.usvm.machine.expr.TSUnresolvedSort

typealias TSSizeSort = UBv32Sort

class TSContext(components: TSComponents) : UContext<TSSizeSort>(components) {
    val undefinedSort: TSUndefinedSort by lazy { TSUndefinedSort(this) }

    val unresolvedSort: TSUnresolvedSort = TSUnresolvedSort(this)

    private val undefinedValue by lazy { TSUndefinedValue(this) }

    fun typeToSort(type: EtsType): USort = when (type) {
        is EtsBooleanType -> boolSort
        is EtsNumberType -> fp64Sort
        is EtsRefType -> addressSort
        is EtsUnknownType -> unresolvedSort
        else -> TODO("Support all JacoDB types")
    }

    fun mkUndefinedValue(): TSUndefinedValue = undefinedValue
}
