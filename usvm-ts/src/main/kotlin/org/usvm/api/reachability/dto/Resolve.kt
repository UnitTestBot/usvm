package org.usvm.api.reachability.dto

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.api.reachability.TsReachabilityTarget

private val logger = KotlinLogging.logger {}

fun resolveTarget(
    node: TargetTreeNodeDto,
    methodMap: Map<String, EtsMethod>,
): TsReachabilityTarget? {
    // First, resolve the current node to a TsReachabilityTarget
    val targetLocation = node.target.location
    val methodName = "${targetLocation.fileName}:${targetLocation.className}.${targetLocation.methodName}"
    val statements = methodMap[methodName]?.cfg?.stmts ?: return null
    val stmt = findStatementByTarget(node.target, statements) ?: return null
    logger.debug { "Resolved target ${node.target} to statement $stmt" }

    val currentTarget: TsReachabilityTarget = when (node.target.type) {
        TargetTypeDto.INITIAL -> TsReachabilityTarget.InitialPoint(stmt, node.target.id)
        TargetTypeDto.INTERMEDIATE -> TsReachabilityTarget.IntermediatePoint(stmt, node.target.id)
        TargetTypeDto.FINAL -> TsReachabilityTarget.FinalPoint(stmt, node.target.id)
    }

    // Add all children to build the hierarchical structure
    node.children.forEach { childNode ->
        val childTarget = resolveTarget(childNode, methodMap)
        if (childTarget != null) {
            currentTarget.addChild(childTarget)
        }
    }

    return currentTarget
}

private fun findStatementByTarget(
    target: TargetDto,
    statements: List<EtsStmt>,
): EtsStmt? {
    // Find statement by matching location
    for (stmt in statements) {
        // TODO: handle 'target.stmtType'
        if (matchesLocation(stmt, target.location)) {
            return stmt
        }
    }
    // Return the first statement as a fallback
    return statements.firstOrNull()
}

private fun matchesLocation(stmt: EtsStmt, location: LocationDto): Boolean {
    // Match by block and index
    if (location.block != null) {
        // If the index is not specified, match the first statement in the block
        val index = location.index ?: 0
        if (stmt.location.blockDtoIndex == location.block && stmt.location.stmtDtoIndex == index) {
            return true
        }
    }

    // EXTRA: If only the index is specified, match by index only (using the normal stmt index in linear CFG)
    if (location.block == null && location.index != null) {
        if (stmt.location.index == location.index) {
            return true
        }
    }

    // No match
    return false
}
