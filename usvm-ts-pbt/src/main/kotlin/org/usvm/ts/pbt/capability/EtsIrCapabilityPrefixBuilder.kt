package org.usvm.ts.pbt.capability

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.ts.pbt.external.SourceTargetRecord
import java.util.IdentityHashMap

/** Production bridge from an EtsIR CFG/AST to the dependency-neutral slicer. */
object EtsIrCapabilityPrefixBuilder {
    fun build(method: EtsMethod, target: SourceTargetRecord): CapabilityPrefixSlice {
        val statements = method.cfg.stmts
        val indices = IdentityHashMap<EtsStmt, Int>().apply {
            statements.forEachIndexed { index, statement -> put(statement, index) }
        }
        val cfg = CapabilityCfg(
            entryStmtIndex = 0,
            nodes = statements.mapIndexed { index, statement ->
                CapabilityCfgNode(
                    stmtIndex = index,
                    successorStmtIndices = method.cfg.successors(statement).map { successor ->
                        indices[successor] ?: -1
                    }.toList(),
                    facts = EtsIrCapabilityAstScanner.scan(statement),
                )
            },
        )
        return CapabilityPrefixSlicer.slice(
            methodId = target.methodId,
            branchId = target.branchId,
            targetStmtIndex = target.stmtIndex,
            cfg = cfg,
        )
    }
}

/**
 * EtsIR does not expose one common expression visitor. Class names and the IR
 * rendering cover nested expressions, while source-origin node kinds provide
 * additional evidence. Unknown/raw nodes are retained as uncertainty rather
 * than coerced to unsupported.
 */
object EtsIrCapabilityAstScanner {
    private val arithmeticOperator = Regex(
        "(^|[^A-Za-z])(\\+|-|\\*|/|%|<|>|===?|!==?|<=|>=|&&|\\|\\|)([^A-Za-z]|$)",
    )
    private val spread = Regex("spread(element)?|\\.\\.\\.", RegexOption.IGNORE_CASE)
    private val yield = Regex("yield(expr)?", RegexOption.IGNORE_CASE)
    private val iterator = Regex("symbol\\.iterator|iterator|\\.next\\s*\\(", RegexOption.IGNORE_CASE)
    private val mapOrSet = Regex(
        "(^|[^A-Za-z])(map|set)([^A-Za-z]|$)|\\.(get|set|has|delete)\\s*\\(",
        RegexOption.IGNORE_CASE,
    )
    private val builtinCall = Regex(
        "(object|number|math|array|string|json)\\.[A-Za-z]+|prototype\\.[A-Za-z]+\\.call|\\.call\\s*\\(",
        RegexOption.IGNORE_CASE,
    )
    private val arrayOrObject = Regex(
        "array|object|property|field|index(access)?|\\[[^]]*]|\\{[^}]*}",
        RegexOption.IGNORE_CASE,
    )
    private val moduleInit = Regex("import|namespace|module(init)?|file.?init|staticinit", RegexOption.IGNORE_CASE)
    private val pointerCall = Regex(
        "pointer.*(call|invoke)|(dynamic|unknown|unresolved).*(call|invoke)",
        RegexOption.IGNORE_CASE,
    )
    private val call = Regex("call(expr)?|invoke(expr)?|new(expr)?|\\w+\\s*\\(", RegexOption.IGNORE_CASE)
    private val unknown = Regex("unsupported(value)?|raw(entity)?|unknown(expr|value|stmt)?", RegexOption.IGNORE_CASE)

    fun scan(statement: EtsStmt): List<CapabilityAstFact> {
        val className = statement::class.simpleName.orEmpty()
        val sourceNodeKind = statement.location.origin?.nodeKind.orEmpty()
        val rendered = statement.toString()
        val searchable = "$className $sourceNodeKind $rendered"
        val facts = mutableListOf<CapabilityAstFact>()

        fun add(kind: String, evidence: String, proven: Boolean = true) {
            facts += CapabilityAstFact(kind, evidence, proven)
        }

        when {
            yield.containsMatchIn(searchable) -> add(CapabilityAstKind.SPREAD_YIELD, "etsir:yield")
            spread.containsMatchIn(searchable) -> add(CapabilityAstKind.SPREAD_YIELD, "etsir:spread")
        }
        if (iterator.containsMatchIn(searchable) && !yield.containsMatchIn(searchable)) {
            add(CapabilityAstKind.ITERATOR, "etsir:iterator")
        }
        if (pointerCall.containsMatchIn(searchable)) {
            add(CapabilityAstKind.UNRESOLVED_POINTER_CALL, "etsir:pointer-call")
        } else if (call.containsMatchIn(className) || className.contains("Call", ignoreCase = true)) {
            add(CapabilityAstKind.CALLABLE, "etsir:call")
        }
        if (builtinCall.containsMatchIn(searchable)) add(CapabilityAstKind.BUILTIN_CALL, "etsir:builtin-call")
        if (mapOrSet.containsMatchIn(searchable)) add(CapabilityAstKind.MAP_SET, "etsir:map-set")
        if (arrayOrObject.containsMatchIn(searchable)) add(CapabilityAstKind.ARRAY_OBJECT, "etsir:array-object")
        if (moduleInit.containsMatchIn(searchable)) add(CapabilityAstKind.MODULE_INIT, "etsir:module-init")
        if (arithmeticOperator.containsMatchIn(rendered)) {
            add(CapabilityAstKind.PRIMITIVE_ARITHMETIC, "etsir:primitive-operator")
        }
        if (unknown.containsMatchIn(searchable) && facts.none { it.kind == CapabilityAstKind.SPREAD_YIELD }) {
            add(CapabilityAstKind.UNKNOWN, "etsir:unknown-node", proven = false)
        }
        return facts.canonicalFacts()
    }
}
