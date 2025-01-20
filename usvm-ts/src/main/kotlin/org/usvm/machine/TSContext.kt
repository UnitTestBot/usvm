package org.usvm.machine

import io.ksmt.sort.KFp64Sort
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsRefType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnknownType
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UBv32Sort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.field.UFieldLValue
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

    fun mkTruthyExpr(expr: UExpr<out USort>): UBoolExpr {
        TODO()
    }

    fun UExpr<out USort>.isFakeObject(): Boolean {
        if (sort !is UAddressSort) return false

        return this is UConcreteHeapRef && address > MAGIC_OFFSET
    }

    fun mkUndefinedValue(): TSUndefinedValue = undefinedValue

    fun mkIntermediateBoolLValue(): UFieldLValue<IntermediateLValueField, UBoolSort> {
        val addr = mkAddressCounter().freshAllocatedAddress() + MAGIC_OFFSET
        return getIntermediateBoolLValue(addr)
    }

    fun getIntermediateBoolLValue(addr: Int): UFieldLValue<IntermediateLValueField, UBoolSort> {
        return UFieldLValue(boolSort, mkConcreteHeapRef(addr), IntermediateLValueField.BOOL)
    }

    fun mkIntermediateFpLValue(): UFieldLValue<IntermediateLValueField, KFp64Sort> {
        val addr = mkAddressCounter().freshAllocatedAddress() + MAGIC_OFFSET
        return getIntermediateFpLValue(addr)
    }

    fun getIntermediateFpLValue(addr: Int): UFieldLValue<IntermediateLValueField, KFp64Sort> {
        return UFieldLValue(mkFp64Sort(), mkConcreteHeapRef(addr), IntermediateLValueField.FP)
    }

    fun mkIntermediateRefLValue(): UFieldLValue<IntermediateLValueField, UAddressSort> {
        val addr = mkAddressCounter().freshAllocatedAddress() + MAGIC_OFFSET
        return getIntermediateRefLValue(addr)
    }

    fun getIntermediateRefLValue(addr: Int): UFieldLValue<IntermediateLValueField, UAddressSort> {
        return UFieldLValue(addressSort, mkConcreteHeapRef(addr), IntermediateLValueField.REF)
    }
}

const val MAGIC_OFFSET = 1000000

enum class IntermediateLValueField {
    BOOL, FP, REF
}
