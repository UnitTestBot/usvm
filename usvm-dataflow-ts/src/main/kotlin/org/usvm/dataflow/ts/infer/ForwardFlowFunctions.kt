package org.usvm.dataflow.ts.infer

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsBooleanConstant
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsLValue
import org.jacodb.ets.base.EtsNewExpr
import org.jacodb.ets.base.EtsNullConstant
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsRef
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.base.EtsUndefinedConstant
import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.callExpr
import org.usvm.dataflow.ifds.ElementAccessor
import org.usvm.dataflow.ifds.FieldAccessor
import org.usvm.dataflow.ifds.FlowFunction
import org.usvm.dataflow.ifds.FlowFunctions
import org.usvm.dataflow.ts.infer.ForwardTypeDomainFact.TypedVariable
import org.usvm.dataflow.ts.infer.ForwardTypeDomainFact.Zero

class ForwardFlowFunctions(
    val graph: EtsApplicationGraph,
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

    private fun addTypes(
        path: AccessPath,
        type: EtsTypeFact,
        facts: MutableList<ForwardTypeDomainFact>,
    ) {
        when (type) {
            EtsTypeFact.UnknownEtsTypeFact -> {
                facts += TypedVariable(path, EtsTypeFact.AnyEtsTypeFact)
            }

            is EtsTypeFact.ObjectEtsTypeFact -> {
                for ((propertyName, propertyType) in type.properties) {
                    val propertyPath = path + FieldAccessor(propertyName)
                    addTypes(propertyPath, propertyType, facts)
                }

                val objType = EtsTypeFact.ObjectEtsTypeFact(cls = type.cls, properties = emptyMap())
                facts += TypedVariable(path, objType)
            }

            is EtsTypeFact.GuardedTypeFact -> {
                addTypes(path, type.type, facts)
            }

            is EtsTypeFact.IntersectionEtsTypeFact -> {
                type.types.forEach { addTypes(path, it, facts) }
            }

            is EtsTypeFact.UnionEtsTypeFact -> {
                type.types.forEach { addTypes(path, it, facts) }
            }

            else -> {
                facts += TypedVariable(path, type)
            }
        }
    }

    override fun obtainSequentFlowFunction(
        current: EtsStmt,
        next: EtsStmt,
    ): FlowFunction<ForwardTypeDomainFact> = FlowFunction { fact ->
        if (current is EtsAssignStmt) {
            val lhvPath = current.lhv.toPathOrNull()
            val rhvPath = current.rhv.toPathOrNull()
            if (lhvPath != null && rhvPath != null && lhvPath == rhvPath) {
                return@FlowFunction listOf(fact)
            }
        }
        when (fact) {
            Zero -> sequentZero(current)
            is TypedVariable -> sequentFact(current, fact)
        }
    }

    private fun sequentZero(current: EtsStmt): List<ForwardTypeDomainFact> {
        if (current !is EtsAssignStmt) return listOf(Zero)

        val lhv = current.lhv.toPath()
        val result = mutableListOf<ForwardTypeDomainFact>(Zero)

        when (val rhv = current.rhv) {
            is EtsNewExpr -> {
                // val newType = rhv.type
                // if (newType is EtsClassType) {
                //     val cls = graph.cp.classes
                //         .firstOrNull { it.name == newType.typeName }
                //     if (cls != null) {
                //         for (f in cls.fields) {
                //             val path = lhv + FieldAccessor(f.name)
                //             result += TypedVariable(path, EtsTypeFact.from(f.type))
                //         }
                //     }
                // }

                val type = EtsTypeFact.ObjectEtsTypeFact(cls = rhv.type, properties = emptyMap())
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

            is EtsNullConstant -> {
                result += TypedVariable(lhv, EtsTypeFact.NullEtsTypeFact)
            }

            is EtsUndefinedConstant -> {
                result += TypedVariable(lhv, EtsTypeFact.UndefinedEtsTypeFact)
            }

            // Note: do not handle cast in forward ff!
            // is EtsCastExpr -> {
            //     result += TypedVariable(lhv, EtsTypeFact.from(rhv.type))
            // }

            else -> {
                // logger.info { "TODO: forward assign $current" }
            }
        }

        return result
    }

    private fun sequentFact(current: EtsStmt, fact: TypedVariable): List<ForwardTypeDomainFact> {
        if (current !is EtsAssignStmt) return listOf(fact)

        val lhv = current.lhv.toPath()

        val rhv = when (val r = current.rhv) {
            is EtsRef -> r.toPath() // This, FieldRef, ArrayAccess
            is EtsLValue -> r.toPath() // Local
            is EtsCastExpr -> r.toPath() // Cast
            else -> {
                // logger.info { "TODO forward assign: $current" }
                null
            }
        }

        // Pass-through completely unrelated facts:
        if (fact.variable.base != lhv.base && fact.variable.base != rhv?.base) {
            return listOf(fact)
        }

        // Override LHS:
        // TODO: what about `x.f := new T` with fact `x:U`?
        if (rhv == null) {
            check(fact.variable.base == lhv.base)
            return emptyList()
        }

        // Case `x := y [as T]`
        // (if no cast):
        //   `fact == y...:U` |= new fact `x...:U`
        // (if cast):
        //   `fact == y...:U` |= new fact `x...:W`, where W = U intersect T
        if (lhv.accesses.isEmpty() && rhv.accesses.isEmpty()) {
            if (lhv.base == fact.variable.base) return emptyList()
            check(fact.variable.base == rhv.base)

            val path = AccessPath(lhv.base, fact.variable.accesses)

            val newFact = if (current.rhv is EtsCastExpr && fact.variable.accesses.isEmpty()) {
                TypedVariable(path, EtsTypeFact.from(current.rhv.type).intersect(fact.type) ?: fact.type)
            } else {
                TypedVariable(path, fact.type)
            }
            return listOf(fact, newFact)
        }

        check(current.rhv !is EtsCastExpr)

        // Case `x := y.f`
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

        // Case `x.f := y`
        // `fact == x.f` |= drop `fact`
        // `fact == x[i]` |= keep the fact
        // `fact.base != x` |= continue
        check(lhv.accesses.isNotEmpty() && rhv.accesses.isEmpty())
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
        val path = lhv + fact.variable.accesses
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
            is TypedVariable -> start(callStatement, calleeStart, fact)
        }
    }

    private fun start(
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
            is TypedVariable -> exit(callStatement, returnSite, exitStatement, fact)
        }
    }

    private fun exit(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        exitStatement: EtsStmt,
        fact: TypedVariable,
    ): List<ForwardTypeDomainFact> {
        val factVariableBase = fact.variable.base
        val callExpr = callStatement.callExpr ?: error("No call")

        when (factVariableBase) {
            is AccessPathBase.This -> {
                // Drop facts on This if the call was static
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

                val result = (callStatement as? EtsAssignStmt)?.lhv?.toPath() ?: return emptyList()
                check(result.accesses.isEmpty())

                val path = AccessPath(result.base, fact.variable.accesses)
                return listOf(TypedVariable(path, fact.type))
            }
        }
    }
}
