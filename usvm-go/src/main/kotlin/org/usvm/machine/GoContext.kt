package org.usvm.machine

import org.usvm.*

internal typealias USizeSort = UBv32Sort

class GoContext(
    components: UComponents<GoType, USizeSort>,
) : UContext<USizeSort>(components) {
    private var argsCount: MutableMap<Long, Int> = mutableMapOf()

    fun getArgsCount(method: Long): Int = argsCount[method]!!

    fun setArgsCount(method: Long, count: Int) {
        argsCount[method] = count
    }
}
