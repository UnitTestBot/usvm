package org.usvm.machine

import org.usvm.*
import org.usvm.domain.GoType

internal typealias USizeSort = UBv32Sort

class GoContext(
    components: UComponents<GoType, USizeSort>,
) : UContext<USizeSort>(components) {
    private var argsCount: Int = 0
    fun idx(name: String): Int = when {
        name.startsWith("t") -> name.removePrefix("t").toInt() + argsCount
        name.startsWith("p") -> name.removePrefix("p").toInt()
        else -> -1
    }

    fun setArgsCount(count: Int) {
        argsCount = count
    }
}
