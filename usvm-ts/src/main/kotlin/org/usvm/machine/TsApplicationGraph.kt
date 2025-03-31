package org.usvm.machine

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsTerminatingStmt
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.UNKNOWN_FILE_NAME
import org.jacodb.ets.utils.callExpr
import org.usvm.dataflow.graph.ApplicationGraph
import org.usvm.util.Maybe
import org.usvm.util.onSome

private val logger = mu.KotlinLogging.logger {}

interface TsApplicationGraph : ApplicationGraph<EtsMethod, EtsStmt> {
    val cp: EtsScene
}

private fun EtsFileSignature?.isUnknown(): Boolean =
    this == null || fileName.isBlank() || fileName == UNKNOWN_FILE_NAME

private fun EtsClassSignature.isUnknown(): Boolean =
    name.isBlank()

private fun EtsClassSignature.isIdeal(): Boolean =
    !isUnknown() && !file.isUnknown()

enum class ComparisonResult {
    Equal,
    NotEqual,
    Unknown,
}

fun compareFileSignatures(
    sig1: EtsFileSignature?,
    sig2: EtsFileSignature?,
): ComparisonResult = when {
    sig1.isUnknown() -> ComparisonResult.Unknown
    sig2.isUnknown() -> ComparisonResult.Unknown
    sig1?.fileName == sig2?.fileName -> ComparisonResult.Equal
    else -> ComparisonResult.NotEqual
}

fun compareClassSignatures(
    sig1: EtsClassSignature,
    sig2: EtsClassSignature,
): ComparisonResult = when {
    sig1.isUnknown() -> ComparisonResult.Unknown
    sig2.isUnknown() -> ComparisonResult.Unknown
    sig1.name == sig2.name -> compareFileSignatures(sig1.file, sig2.file)
    else -> ComparisonResult.NotEqual
}

