package org.usvm.machine

import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USort
import org.usvm.api.UnknownSortException
import org.usvm.machine.type.Type

internal typealias USizeSort = UBv32Sort

class GoContext(
    components: UComponents<GoType, USizeSort>,
) : UContext<USizeSort>(components) {
    private var argsCount: MutableMap<Long, Int> = mutableMapOf()

    fun getArgsCount(method: Long): Int = argsCount[method]!!

    fun setArgsCount(method: Long, count: Int) {
        argsCount[method] = count
    }

    fun typeToSort(type: Type): USort = when (type) {
        Type.BOOL -> boolSort
        Type.INT8, Type.UINT8 -> bv8Sort
        Type.INT16, Type.UINT16 -> bv16Sort
        Type.INT32, Type.UINT32 -> bv32Sort
        Type.INT64, Type.UINT64 -> bv64Sort
        Type.FLOAT32 -> fp32Sort
        Type.FLOAT64 -> fp64Sort
        else -> throw UnknownSortException()
    }
}
