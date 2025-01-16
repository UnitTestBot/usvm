package org.usvm.machine

import io.ksmt.utils.cast
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsRefType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnknownType
import org.usvm.UBv32Sort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.expr.TSUndefinedSort
import org.usvm.machine.expr.TSUndefinedValue
import org.usvm.machine.expr.TSUnresolvedSort
import org.usvm.machine.expr.TSWrappedValue

typealias TSSizeSort = UBv32Sort

class TSContext(components: TSComponents) : UContext<TSSizeSort>(components) {

    private val tsWrappedValueCache = mkAstInterner<TSWrappedValue>()
    fun <T : USort> mkTSWrappedValue(value: UExpr<T>): TSWrappedValue = tsWrappedValueCache.createIfContextActive {
        if (value is TSWrappedValue) return@createIfContextActive value

        when (val valueSort = value.sort) {
            boolSort -> TSWrappedValue(this, boolValue = value.cast())
            fp64Sort -> TSWrappedValue(this, fpValue = value.cast())
            addressSort -> TSWrappedValue(this, refValue = value.cast())
            else -> error("Unsupported sort: $valueSort")
        }
    }.cast()

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
