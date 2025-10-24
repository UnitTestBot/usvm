package org.usvm.api.reachability

import org.jacodb.ets.model.EtsMethod
import org.usvm.api.reachability.dto.TargetDto
import org.usvm.api.reachability.dto.TargetTreeNodeDto
import org.usvm.api.reachability.dto.buildLinearTrace
import org.usvm.api.reachability.dto.resolveTarget

/**
 * Represents a trace - an independent hierarchical structure of targets.
 */
sealed interface TargetTrace {
    data class Linear(val targets: List<TargetDto>) : TargetTrace
    data class Tree(val root: TargetTreeNodeDto) : TargetTrace
}

fun createTargetsFromTraces(
    methods: List<EtsMethod>,
    targetTraces: List<TargetTrace>,
): List<TsReachabilityTarget> {
    val targets = mutableListOf<TsReachabilityTarget>()
    val methodMap = methods.associateBy {
        val enclosingClass = it.enclosingClass
        val fileName = enclosingClass?.declaringFile?.name ?: "Unknown"
        val className = enclosingClass?.name ?: "Unknown"
        "$fileName:$className.${it.name}"
    }

    targetTraces.forEach { trace ->
        val root = when (trace) {
            is TargetTrace.Tree -> trace.root
            is TargetTrace.Linear -> buildLinearTrace(trace.targets)!!
        }

        // Resolve the root target and build the hierarchy using addChild
        val rootTarget = resolveTarget(root, methodMap)
        if (rootTarget != null) {
            targets.add(rootTarget)
        }
    }

    return targets
}