class TsApplicationGraphImpl(
    override val cp: EtsScene,
) : TsApplicationGraph {

    override fun predecessors(node: EtsStmt): Sequence<EtsStmt> {
        // val graph = node.location.method.cfg
        // val predecessors = graph.predecessors(node)
        // return predecessors.asSequence()
        TODO()
    }

    override fun successors(node: EtsStmt): Sequence<EtsStmt> {
        val graph = node.location.method.cfg
        val successors = graph.successors(node)
        return successors.asSequence()
    }

    private val projectClassesBySignature by lazy {
        cp.projectAndSdkClasses.groupByTo(hashMapOf()) { it.signature }
    }
    private val projectClassesByName by lazy {
        cp.projectAndSdkClasses
            .filter { !it.signature.isUnknown() }
            .groupByTo(hashMapOf()) { it.name }
    }
    private val projectMethodsByName by lazy {
        cp.projectAndSdkClasses
            .flatMap { it.methods }
            .groupByTo(hashMapOf()) { it.name }
    }

    private val cacheClassWithIdealSignature: MutableMap<EtsClassSignature, Maybe<EtsClass>> = hashMapOf()
    private val cacheMethodWithIdealSignature: MutableMap<EtsMethodSignature, Maybe<EtsMethod>> = hashMapOf()
    private val cachePartiallyMatchedCallees: MutableMap<EtsMethodSignature, List<EtsMethod>> = hashMapOf()

    private fun lookupClassWithIdealSignature(signature: EtsClassSignature): Maybe<EtsClass> {
        require(signature.isIdeal())

        if (signature in cacheClassWithIdealSignature) {
            return cacheClassWithIdealSignature.getValue(signature)
        }

        val matched = projectClassesBySignature[signature].orEmpty()
        if (matched.isEmpty()) {
            cacheClassWithIdealSignature[signature] = Maybe.none()
            return Maybe.none()
        } else {
            val s = matched.singleOrNull()
                ?: error("Multiple classes with the same signature: $matched")
            cacheClassWithIdealSignature[signature] = Maybe.some(s)
            return Maybe.some(s)
        }
    }

    override fun callees(node: EtsStmt): Sequence<EtsMethod> {
        val expr = node.callExpr ?: return emptySequence()

        val callee = expr.callee

        // Note: the resolving code below expects that at least the current method signature is known.
        check(node.location.method.signature.enclosingClass.isIdeal()) {
            "Incomplete signature in method: ${node.location.method}"
        }

        // Note: specific resolve for constructor:
        if (callee.name == CONSTRUCTOR_NAME) {
            if (!callee.enclosingClass.isIdeal()) {
                val prevStmt = predecessors(node).singleOrNull()
                if (prevStmt == null) {
                    // Constructor call is the first statement in the method.
                    // We can't resolve it without the class signature.
                    return emptySequence()
                }

                if (prevStmt is EtsAssignStmt && prevStmt.rhv is EtsNewExpr) {
                    val cls = (prevStmt.rhv as EtsNewExpr).type
                    if (cls !is EtsClassType) {
                        return emptySequence()
                    }

                    val sig = cls.signature
                    if (sig.isIdeal()) {
                        lookupClassWithIdealSignature(sig).onSome { c ->
                            return sequenceOf(c.ctor)
                        }
                    } else {
                        if (!sig.isUnknown()) {
                            val resolved = projectClassesByName[sig.name].orEmpty()
                                .singleOrNull { compareClassSignatures(it.signature, sig) != ComparisonResult.NotEqual }
                            if (resolved != null) {
                                return sequenceOf(resolved.ctor)
                            }
                        }
                    }
                }

                // Constructor signature is garbage. Sorry, can't do anything in such case.
                return emptySequence()
            }

            // Here, we assume that the constructor signature is ideal.
            check(callee.enclosingClass.isIdeal())

            val cls = lookupClassWithIdealSignature(callee.enclosingClass)
            if (cls.isSome) {
                return sequenceOf(cls.getOrThrow().ctor)
            } else {
                return emptySequence()
            }
        }

        // If the callee signature is ideal, resolve it directly:
        if (callee.enclosingClass.isIdeal()) {
            if (callee in cacheMethodWithIdealSignature) {
                val resolved = cacheMethodWithIdealSignature.getValue(callee)
                if (resolved.isSome) {
                    return sequenceOf(resolved.getOrThrow())
                } else {
                    return emptySequence()
                }
            }

            val cls = lookupClassWithIdealSignature(callee.enclosingClass)

            val resolved = run {
                if (cls.isNone) {
                    emptySequence()
                } else {
                    cls.getOrThrow().methods.asSequence().filter { it.name == callee.name }
                }
            }
            if (resolved.none()) {
                cacheMethodWithIdealSignature[callee] = Maybe.none()
                return emptySequence()
            }
            val r = resolved.singleOrNull()
                ?: error("Multiple methods with the same complete signature: ${resolved.toList()}")
            cacheMethodWithIdealSignature[callee] = Maybe.some(r)
            return sequenceOf(r)
        }

        // If the callee signature is not ideal, resolve it via a partial match...
        check(!callee.enclosingClass.isIdeal())

        val cls = lookupClassWithIdealSignature(node.location.method.signature.enclosingClass).let {
            if (it.isNone) {
                error("Could not find the enclosing class: ${node.location.method.signature.enclosingClass}")
            }
            it.getOrThrow()
        }

        // If the complete signature match failed,
        // try to find the unique not-the-same neighbour method in the same class:
        val neighbors = cls.methods
            .asSequence()
            .filter { it.name == callee.name }
            .filterNot { it.name == node.location.method.name }
            .toList()
        if (neighbors.isNotEmpty()) {
            val s = neighbors.singleOrNull()
                ?: error("Multiple methods with the same name: $neighbors")
            cachePartiallyMatchedCallees[callee] = listOf(s)
            return sequenceOf(s)
        }

        // NOTE: cache lookup MUST be performed AFTER trying to match the neighbour!
        if (callee in cachePartiallyMatchedCallees) {
            return cachePartiallyMatchedCallees.getValue(callee).asSequence()
        }

        // If the neighbour match failed,
        // try to *uniquely* resolve the callee via a partial signature match:
        val resolved = projectMethodsByName[callee.name].orEmpty()
            .asSequence()
            .filter {
                compareClassSignatures(
                    it.signature.enclosingClass,
                    callee.enclosingClass
                ) != ComparisonResult.NotEqual
            }
            // Note: exclude current class:
            .filterNot {
                compareClassSignatures(
                    it.signature.enclosingClass,
                    node.location.method.signature.enclosingClass
                ) != ComparisonResult.NotEqual
            }
            .toList()
        if (resolved.isEmpty()) {
            cachePartiallyMatchedCallees[callee] = emptyList()
            return emptySequence()
        }
        val r = resolved.singleOrNull() ?: run {
            logger.warn { "Multiple methods with the same partial signature '$callee': $resolved" }
            cachePartiallyMatchedCallees[callee] = emptyList()
            return emptySequence()
        }
        cachePartiallyMatchedCallees[callee] = listOf(r)
        return sequenceOf(r)
    }

    override fun callers(method: EtsMethod): Sequence<EtsStmt> {
        // Note: currently, nobody uses `callers`, so if is safe to disable it for now.
        // Note: comparing methods by signature may be incorrect, and comparing only by name fails for constructors.
        TODO("disabled for now, need re-design")
        // return cp.classes.asSequence()
        //     .flatMap { it.methods }
        //     .flatMap { it.cfg.instructions }
        //     .filterIsInstance<TsCallStmt>()
        //     .filter { it.expr.method == method.signature }
    }

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> {
        return method.cfg.stmts.asSequence().take(1)
    }

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> {
        return method.cfg.stmts.asSequence().filter { it is EtsTerminatingStmt }
    }

    override fun methodOf(node: EtsStmt): EtsMethod {
        return node.location.method
    }
}

class TsGraph(scene: EtsScene) : org.usvm.statistics.ApplicationGraph<EtsMethod, EtsStmt> {
    private val graph = TsApplicationGraphImpl(scene)

    /*override*/ val cp: EtsScene
        get() = graph.cp

    override fun predecessors(node: EtsStmt): Sequence<EtsStmt> =
        graph.predecessors(node)

    override fun successors(node: EtsStmt): Sequence<EtsStmt> =
        if (node is TsMethodCall) {
            graph.successors(node.returnSite)
        } else {
            graph.successors(node)
        }

    override fun callees(node: EtsStmt): Sequence<EtsMethod> =
        graph.callees(node)

    override fun callers(method: EtsMethod): Sequence<EtsStmt> =
        graph.callers(method)

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> =
        graph.entryPoints(method)

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> =
        graph.exitPoints(method)

    override fun methodOf(node: EtsStmt): EtsMethod =
        graph.methodOf(node)

    override fun statementsOf(method: EtsMethod): Sequence<EtsStmt> =
        method.cfg.stmts.asSequence()
}
