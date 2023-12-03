package org.usvm.machine

import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext

internal typealias USizeSort = UBv32Sort

class GoContext(components: UComponents<*, USizeSort>) : UContext<USizeSort>(components)
