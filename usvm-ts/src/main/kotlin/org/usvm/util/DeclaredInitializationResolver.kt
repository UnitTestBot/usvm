package org.usvm.util

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.usvm.machine.TsGraph
import org.usvm.machine.TsVirtualMethodCallStmt

/** Construction and instance initialization use their declared owner, including symbolic receivers. */
internal fun TsVirtualMethodCallStmt.resolveDeclaredInitialization(
    hierarchy: EtsHierarchy,
    allocatedType: EtsClassType?,
): EtsMethod? {
    val callee = call.callee
    val isInitialization = callee.name == CONSTRUCTOR_NAME || callee.name == INSTANCE_INIT_METHOD_NAME
    if (targetCaptured || !isInitialization) return null

    val ownerType = allocatedType ?: EtsClassType(callee.enclosingClass)
    val methods = hierarchy.classesForType(ownerType)
        .flatMap { owner -> owner.methods }
        .filter { method -> method.name == callee.name }
        .canonicalizeExecutableOverloads()

    return methods.singleOrNull()
}

/** Argument temporaries can separate allocation and construction; every reaching definition must allocate. */
internal fun TsVirtualMethodCallStmt.hasConstructorAllocation(graph: TsGraph): Boolean {
    if (call.callee.name != CONSTRUCTOR_NAME) return false

    val pending = ArrayDeque(graph.predecessors(returnSite).toList())
    val visited = mutableSetOf<EtsStmt>()
    var foundAllocation = false
    while (pending.isNotEmpty()) {
        val statement = pending.removeFirst()
        if (!visited.add(statement)) continue

        val assignment = statement as? EtsAssignStmt
        val local = assignment?.lhv as? EtsLocal
        if (local?.name == call.instance.name) {
            if (assignment.rhv !is EtsNewExpr) return false
            foundAllocation = true
            continue
        }

        val predecessors = graph.predecessors(statement).toList()
        if (predecessors.isEmpty()) return false
        pending.addAll(predecessors)
    }
    return foundAllocation
}
