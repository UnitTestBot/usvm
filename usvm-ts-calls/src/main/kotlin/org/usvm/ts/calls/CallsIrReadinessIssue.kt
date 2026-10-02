package org.usvm.ts.calls

import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsFieldRef
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsLexicalEnvType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsRawEntity
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.utils.getDeclaredLocals
import org.jacodb.ets.utils.getOperands
import org.usvm.machine.TsGraph

internal data class CallsIrReadinessIssue(
    val reasonCode: CallsSymbolicPreflightReasonCode,
    val diagnostic: String,
)

internal fun callsIrReadinessIssue(
    method: EtsMethod,
    graph: TsGraph,
    source: String,
    admittedLexicalEnvironment: EtsLexicalEnvType?,
): CallsIrReadinessIssue? {
    val sourceFile = method.signature.enclosingClass.file
    val pending = ArrayDeque<EtsMethod>().apply { addLast(method) }
    val visited = hashSetOf<EtsMethod>()

    while (pending.isNotEmpty()) {
        val currentMethod = pending.removeFirst()
        if (!visited.add(currentMethod)) continue

        currentMethod.irReadinessIssue(
            source = source,
            admittedLexicalEnvironment = admittedLexicalEnvironment,
        )?.let { issue -> return issue }

        currentMethod.sameFileCallees(graph = graph, sourceFile = sourceFile)
            .forEach(pending::addLast)
    }

    return null
}

private fun EtsMethod.sameFileCallees(
    graph: TsGraph,
    sourceFile: EtsFileSignature,
): Sequence<EtsMethod> = cfg.stmts.asSequence()
    .flatMap { statement ->
        runCatching { graph.callees(statement).toList() }
            .getOrDefault(emptyList())
            .asSequence()
    }
    .filter { callee -> callee.signature.enclosingClass.file == sourceFile }

private fun EtsMethod.irReadinessIssue(
    source: String,
    admittedLexicalEnvironment: EtsLexicalEnvType?,
): CallsIrReadinessIssue? {
    val statements = cfg.stmts
    val entitiesByStatement = statements.associateWith { statement -> statement.walkEntities() }
    val entities = entitiesByStatement.values.flatten()

    if (hasUnsupportedLexicalEnvironment(entities, admittedLexicalEnvironment)) {
        return issue(
            reasonCode = CallsSymbolicPreflightReasonCode.LEXICAL_ENVIRONMENT_UNSUPPORTED,
            diagnostic = "Reachable EtsIR contains a runtime lexical environment in $name",
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

    if (entities.any(::isPrototypeAccess)) {
        return issue(
            reasonCode = CallsSymbolicPreflightReasonCode.PROTOTYPE_ACCESS_UNSUPPORTED,
            diagnostic = "Reachable EtsIR accesses a prototype property in $name",
        )
    }

    if (entities.any(::requiresSymbolicNumberToString)) {
        return issue(
            reasonCode = CallsSymbolicPreflightReasonCode.SYMBOLIC_NUMBER_TO_STRING_UNSUPPORTED,
            diagnostic = "Reachable EtsIR concatenates a string with a symbolic number in $name",
        )
    }

    return null
}

private fun EtsMethod.hasUnsupportedLexicalEnvironment(
    entities: List<EtsEntity>,
    admittedLexicalEnvironment: EtsLexicalEnvType?,
): Boolean {
    if (getDeclaredLocals().any { local ->
            val type = local.type as? EtsLexicalEnvType
            type != null && type != admittedLexicalEnvironment
        }
    ) {
        return true
    }

    return entities.any { entity ->
        val functionType = entity.type as? EtsFunctionType ?: return@any false
        val environment = functionType.signature.parameters.firstOrNull()?.type as? EtsLexicalEnvType
        environment != null && environment != admittedLexicalEnvironment
    }
}

private fun org.jacodb.ets.model.EtsStmt.sourceSnippet(source: String): String {
    val origin = location.origin ?: return ""
    if (origin.startOffset !in 0..source.length || origin.endOffset !in origin.startOffset..source.length) return ""

    return source.substring(origin.startOffset, origin.endOffset)
}

private fun org.jacodb.ets.model.EtsStmt.walkEntities(): List<EtsEntity> {
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

private fun isPrototypeAccess(entity: EtsEntity): Boolean =
    entity is EtsFieldRef && entity.field.name == "prototype"

private fun requiresSymbolicNumberToString(entity: EtsEntity): Boolean {
    val addition = entity as? EtsAddExpr ?: return false
    if (addition.type != EtsStringType) return false

    val symbolicNumberOnLeft = addition.left.type == EtsNumberType && addition.left !is EtsNumberConstant
    val symbolicNumberOnRight = addition.right.type == EtsNumberType && addition.right !is EtsNumberConstant
    return symbolicNumberOnLeft || symbolicNumberOnRight
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

    fun isRegexLiteral(): Boolean = matches("regex", "regexp", "regular expression") ||
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
