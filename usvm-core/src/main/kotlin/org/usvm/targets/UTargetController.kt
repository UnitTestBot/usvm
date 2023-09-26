package org.usvm.targets

interface UTargetController {
    val targets: MutableCollection<out UTarget<*, *, *>>
}
