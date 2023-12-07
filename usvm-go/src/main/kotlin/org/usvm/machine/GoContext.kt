package org.usvm.machine

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBv32Sort
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UExpr

internal typealias USizeSort = UBv32Sort

class GoContext(components: UComponents<*, USizeSort>) : UContext<USizeSort>(components) {
    val vars = mutableMapOf<String, UExpr<KBv32Sort>>()
    val expressions = mutableMapOf<String, UExpr<KBoolSort>>()
}
