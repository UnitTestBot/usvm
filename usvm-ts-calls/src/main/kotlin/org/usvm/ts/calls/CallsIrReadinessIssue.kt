package org.usvm.ts.calls

import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRawEntity
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.utils.getOperands
import org.usvm.machine.TsGraph

internal data class CallsIrReadinessIssue(
    val reasonCode: CallsSymbolicPreflightReasonCode,
    val diagnostic: String,
)

internal fun callsIrReadinessIssue(
    method: EtsMethod,
    graph: TsGraph,
    sourceByFile: Map<EtsFileSignature, String>,
): CallsIrReadinessIssue? {
    val pending = ArrayDeque<EtsMethod>().apply { addLast(method) }
    val visited = hashSetOf<EtsMethod>()

    while (pending.isNotEmpty()) {
        val currentMethod = pending.removeFirst()
        if (!visited.add(currentMethod)) continue

        currentMethod.irReadinessIssue(
            source = sourceByFile.getValue(currentMethod.signature.enclosingClass.file),
            graph = graph,
        )?.let { issue -> return issue }

        currentMethod.projectCallees(graph = graph, sourceFiles = sourceByFile.keys)
            .forEach(pending::addLast)
        currentMethod.projectStaticInitializers(graph = graph, sourceFiles = sourceByFile.keys)
            .forEach(pending::addLast)
    }

    return null
}

private fun EtsMethod.projectCallees(
    graph: TsGraph,
    sourceFiles: Set<EtsFileSignature>,
): Sequence<EtsMethod> = cfg.stmts.asSequence()
    .flatMap { statement ->
        val directCallees = runCatching { graph.callees(statement).toList() }.getOrDefault(emptyList())
        val pointerCallees = statement.walkEntities().filterIsInstance<EtsPtrCallExpr>().flatMap { call ->
            val signature = (call.ptr.type as? EtsFunctionType)?.signature ?: return@flatMap emptyList()
            graph.cp.projectClasses.filter { owner -> owner.signature == signature.enclosingClass }
                .flatMap { owner -> owner.methods }
                .filter { callee -> callee.signature == signature }
        }
        (directCallees + pointerCallees).asSequence()
    }
    .filter { callee -> callee.signature.enclosingClass.file in sourceFiles }

private fun EtsMethod.projectStaticInitializers(
    graph: TsGraph,
    sourceFiles: Set<EtsFileSignature>,
): Sequence<EtsMethod> = cfg.stmts.asSequence()
    .flatMap { it.walkEntities().asSequence() }
    .filterIsInstance<EtsStaticFieldRef>()
    .map { it.field.enclosingClass }
    .filter { it.file in sourceFiles }
    .distinct()
    .mapNotNull { signature ->
        val owner = graph.cp.projectClasses.singleOrNull { it.signature == signature } ?: return@mapNotNull null
        owner.methods.singleOrNull { it.name == STATIC_INIT_METHOD_NAME }
            ?: owner.takeIf { it.name == DEFAULT_ARK_CLASS_NAME }
                ?.methods
                ?.singleOrNull { it.name == DEFAULT_ARK_METHOD_NAME }
    }

internal fun callsIrReadinessIssue(
    method: EtsMethod,
    graph: TsGraph,
    source: String,
): CallsIrReadinessIssue? = callsIrReadinessIssue(
    method = method,
    graph = graph,
    sourceByFile = mapOf(method.signature.enclosingClass.file to source),
)

