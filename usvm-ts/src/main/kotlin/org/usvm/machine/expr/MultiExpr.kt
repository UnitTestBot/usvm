package org.usvm.machine.expr

import io.ksmt.expr.KExpr
import io.ksmt.sort.KFp64Sort
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.memory.ULValue

data class MultiExpr(
    val boolValue: UBoolExpr? = null,
    val fpValue: KExpr<KFp64Sort>? = null,
    val refValue: UExpr<UAddressSort>? = null,
) {
    val singleValueOrNull: UExpr<out USort>?
        get() = listOf(boolValue, fpValue, refValue).singleOrNull { it != null }

    val singularSort: USort?
        get() = singleValueOrNull?.sort
}

data class MultiLValue<Key>(
    val boolLValue: ULValue<Key, UBoolSort>? = null,
    val fpLValue: ULValue<Key, KFp64Sort>? = null,
    val refLValue: ULValue<Key, UAddressSort>? = null
)