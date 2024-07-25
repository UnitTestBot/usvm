package org.usvm.dataflow.ts.infer

import mu.KotlinLogging
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsBooleanConstant
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsLValue
import org.jacodb.ets.base.EtsNewExpr
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsRef
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.callExpr
import org.usvm.dataflow.ifds.ElementAccessor
import org.usvm.dataflow.ifds.FieldAccessor
import org.usvm.dataflow.ifds.FlowFunction
import org.usvm.dataflow.ifds.FlowFunctions
import org.usvm.dataflow.ts.infer.ForwardTypeDomainFact.TypedVariable
import org.usvm.dataflow.ts.infer.ForwardTypeDomainFact.Zero

private val logger = KotlinLogging.logger {}

class ForwardFlowFunction(
    val graph: ApplicationGraph<EtsMethod, EtsStmt>,
    val methodInitialTypes: Map<EtsMethod, EtsMethodTypeFacts>,
) : FlowFunctions<ForwardTypeDomainFact, EtsMethod, EtsStmt> {

    override fun obtainPossibleStartFacts(method: EtsMethod): Collection<ForwardTypeDomainFact> {
        val result = mutableListOf<ForwardTypeDomainFact>(Zero)

        val initialTypes = methodInitialTypes[method]
        if (initialTypes != null) {
            for ((base, type) in initialTypes.types) {
                val path = AccessPath(base, accesses = emptyList())
                addTypes(path, type, result)
            }
        }

        return result
    }

    private fun addTypes(ap: AccessPath, type: EtsTypeFact, facts: MutableList<ForwardTypeDomainFact>) {
        when (type) {
            EtsTypeFact.UnknownEtsTypeFact -> facts += TypedVariable(ap, EtsTypeFact.AnyEtsTypeFact)

            EtsTypeFact.AnyEtsTypeFact,
            EtsTypeFact.FunctionEtsTypeFact,
            EtsTypeFact.NumberEtsTypeFact,
            EtsTypeFact.BooleanEtsTypeFact,
            EtsTypeFact.StringEtsTypeFact,
            -> facts += TypedVariable(ap, type)

            is EtsTypeFact.ObjectEtsTypeFact -> {
                for ((propertyName, propertyType) in type.properties) {
                    val propertyAp = ap.plus(FieldAccessor(propertyName))
                    addTypes(propertyAp, propertyType, facts)
                }

                val objType = EtsTypeFact.ObjectEtsTypeFact(type.cls, properties = emptyMap())
                facts += TypedVariable(ap, objType)
            }

            is EtsTypeFact.GuardedTypeFact -> {
                addTypes(ap, type.type, facts)
            }

            is EtsTypeFact.IntersectionEtsTypeFact -> {
                type.types.forEach { addTypes(ap, it, facts) }
            }

            is EtsTypeFact.UnionEtsTypeFact -> {
                type.types.forEach { addTypes(ap, it, facts) }
            }
        }
    }

    override fun obtainSequentFlowFunction(
        current: EtsStmt,
        next: EtsStmt,
    ): FlowFunction<ForwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> zeroSequent(current)
            is TypedVariable -> factSequent(current, fact)
        }
    }

    private fun zeroSequent(current: EtsStmt): List<ForwardTypeDomainFact> {
        if (current !is EtsAssignStmt) return listOf(Zero)

        val result = mutableListOf<ForwardTypeDomainFact>(Zero)

        val rhv = current.rhv
        if (rhv is EtsLValue || rhv is EtsRef) return result

        val lhv = current.lhv.toPath()

        when (rhv) {
            is EtsNewExpr -> {
                val newType = rhv.type
                val type = if (newType is EtsClassType) {
                    val cls = (graph as EtsApplicationGraphWithExplicitEntryPoint).graph.cp.classes
                        .firstOrNull { it.name == newType.typeName }
                    if (cls != null) {
                        EtsTypeFact.ObjectEtsTypeFact(
                            cls = rhv.type,
                            properties = cls.fields.associate {
                                // it.name to EtsTypeFact.UnknownEtsTypeFact
                                it.name to EtsTypeFact.from(it.type)
                            }
                        )
                    } else {
                        EtsTypeFact.ObjectEtsTypeFact(cls = rhv.type, properties = emptyMap())
                    }
                } else {
                    EtsTypeFact.ObjectEtsTypeFact(cls = rhv.type, properties = emptyMap())
                }
                result += TypedVariable(lhv, type)
            }

            is EtsStringConstant -> {
                result += TypedVariable(lhv, EtsTypeFact.StringEtsTypeFact)
            }

            is EtsNumberConstant -> {
                result += TypedVariable(lhv, EtsTypeFact.NumberEtsTypeFact)
            }

            is EtsBooleanConstant -> {
                result += TypedVariable(lhv, EtsTypeFact.BooleanEtsTypeFact)
            }

            else -> {
                logger.info { "TODO: forward assign $current" }
            }
        }

        return result
    }

    private fun factSequent(current: EtsStmt, fact: TypedVariable): List<ForwardTypeDomainFact> {
        if (current !is EtsAssignStmt) return listOf(fact)

        val lhv = current.lhv.toPath()

        val rhv = when (val r = current.rhv) {
            is EtsRef -> r.toPath()
            is EtsLValue -> r.toPath()
            else -> {
                logger.info { "TODO forward assign: $current" }
                null
            }
        }

        if (fact.variable.base != lhv.base && fact.variable.base != rhv?.base) {
            return listOf(fact)
        }

        if (rhv == null) {
            check(fact.variable.base == lhv.base)
            return emptyList()
        }

        if (lhv.accesses.isEmpty() && rhv.accesses.isEmpty()) {
            if (lhv.base == fact.variable.base) return emptyList()
            check(fact.variable.base == rhv.base)

            val path = AccessPath(lhv.base, fact.variable.accesses)
            return listOf(fact, TypedVariable(path, fact.type))
        }

        if (lhv.accesses.isEmpty()) {
            if (lhv.base == fact.variable.base) return emptyList()
            check(fact.variable.base == rhv.base)

            val accessor = rhv.accesses.single()
            if (fact.variable.accesses.firstOrNull() != accessor) {
                return listOf(fact)
            }

            val path = AccessPath(lhv.base, fact.variable.accesses.drop(1))
            return listOf(fact, TypedVariable(path, fact.type))
        }

        // check(lhv.accesses.isNotEmpty() && rhv.accesses.isEmpty())

        val accessor = lhv.accesses.single()
        if (fact.variable.base == lhv.base) {
            if (fact.variable.accesses.firstOrNull() == accessor) {
                return when (accessor) {
                    is FieldAccessor -> emptyList()

                    // Can't erase array type
                    ElementAccessor -> listOf(fact)
                }
            }

            return listOf(fact)
        }

        check(fact.variable.base == rhv.base)
        val path = lhv.plus(fact.variable.accesses)
        return listOf(fact, TypedVariable(path, fact.type))
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
    ): FlowFunction<ForwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(Zero)
            is TypedVariable -> callToReturn(callStatement, returnSite, fact)
        }
    }

    private fun callToReturn(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        fact: TypedVariable,
    ): List<ForwardTypeDomainFact> {
        val callResultValue = (callStatement as? EtsAssignStmt)?.lhv?.toPath()
        if (callResultValue != null) {
            if (fact.variable.base == callResultValue.base) return emptyList()
        }

        val callExpr = callStatement.callExpr ?: error("No call")

        // todo: hack, keep fact if call was not resolved
        if (graph.callees(callStatement).none()) {
            return listOf(fact)
        }

        if (callExpr is EtsInstanceCallExpr) {
            val instance = callExpr.instance.toPath()
            if (fact.variable.base == instance.base) return emptyList()
        }

        for (arg in callExpr.args) {
            val argPath = arg.toPath()
            if (fact.variable.base == argPath.base) return emptyList()
        }

        return listOf(fact)
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: EtsStmt,
        calleeStart: EtsStmt,
    ): FlowFunction<ForwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(Zero)
            is TypedVariable -> callToStart(callStatement, calleeStart, fact)
        }
    }

    private fun callToStart(
        callStatement: EtsStmt,
        calleeStart: EtsStmt,
        fact: TypedVariable,
    ): List<ForwardTypeDomainFact> {
        val result = mutableListOf<ForwardTypeDomainFact>()

        val callExpr = callStatement.callExpr ?: error("No call")

        if (callExpr is EtsInstanceCallExpr) {
            val instance = callExpr.instance.toPath()
            if (fact.variable.base == instance.base) {
                val path = AccessPath(AccessPathBase.This, fact.variable.accesses)
                result += TypedVariable(path, fact.type)
            }
        }

        for ((index, arg) in callExpr.args.withIndex()) {
            val argPath = arg.toPath()
            if (fact.variable.base == argPath.base) {
                val path = AccessPath(AccessPathBase.Arg(index), fact.variable.accesses)
                result += TypedVariable(path, fact.type)
            }
        }

        return result
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        exitStatement: EtsStmt,
    ): FlowFunction<ForwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(Zero)
            is TypedVariable -> exitToReturn(callStatement, returnSite, exitStatement, fact)
        }
    }

    private fun exitToReturn(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        exitStatement: EtsStmt,
        fact: TypedVariable,
    ): List<ForwardTypeDomainFact> {
        val factVariableBase = fact.variable.base
        val callExpr = callStatement.callExpr ?: error("No call")

        when (factVariableBase) {
            is AccessPathBase.This -> {
                if (callExpr !is EtsInstanceCallExpr) {
                    return emptyList()
                }

                val instance = callExpr.instance.toPath()
                check(instance.accesses.isEmpty())

                val path = AccessPath(instance.base, fact.variable.accesses)
                return listOf(TypedVariable(path, fact.type))
            }

            is AccessPathBase.Arg -> {
                val arg = callExpr.args.getOrNull(factVariableBase.index)?.toPath() ?: return emptyList()
                val path = AccessPath(arg.base, fact.variable.accesses)
                return listOf(TypedVariable(path, fact.type))
            }

            else -> {
                if (exitStatement !is EtsReturnStmt) return emptyList()
                val exitValue = exitStatement.returnValue?.toPath() ?: return emptyList()

                if (fact.variable.base != exitValue.base) return emptyList()

                val callResultValue = (callStatement as? EtsAssignStmt)?.lhv ?: return emptyList()
                val callResultPath = callResultValue.toPath()

                val path = AccessPath(callResultPath.base, fact.variable.accesses)
                return listOf(TypedVariable(path, fact.type))
            }
        }
    }
}
