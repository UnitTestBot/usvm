package org.usvm.api.reachability.dto

import org.usvm.api.reachability.TargetTrace

fun extractTargetTraces(container: TargetsContainerDto): List<TargetTrace> {
    return when (container) {
        // Single trace (linear or tree)
        is TargetsContainerDto.SingleTrace -> {
            listOf(extractTargetTrace(container))
        }

        // Multiple traces (can be linear or tree)
        is TargetsContainerDto.TraceList -> {
            container.traces.map { extractTargetTrace(it) }
        }
    }
}

fun extractTargetTrace(trace: TargetsContainerDto.SingleTrace): TargetTrace {
    return when (trace) {
        // Single linear trace
        is TargetsContainerDto.LinearTrace -> {
            TargetTrace.Linear(targets = trace.targets)
        }

        // Single tree trace
        is TargetsContainerDto.TreeTrace -> {
            TargetTrace.Tree(root = trace.root)
        }
    }
}
