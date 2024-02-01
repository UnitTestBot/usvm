package org.usvm.machine

import io.ksmt.utils.asExpr
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UConcreteHeapAddress
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UAddressPointer
import org.usvm.ULValuePointer
import org.usvm.USort
import org.usvm.api.UnknownSortException
import org.usvm.machine.type.GoType
import org.usvm.machine.type.GoSort
import org.usvm.memory.ULValue

internal typealias USizeSort = UBv32Sort

class GoContext(
    components: UComponents<GoType, USizeSort>,
) : UContext<USizeSort>(components) {
    private var argsCount: MutableMap<Long, Int> = mutableMapOf()

    fun getArgsCount(method: Long): Int = argsCount[method]!!

    fun setMethodInfo(method: Long, info: GoMethodInfo) {
        argsCount[method] = info.parametersCount
    }

    fun mapSort(sort: GoSort): USort = when (sort) {
        GoSort.BOOL -> boolSort
        GoSort.INT8, GoSort.UINT8 -> bv8Sort
        GoSort.INT16, GoSort.UINT16 -> bv16Sort
        GoSort.INT32, GoSort.UINT32 -> bv32Sort
        GoSort.INT64, GoSort.UINT64 -> bv64Sort
        GoSort.FLOAT32 -> fp32Sort
        GoSort.FLOAT64 -> fp64Sort
        GoSort.ARRAY, GoSort.SLICE, GoSort.MAP, GoSort.STRUCT, GoSort.INTERFACE, GoSort.TUPLE -> addressSort
        GoSort.POINTER -> pointerSort
        else -> throw UnknownSortException()
    }

    fun mkAddressPointer(address: UConcreteHeapAddress): UExpr<USort> {
        return UAddressPointer(this, address).asExpr(pointerSort)
    }

    fun mkLValuePointer(lvalue: ULValue<*, *>): UExpr<USort> {
        return ULValuePointer(this, lvalue).asExpr(pointerSort)
    }
}
