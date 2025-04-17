package org.usvm.dataflow.ts.infer

import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsFieldRef
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.utils.callExpr
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.usvm.dataflow.ifds.FlowFunction
import org.usvm.dataflow.ifds.FlowFunctions
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.infer.BackwardTypeDomainFact.TypedVariable
import org.usvm.dataflow.ts.infer.BackwardTypeDomainFact.Zero
import org.usvm.dataflow.ts.util.fixAnyToUnknown
import org.usvm.util.Maybe

private val logger = KotlinLogging.logger {}

class BackwardFlowFunctions(
    val graph: EtsApplicationGraph,
    val dominators: (EtsMethod) -> GraphDominators<EtsStmt>,
    val savedTypes: MutableMap<EtsType, MutableList<EtsTypeFact>>,
    val doAddKnownTypes: Boolean = true,
) : FlowFunctions<BackwardTypeDomainFact, EtsMethod, EtsStmt> {

    private val typeProcessor = TypeFactProcessor(graph.cp)

    // private val aliasesCache: MutableMap<EtsMethod, Map<EtsStmt, Pair<AliasInfo, AliasInfo>>> = hashMapOf()
    //
    // private fun getAliases(method: EtsMethod): Map<EtsStmt, Pair<AliasInfo, AliasInfo>> {
    //     return aliasesCache.computeIfAbsent(method) { computeAliases(method) }
    // }

    override fun obtainPossibleStartFacts(method: EtsMethod) = listOf(Zero)

    override fun obtainSequentFlowFunction(
        current: EtsStmt,
        next: EtsStmt,
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        if (current is EtsAssignStmt) {
            val lhvPath = current.lhv.toPathOrNull()
            val rhvPath = current.rhv.toPathOrNull()
            if (lhvPath != null && rhvPath != null && lhvPath == rhvPath) {
                return@FlowFunction listOf(fact)
            }
        }
        when (fact) {
            Zero -> sequentZero(current)
            is TypedVariable -> sequent(current, fact).myFilter()
        }
    }

    private fun TypedVariable.withTypeGuards(current: EtsStmt): TypedVariable {
        val dominators = dominators(current.method).dominators(current).asReversed()

        var result = this

        for (stmt in dominators.filterIsInstance<EtsIfStmt>()) {
            val (guardedVariable, typeGuard) = resolveTypeGuard(stmt) ?: continue

            if (guardedVariable != result.variable) continue

            val branches = graph.predecessors(stmt).toList() // graph is reversed
            check(branches.size == 2) { "Unexpected IF branches: $branches" }

            val (falseBranch, trueBranch) = branches

            val isTrueStatement = current.isReachableFrom(trueBranch)
            val isFalseStatement = current.isReachableFrom(falseBranch)

            if (isTrueStatement && !isFalseStatement) {
                val type = result.type.withGuard(typeGuard, guardNegated = false)
                result = TypedVariable(result.variable, type)
            }

            if (!isTrueStatement && isFalseStatement) {
                val type = result.type.withGuard(typeGuard, guardNegated = true)
                result = TypedVariable(result.variable, type)
            }
        }

        return result
    }

    private fun resolveTypeGuard(branch: EtsIfStmt): Pair<AccessPathBase, EtsTypeFact.BasicType>? {
        val condition = branch.condition as? EtsEqExpr ?: return null

        if (condition.right == EtsNumberConstant(0.0)) {
            return resolveTypeGuard(condition.left, branch)
        }

        return null
    }

    private fun resolveTypeGuard(
        value: EtsEntity,
        stmt: EtsStmt,
    ): Pair<AccessPathBase, EtsTypeFact.BasicType>? {
        val valueAssignment = findAssignment(value, stmt) ?: return null

        return when (val rhv = valueAssignment.rhv) {
            is EtsLocal, is EtsThis, is EtsParameterRef, is EtsFieldRef, is EtsArrayAccess -> {
                resolveTypeGuard(rhv, valueAssignment)
            }

            is EtsInExpr -> {
                resolveTypeGuardFromIn(rhv.left, rhv.right)
            }

            else -> null
        }
    }

    private fun findAssignment(value: EtsEntity, stmt: EtsStmt): EtsAssignStmt? {
        val cache = hashMapOf<EtsStmt, Maybe<EtsAssignStmt>>()
        findAssignment(value, stmt, cache)
        val maybeValue = cache.getValue(stmt)

        return if (maybeValue.isNone) null else maybeValue.getOrThrow()
    }

    private fun findAssignment(
        value: EtsEntity,
        stmt: EtsStmt,
        cache: MutableMap<EtsStmt, Maybe<EtsAssignStmt>>,
    ) {
        if (stmt in cache) return

        if (stmt is EtsAssignStmt && stmt.lhv == value) {
            cache[stmt] = Maybe.some(stmt)
            return
        }

        // val predecessors = graph.successors(stmt) // graph is reversed
        // val predecessors = dominators(stmt.method).dominators(stmt) - stmt
        val predecessors = dominators(stmt.method).dominators(stmt).toSet().intersect(graph.successors(stmt).toSet())
        predecessors.forEach { findAssignment(value, it, cache) }

        val predecessorValues = predecessors.map { cache.getValue(it) }
        if (predecessorValues.any { it.isNone }) {
            cache[stmt] = Maybe.none()
            return
        }

        val values = predecessorValues.map { it.getOrThrow() }.toHashSet()
        if (values.size == 1) {
            cache[stmt] = Maybe.some(values.single())
            return
        }

        cache[stmt] = Maybe.none()
    }

    private fun resolveTypeGuardFromIn(
        left: EtsEntity,
        right: EtsEntity,
    ): Pair<AccessPathBase, EtsTypeFact.BasicType>? {
        if (left !is EtsStringConstant) return null

        check(right is EtsValue)
        val base = right.toBase()
        val type = EtsTypeFact.ObjectEtsTypeFact(
            cls = null,
            properties = mapOf(left.value to EtsTypeFact.UnknownEtsTypeFact)
        )
        return base to type
    }

    private fun EtsStmt.isReachableFrom(stmt: EtsStmt): Boolean {
        val visited = hashSetOf<EtsStmt>()
        val queue = mutableListOf(stmt)

        while (queue.isNotEmpty()) {
            val s = queue.removeLast()
            if (this == s) return true

            if (!visited.add(s)) continue

            val successors = graph.predecessors(s) // graph is reversed
            queue.addAll(successors)
        }

        return false
    }

    private fun sequentZero(current: EtsStmt): List<BackwardTypeDomainFact> {
        val result = mutableListOf<BackwardTypeDomainFact>(Zero)

        // Case `return x`
        // ∅ |= x:unknown
        if (current is EtsReturnStmt) {
            val returnValue = current.returnValue
            if (returnValue != null) {
                val variable = returnValue.toBase()
                val type = if (doAddKnownTypes) {
                    val knownType = returnValue.tryGetKnownType(current.method)
                    EtsTypeFact.from(knownType).fixAnyToUnknown()
                } else {
                    EtsTypeFact.UnknownEtsTypeFact
                }
                result += TypedVariable(variable, type)
            }
        }

        if (current is EtsAssignStmt) {
            val rhv = when (val r = current.rhv) {
                is EtsLocal -> r.toPath()
                is EtsThis -> r.toPath()
                is EtsParameterRef -> r.toPath()
                is EtsFieldRef -> r.toPath()
                is EtsArrayAccess -> r.toPath()
                else -> {
                    // logger.info { "TODO backward assign zero: $current" }
                    null
                }
            }

            // When RHS is not const-like, handle possible new facts for RHV:
            if (rhv != null) {
                val y = rhv.base

                if (rhv.accesses.isEmpty()) {
                    // Case `x... := y`
                    // ∅ |= y:unknown
                    val type = if (doAddKnownTypes) {
                        val knownType = current.rhv.tryGetKnownType(current.method)
                        EtsTypeFact.from(knownType).fixAnyToUnknown()
                    } else {
                        EtsTypeFact.UnknownEtsTypeFact
                    }
                    result += TypedVariable(y, type)
                } else {
                    // Case `x := y.f`  OR  `x := y[i]`

                    check(rhv.accesses.size == 1)
                    when (val accessor = rhv.accesses.single()) {
                        // Case `x := y.f`
                        // ∅ |= y:{f:unknown}
                        is FieldAccessor -> {
                            val type = EtsTypeFact.ObjectEtsTypeFact(
                                cls = null,
                                properties = mapOf(accessor.name to EtsTypeFact.UnknownEtsTypeFact)
                            )
                            result += TypedVariable(y, type).withTypeGuards(current)
                        }

                        // Case `x := y[i]`
                        // ∅ |= y:Array<unknown>
                        is ElementAccessor -> {
                            // Note: ElementAccessor guarantees that `y` is an array,
                            //       since `y[i]` for property access (i.e. access property
                            //       with name "i") is represented via FieldAccessor.
                            val type = EtsTypeFact.ArrayEtsTypeFact(
                                elementType = EtsTypeFact.UnknownEtsTypeFact
                            )
                            result += TypedVariable(y, type).withTypeGuards(current)
                        }
                    }
                }
            }

            val lhv = current.lhv.toPath()

            // Handle new possible facts for LHS:
            if (lhv.accesses.isNotEmpty()) {
                // Case `x.f := y`  OR  `x[i] := y`
                val x = lhv.base

                when (val a = lhv.accesses.single()) {
                    // Case `x.f := y`
                    // ∅ |= x:{f:unknown}
                    is FieldAccessor -> {
                        val type = EtsTypeFact.ObjectEtsTypeFact(
                            cls = null,
                            properties = mapOf(a.name to EtsTypeFact.UnknownEtsTypeFact)
                        )
                        result += TypedVariable(x, type).withTypeGuards(current)
                    }

                    // Case `x[i] := y`
                    // ∅ |= x:Array<unknown>
                    is ElementAccessor -> {
                        // Note: ElementAccessor guarantees that `y` is an array,
                        //       since `y[i]` for property access (i.e. access property
                        //       with name "i") is represented via FieldAccessor.
                        val type = EtsTypeFact.ArrayEtsTypeFact(
                            elementType = EtsTypeFact.UnknownEtsTypeFact
                        )
                        result += TypedVariable(x, type)
                    }
                }
            }
        }

        return result
    }

    private fun sequent(
        current: EtsStmt,
        fact: TypedVariable,
    ): List<TypedVariable> {
        if (current !is EtsAssignStmt) {
            return listOf(fact)
        }

        val lhv = current.lhv.toPath()

        val rhv = when (val r = current.rhv) {
            is EtsLocal -> r.toPath()
            is EtsThis -> r.toPath()
            is EtsParameterRef -> r.toPath()
            is EtsFieldRef -> r.toPath()
            is EtsArrayAccess -> r.toPath()
            is EtsCastExpr -> r.toPath()
            is EtsNewExpr -> {
                // TODO: what about `x.f := new T()` ?
                // `x := new T()` with fact `x:U` => `saved[T] += U`
                if (fact.variable == lhv.base) {
                    savedTypes.getOrPut(r.type) { mutableListOf() }.add(fact.type)
                }
                return listOf(fact)
            }

            else -> {
                // logger.info { "TODO backward assign: $current" }
                return listOf(fact)
            }
        }

        // Pass-through completely unrelated facts:
        if (fact.variable != lhv.base) return listOf(fact)

        // val (preAliases, _) = getAliases(current.method)[current]!!

        if (lhv.accesses.isEmpty() && rhv.accesses.isEmpty()) {
            // Case `x := y`

            // x:T |= x:T (keep) + y:T
            val y = rhv.base
            val newFact = TypedVariable(y, fact.type).withTypeGuards(current)
            return listOf(fact, newFact)

        } else if (lhv.accesses.isEmpty()) {
            // Case `x := y.f`  OR  `x := y[i]`

            check(rhv.accesses.size == 1)
            when (val a = rhv.accesses.single()) {
                // Case `x := y.f`
                is FieldAccessor -> {
                    // // Drop facts containing duplicate fields
                    // if (fact.type is EtsTypeFact.ObjectEtsTypeFact && a.name in fact.type.properties) {
                    //     // can just drop?
                    //     return listOf(fact)
                    // }

                    // x:T |= x:T (keep) + y:{f:T} + aliases
                    val result = mutableListOf(fact)
                    val y = rhv.base
                    val type = EtsTypeFact.ObjectEtsTypeFact(
                        cls = null,
                        properties = mapOf(a.name to fact.type)
                    )
                    result += TypedVariable(y, type).withTypeGuards(current)
                    // aliases: +|= z:{f:T}
                    // for (z in preAliases.getAliases(AccessPath(y, emptyList()))) {
                    //     val type2 = unrollAccessorsToTypeFact(z.accesses + a, fact.type)
                    //     result += TypedVariable(z.base, type2).withTypeGuards(current)
                    // }
                    return result
                }

                // Case `x := y[i]`
                is ElementAccessor -> {
                    // x:T |= x:T (keep) + y:Array<T>
                    val y = rhv.base
                    val type = EtsTypeFact.ArrayEtsTypeFact(elementType = fact.type)
                    // val realType = EtsTypeFact.from(current.rhv.type)
                    // val type = newType.intersect(realType) ?: run {
                    //     logger.warn { "Empty intersection of fact and real type: $newType & $realType" }
                    //     newType
                    // }
                    val newFact = TypedVariable(y, type).withTypeGuards(current)
                    return listOf(fact, newFact)
                }
            }

        } else if (rhv.accesses.isEmpty()) {
            // Case `x.f := y`  OR  `x[i] := y`

            check(lhv.accesses.size == 1)
            when (val a = lhv.accesses.single()) {
                // Case `x.f := y`
                is FieldAccessor -> {
                    val facts = mutableListOf(fact)

                    if (fact.type is EtsTypeFact.UnionEtsTypeFact) {
                        val types = fact.type.types.mapNotNull {
                            if (it is EtsTypeFact.ObjectEtsTypeFact) {
                                it.properties[a.name]
                            } else {
                                null
                            }
                        }
                        if (types.isNotEmpty()) {
                            // x:T |= x:T (keep) + y:T
                            val newType = types.reduce { acc, type -> typeProcessor.union(acc, type) }
                            facts += TypedVariable(rhv.base, newType).withTypeGuards(current)
                        }
                        return facts
                    }

                    if (fact.type is EtsTypeFact.IntersectionEtsTypeFact) {
                        for (subType in fact.type.types) {
                            if (subType is EtsTypeFact.ObjectEtsTypeFact) {
                                val propertyType = subType.properties[a.name]
                                if (propertyType != null) {
                                    facts += TypedVariable(rhv.base, propertyType).withTypeGuards(current)
                                }
                            }
                        }
                        return facts
                    }

                    // Ignore (pass) non-object type facts:
                    // x:primitive |= x:primitive (pass)
                    if (fact.type !is EtsTypeFact.ObjectEtsTypeFact) {
                        return facts
                    }

                    // x:{f:T} |= x:{f:T} (keep) + y:T
                    // x:{no f} |= only keep x:{..}
                    val type = fact.type.properties[a.name]
                    if (type != null) {
                        val y = rhv.base
                        facts += TypedVariable(y, type).withTypeGuards(current)
                    }
                    return facts
                }

                // Case `x[i] := y`
                is ElementAccessor -> {
                    val facts = mutableListOf(fact)

                    if (fact.type is EtsTypeFact.UnionEtsTypeFact) {
                        val types = fact.type.types.mapNotNull {
                            if (it is EtsTypeFact.ArrayEtsTypeFact) {
                                it.elementType
                            } else {
                                null
                            }
                        }
                        if (types.isNotEmpty()) {
                            // x:T |= x:T (keep) + y:T
                            val newType = types.reduce { acc, type -> typeProcessor.union(acc, type) }
                            facts += TypedVariable(rhv.base, newType).withTypeGuards(current)
                        }
                        return facts
                    }

                    if (fact.type is EtsTypeFact.IntersectionEtsTypeFact) {
                        for (subType in fact.type.types) {
                            if (subType is EtsTypeFact.ArrayEtsTypeFact) {
                                val elementType = subType.elementType
                                facts += TypedVariable(rhv.base, elementType).withTypeGuards(current)
                            }
                        }
                        return facts
                    }

                    // x:Array<T> |= x:Array<T> (pass)
                    if (fact.type !is EtsTypeFact.ArrayEtsTypeFact) {
                        return facts
                    }

                    // x:Array<T> |= x:Array<T> (keep) + y:T
                    val y = rhv.base
                    val type = fact.type.elementType
                    facts += TypedVariable(y, type).withTypeGuards(current)
                    return facts
                }
            }
        } else {
            error("Incorrect 3AC: $current")
        }
    }

    private fun EtsTypeFact.ObjectEtsTypeFact.removePropertyType(propertyName: String): Pair<EtsTypeFact, EtsTypeFact?> {
        val propertyType = properties[propertyName]
        val updatedThis = EtsTypeFact.ObjectEtsTypeFact(cls, properties - propertyName)
        return updatedThis to propertyType
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> callZero(callStatement)
            is TypedVariable -> call(callStatement, fact)
        }
    }

    private fun callZero(
        callStatement: EtsStmt,
    ): List<BackwardTypeDomainFact> {
        val result = mutableListOf<BackwardTypeDomainFact>(Zero)

        val callExpr = callStatement.callExpr ?: error("No call")

        if (callExpr is EtsInstanceCallExpr) {
            val instance = callExpr.instance
            val path = instance.toBase()
            val objectWithMethod = EtsTypeFact.ObjectEtsTypeFact(
                cls = null,
                properties = mapOf(
                    callExpr.callee.name to EtsTypeFact.FunctionEtsTypeFact
                )
            )
            result += TypedVariable(path, objectWithMethod)
        }

        if (doAddKnownTypes) {
            // f(x:T) |= x:T, where T is the type of the argument in f's signature
            for ((index, arg) in callExpr.args.withIndex()) {
                val param = callExpr.callee.parameters.getOrNull(index) ?: continue
                val base = arg.toBase()
                val type = EtsTypeFact.from(param.type)
                result += TypedVariable(base, type)
            }
        }

        return result
    }

    private fun call(
        callStatement: EtsStmt,
        fact: TypedVariable,
    ): List<TypedVariable> {
        val result = mutableListOf<TypedVariable>()

        val callResult = (callStatement as? EtsAssignStmt)?.lhv?.toBase()
        if (callResult != null) {
            // If fact was for LHS, drop it as overridden
            if (fact.variable == callResult) return result
        }

        result += fact
        return result
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: EtsStmt,
        calleeStart: EtsStmt,
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(fact)
            is TypedVariable -> start(callStatement, calleeStart, fact)
        }
    }

    private fun start(
        callStatement: EtsStmt,
        calleeStart: EtsStmt,
        fact: TypedVariable,
    ): List<TypedVariable> {
        val callResult = (callStatement as? EtsAssignStmt)?.lhv?.toBase() ?: return emptyList()

        if (fact.variable != callResult) return emptyList()

        if (calleeStart is EtsThrowStmt) return emptyList() // TODO support throwStmt

        check(calleeStart is EtsReturnStmt)

        val exitValue = calleeStart.returnValue?.toBase() ?: return emptyList()

        return listOf(TypedVariable(exitValue, fact.type))
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        exitStatement: EtsStmt,
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(fact)
            is TypedVariable -> exit(callStatement, fact)
        }
    }

    private fun exit(
        callStatement: EtsStmt,
        fact: TypedVariable,
    ): List<TypedVariable> {
        val callExpr = callStatement.callExpr ?: error("No call")

        when (fact.variable) {
            is AccessPathBase.This -> {
                if (callExpr !is EtsInstanceCallExpr) {
                    return emptyList()
                }

                val instance = callExpr.instance
                val instancePath = instance.toBase()
                return listOf(TypedVariable(instancePath, fact.type))
            }

            is AccessPathBase.Arg -> {
                val arg = callExpr.args.getOrNull(fact.variable.index)?.toBase() ?: return emptyList()
                return listOf(TypedVariable(arg, fact.type))
            }

            else -> return emptyList()
        }
    }
}

private const val COMPLEXITY_LIMIT = 5

private fun Iterable<TypedVariable>.myFilter(): List<TypedVariable> = filter {
    if (it.type.complexity() >= COMPLEXITY_LIMIT) {
        // logger.warn { "Dropping too complex fact: $it" }
        return@filter false
    }
    true
}

/**
 * Complexity of a type fact is the maximum depth of nested types.
 */
private fun EtsTypeFact.complexity(): Int = when (this) {
    is EtsTypeFact.ObjectEtsTypeFact -> (properties.values.maxOfOrNull { it.complexity() } ?: 0) + 1
    is EtsTypeFact.ArrayEtsTypeFact -> elementType.complexity() + 1
    is EtsTypeFact.UnionEtsTypeFact -> (types.maxOfOrNull { it.complexity() } ?: 0) + 1
    is EtsTypeFact.IntersectionEtsTypeFact -> (types.maxOfOrNull { it.complexity() } ?: 0) + 1
    else -> 0
}
