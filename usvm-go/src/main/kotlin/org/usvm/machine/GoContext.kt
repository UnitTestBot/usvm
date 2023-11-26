package org.usvm.machine

import org.usvm.*

internal typealias USizeSort = UBv32Sort

class GoContext(
    components: UComponents<GoType, USizeSort>,
) : UContext<USizeSort>(components) {
    private var argsCount: Int = 0

    fun getArgsCount() = argsCount

    fun setArgsCount(count: Int) {
        argsCount = count
    }
}
