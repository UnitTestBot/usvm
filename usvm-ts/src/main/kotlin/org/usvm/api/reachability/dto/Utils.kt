package org.usvm.api.reachability.dto

fun buildLinearTrace(targets: List<TargetDto>): TargetTreeNodeDto? {
    if (targets.isEmpty()) return null

    var current: TargetTreeNodeDto? = null
    for (target in targets.asReversed()) {
        current = TargetTreeNodeDto(target, children = listOfNotNull(current))
    }
    return current
}