private fun EtsMethod.irReadinessIssue(
    source: String,
    graph: TsGraph,
): CallsIrReadinessIssue? {
    val statements = cfg.stmts
    val entitiesByStatement = statements.associateWith { statement -> statement.walkEntities() }
    val intrinsicRegexAllocation = entitiesByStatement.values.asSequence().flatten()
        .filterIsInstance<EtsNewExpr>()
        .any { allocation ->
            val type = allocation.type as? EtsClassType ?: return@any false
            val intrinsicOwner = type.signature.file == EtsFileSignature.UNKNOWN ||
                graph.cp.sdkClasses.any { owner -> owner.signature == type.signature }
            type.signature.name == "RegExp" && intrinsicOwner
        }
    if (intrinsicRegexAllocation) {
        return issue(
            reasonCode = CallsSymbolicPreflightReasonCode.REGEX_LITERAL_UNSUPPORTED,
            diagnostic = "Reachable EtsIR allocates an intrinsic RegExp without a runtime model in $name",
        )
    }

    val rawNodes = buildList {
        entitiesByStatement.forEach { (statement, statementEntities) ->
            val sourceSnippet = statement.sourceSnippet(source)
            if (statement is EtsRawStmt) {
                add(RawNode(kind = statement.kind, extra = statement.extra, sourceSnippet = sourceSnippet))
            }
            statementEntities.filterIsInstance<EtsRawEntity>().forEach { entity ->
                add(RawNode(kind = entity.kind, extra = entity.extra, sourceSnippet = sourceSnippet))
            }
        }
    }
    rawNodes.firstOrNull(RawNode::isDestructuring)?.let { raw ->
        return raw.issue(CallsSymbolicPreflightReasonCode.DESTRUCTURING_UNSUPPORTED, name)
    }
    rawNodes.firstOrNull(RawNode::isSpread)?.let { raw ->
        return raw.issue(CallsSymbolicPreflightReasonCode.SPREAD_UNSUPPORTED, name)
    }
    rawNodes.firstOrNull(RawNode::isRegexLiteral)?.let { raw ->
        return raw.issue(CallsSymbolicPreflightReasonCode.REGEX_LITERAL_UNSUPPORTED, name)
    }
    rawNodes.firstOrNull()?.let { raw ->
        return raw.issue(CallsSymbolicPreflightReasonCode.RAW_ENTITY_UNSUPPORTED, name)
    }

    return null
}

private fun EtsStmt.sourceSnippet(source: String): String {
    val origin = location.origin ?: return ""
    if (origin.startOffset !in 0..source.length || origin.endOffset !in origin.startOffset..source.length) return ""

    return source.substring(origin.startOffset, origin.endOffset)
}

private fun EtsStmt.walkEntities(): List<EtsEntity> {
    val result = mutableListOf<EtsEntity>()
    val pending = ArrayDeque<EtsEntity>()
    getOperands().forEach(pending::addLast)
    while (pending.isNotEmpty()) {
        val entity = pending.removeFirst()
        result += entity
        entity.getOperands().forEach(pending::addLast)
    }

    return result
}

private data class RawNode(
    val kind: String,
    val extra: Map<String, Any>,
    val sourceSnippet: String,
) {
    private val normalizedDescription: String = sequenceOf(kind)
        .plus(extra.entries.sortedBy { (key, _) -> key }.flatMap { (key, value) -> sequenceOf(key, value.toString()) })
        .plus(sourceSnippet)
        .joinToString(separator = " ")
        .lowercase()

    fun matches(vararg markers: String): Boolean = markers.any(normalizedDescription::contains)

    fun isDestructuring(): Boolean = matches("destruct", "bindingpattern", "binding pattern") ||
        DESTRUCTURING_DECLARATION.containsMatchIn(sourceSnippet) ||
        DESTRUCTURING_ASSIGNMENT.containsMatchIn(sourceSnippet)

    fun isSpread(): Boolean = matches("spread") || sourceSnippet.contains("...")

    fun isRegexLiteral(): Boolean = matches("regex", "regexp", "regularexpression", "regular expression") ||
        REGEX_LITERAL.containsMatchIn(sourceSnippet)

    fun issue(reasonCode: CallsSymbolicPreflightReasonCode, methodName: String): CallsIrReadinessIssue = issue(
        reasonCode = reasonCode,
        diagnostic = "Reachable EtsIR contains unsupported raw node kind $kind in $methodName",
    )
}

private val DESTRUCTURING_DECLARATION = Regex("""\b(?:const|let|var)\s*[\[{]""")
private val DESTRUCTURING_ASSIGNMENT = Regex("""(?:^|[;{}])\s*[\[{][^=]*[\]}]\s*=""")
private val REGEX_LITERAL = Regex("""/(?:\\.|[^/\r\n])+/[a-z]*""")

private fun issue(
    reasonCode: CallsSymbolicPreflightReasonCode,
    diagnostic: String,
): CallsIrReadinessIssue = CallsIrReadinessIssue(
    reasonCode = reasonCode,
    diagnostic = diagnostic,
)
