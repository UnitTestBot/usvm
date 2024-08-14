package org.usvm.dataflow.ts.infer

import mu.KotlinLogging
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsEqExpr
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsInExpr
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsLValue
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsRef
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.callExpr
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.usvm.dataflow.ifds.FieldAccessor
import org.usvm.dataflow.ifds.FlowFunction
import org.usvm.dataflow.ifds.FlowFunctions
import org.usvm.dataflow.ifds.Maybe
import org.usvm.dataflow.ts.infer.BackwardTypeDomainFact.TypedVariable
import org.usvm.dataflow.ts.infer.BackwardTypeDomainFact.Zero

private val logger = KotlinLogging.logger {}

class BackwardFlowFunction(
    val graph: ApplicationGraph<EtsMethod, EtsStmt>,
    val dominators: (EtsMethod) -> GraphDominators<EtsStmt>,
) : FlowFunctions<BackwardTypeDomainFact, EtsMethod, EtsStmt> {
    override fun obtainPossibleStartFacts(method: EtsMethod) = listOf(Zero)

    override fun obtainSequentFlowFunction(
        current: EtsStmt,
        next: EtsStmt,
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> sequentZero(current)
            is TypedVariable -> sequentFact(current, fact)
        }.map { it.fixThis() }
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

    private fun resolveTypeGuard(value: EtsEntity, stmt: EtsStmt): Pair<AccessPathBase, EtsTypeFact.BasicType>? {
        val valueAssignment = findAssignment(value, stmt) ?: return null

        return when (val rhv = valueAssignment.rhv) {
            is EtsRef, is EtsLValue -> {
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

        val predecessors = graph.successors(stmt) // graph is reversed
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

        if (current is EtsReturnStmt) {
            val variable = current.returnValue?.toBase()
            if (variable != null) {
                result += TypedVariable(variable, EtsTypeFact.UnknownEtsTypeFact)
            }
        }

        if (current is EtsAssignStmt) {
            val rhv = when (val r = current.rhv) {
                is EtsRef -> r.toPath()
                is EtsLValue -> r.toPath()
                else -> {
                    // logger.info { "TODO backward assign zero: $current" }
                    null
                }
            }

            if (rhv != null) {
                if (rhv.accesses.isEmpty()) {
                    result += TypedVariable(rhv.base, EtsTypeFact.UnknownEtsTypeFact)
                } else {
                    val accessor = rhv.accesses.single()

                    if (accessor !is FieldAccessor) {
                        // TODO("$accessor")
                        return result
                    }

                    val type = EtsTypeFact.ObjectEtsTypeFact(
                        cls = null,
                        properties = mapOf(accessor.name to EtsTypeFact.UnknownEtsTypeFact)
                    )

                    result += TypedVariable(rhv.base, type).withTypeGuards(current)
                }
            }

            val lhv = when (val r = current.lhv) {
                is EtsRef -> r.toPath()
                is EtsLValue -> r.toPath()
                else -> {
                // logger.info { "TODO backward assign zero: $current" }
                null
            }
            }

            if (lhv != null) {
                if (lhv.accesses.isEmpty()) {
                    // result += TypedVariable(lhv.base, EtsTypeFact.UnknownEtsTypeFact)
                } else {
                    val accessor = lhv.accesses.single()

                    if (accessor !is FieldAccessor) {
                        // TODO("$accessor")
                        return result
                    }

                    val type = EtsTypeFact.ObjectEtsTypeFact(
                        cls = null,
                        properties = mapOf(accessor.name to EtsTypeFact.UnknownEtsTypeFact)
                    )

                    result += TypedVariable(lhv.base, type).withTypeGuards(current)
                }
            }
        }

        return result
    }

    private fun sequentFact(
        current: EtsStmt,
        fact: TypedVariable,
    ): List<BackwardTypeDomainFact> {
        // println("sequentFact(current = $current, fact = $fact)")

        if (current !is EtsAssignStmt) {
            return listOf(fact)
        }

        val lhv = current.lhv.toPath()

        val rhv = when (val r = current.rhv) {
            is EtsRef -> r.toPath()
            is EtsLValue -> r.toPath()
            is EtsCastExpr -> r.toPath()
            else -> {
                // logger.info { "TODO backward assign: $current" }
                return listOf(fact)
            }
        }

        // Pass-through completely unrelated facts
        if (fact.variable != lhv.base) return listOf(fact)

        // Case `x := y`
        if (lhv.accesses.isEmpty() && rhv.accesses.isEmpty()) {
            return listOf(TypedVariable(rhv.base, fact.type).withTypeGuards(current))
        }

        // Case `x := y.f`
        if (lhv.accesses.isEmpty()) {
            val rhvAccessor = rhv.accesses.single()

            if (rhvAccessor !is FieldAccessor) {
                // TODO("$rhvAccessor")
                return listOf(fact)
            }

            if (fact.type is EtsTypeFact.ObjectEtsTypeFact && rhvAccessor.name in fact.type.properties) {
                // can just drop?
                return listOf(fact)
            }

            val rhvType = EtsTypeFact.ObjectEtsTypeFact(
                cls = null,
                properties = mapOf(rhvAccessor.name to fact.type)
            )
            return listOf(TypedVariable(rhv.base, rhvType).withTypeGuards(current))
        }

        // Case `x.f := y`
        check(lhv.accesses.isNotEmpty() && rhv.accesses.isEmpty()) {
            "Unexpected non-three address code: $current"
        }
        val lhvAccessor = lhv.accesses.single()

        if (lhvAccessor !is FieldAccessor) {
            // TODO("$lhvAccessor")
            return listOf(fact)
        }

        // todo: check fact has object type
        val (typeWithoutProperty, propertyType) = fact.type.removePropertyType(lhvAccessor.name)

        val updatedFact = TypedVariable(fact.variable, typeWithoutProperty)
        val rhvType = propertyType?.let { TypedVariable(rhv.base, it) }?.withTypeGuards(current)
        return listOfNotNull(updatedFact, rhvType)
    }

    private fun EtsTypeFact.removePropertyType(propertyName: String): Pair<EtsTypeFact, EtsTypeFact?> = when (this) {
        is EtsTypeFact.ObjectEtsTypeFact -> {
            val propertyType = properties[propertyName]
            val updatedThis = EtsTypeFact.ObjectEtsTypeFact(cls, properties.minus(propertyName))
            updatedThis to propertyType
        }

        is EtsTypeFact.IntersectionEtsTypeFact -> TODO()
        is EtsTypeFact.UnionEtsTypeFact -> TODO()
        else -> this to null
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(fact)
            is TypedVariable -> callToReturn(callStatement, returnSite, fact)
        }.map { it.fixThis() }
    }

    private fun callToReturn(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        fact: TypedVariable,
    ): List<BackwardTypeDomainFact> {
        val result = mutableListOf<BackwardTypeDomainFact>()

        val callExpr = callStatement.callExpr ?: error("No call")
        if (callExpr is EtsInstanceCallExpr) {
            val instance = callExpr.instance
            if (instance !is EtsValue) {
                return emptyList()
            }
            val instancePath = instance.toBase()

            val objectWithMethod = EtsTypeFact.ObjectEtsTypeFact(
                cls = null,
                properties = mapOf(callExpr.method.name to EtsTypeFact.FunctionEtsTypeFact)
            )
            result += TypedVariable(instancePath, objectWithMethod)
        }

        val callResultValue = (callStatement as? EtsAssignStmt)?.lhv
        if (callResultValue != null) {
            val callResultPath = callResultValue.toBase()
            if (fact.variable == callResultPath) return result
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
            is TypedVariable -> callToStart(callStatement, calleeStart, fact)
        }.map { it.fixThis() }
    }

    private fun callToStart(
        callStatement: EtsStmt,
        calleeStart: EtsStmt,
        fact: TypedVariable,
    ): List<BackwardTypeDomainFact> {
        val callResultValue = (callStatement as? EtsAssignStmt)?.lhv ?: return emptyList()

        val callResultPath = callResultValue.toBase()

        if (fact.variable != callResultPath) return emptyList()

        if (calleeStart !is EtsReturnStmt) return emptyList()
        val exitValuePath = calleeStart.returnValue?.toBase() ?: return emptyList()

        return listOf(TypedVariable(exitValuePath, fact.type))
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        exitStatement: EtsStmt,
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(fact)
            is TypedVariable -> exitToReturn(callStatement, returnSite, exitStatement, fact)
        }.map { it.fixThis() }
    }

    private fun exitToReturn(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        exitStatement: EtsStmt,
        fact: TypedVariable,
    ): List<BackwardTypeDomainFact> {
        val factVariableBase = fact.variable
        val callExpr = callStatement.callExpr ?: error("No call")

        when (factVariableBase) {
            is AccessPathBase.This -> {
                if (callExpr !is EtsInstanceCallExpr) {
                    return emptyList()
                }

                val instance = callExpr.instance
                if (instance !is EtsValue) {
                    error("Unexpected instance: $instance")
                    // return emptyList()
                }
                val instancePath = instance.toBase()
                return listOf(TypedVariable(instancePath, fact.type))
            }

            is AccessPathBase.Arg -> {
                val arg = callExpr.args.getOrNull(factVariableBase.index)?.toBase() ?: return emptyList()
                return listOf(TypedVariable(arg, fact.type))
            }

            else -> return emptyList()
        }
    }
}
