package org.usvm.dataflow.ts.infer

import mu.KotlinLogging
import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsArithmeticExpr
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsBooleanConstant
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsFieldRef
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsLValue
import org.jacodb.ets.base.EtsNewArrayExpr
import org.jacodb.ets.base.EtsNewExpr
import org.jacodb.ets.base.EtsNullConstant
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsRef
import org.jacodb.ets.base.EtsRelationExpr
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUndefinedConstant
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.callExpr
import org.usvm.dataflow.ifds.FlowFunction
import org.usvm.dataflow.ifds.FlowFunctions
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.infer.ForwardTypeDomainFact.TypedVariable
import org.usvm.dataflow.ts.infer.ForwardTypeDomainFact.Zero

private val logger = KotlinLogging.logger {}

class ForwardFlowFunctions(
    val graph: EtsApplicationGraph,
    val methodInitialTypes: Map<EtsMethod, EtsMethodTypeFacts>,
    val typeInfo: Map<EtsType, EtsTypeFact>,
    val doAddKnownTypes: Boolean = true,
) : FlowFunctions<ForwardTypeDomainFact, EtsMethod, EtsStmt> {

    private val aliasesCache: MutableMap<EtsMethod, Map<EtsStmt, Pair<AliasInfo, AliasInfo>>> = hashMapOf()

    private fun getAliases(method: EtsMethod): Map<EtsStmt, Pair<AliasInfo, AliasInfo>> {
        return aliasesCache.computeIfAbsent(method) { computeAliases(method) }
    }

    override fun obtainPossibleStartFacts(method: EtsMethod): Collection<ForwardTypeDomainFact> {
        val result = mutableListOf<ForwardTypeDomainFact>(Zero)

        val initialTypes = methodInitialTypes[method]
        if (initialTypes != null) {
            for ((base, type) in initialTypes.types) {
                val path = AccessPath(base, accesses = emptyList())
                addTypes(path, type, result)
            }
        }

        if (doAddKnownTypes) {
            for (local in method.locals) {
                if (local.type != EtsUnknownType && local.type != EtsAnyType) {
                    val path = AccessPath(AccessPathBase.Local(local.name), accesses = emptyList())
                    val type = EtsTypeFact.from(local.type)
                    if (type != EtsTypeFact.UnknownEtsTypeFact && type != EtsTypeFact.AnyEtsTypeFact) {
                        logger.debug { "Adding known type for $path: $type" }
                        addTypes(path, type, result)
                    }
                }
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

                facts += TypedVariable(path, type)
            }

            is EtsTypeFact.ArrayEtsTypeFact -> {
                // check(type.elementType !is EtsTypeFact.ArrayEtsTypeFact)
                facts += TypedVariable(path, type)
                addTypes(path + ElementAccessor, type.elementType, facts)
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
            is TypedVariable -> sequentFact(current, fact).myFilter()
        }
    }

    private fun sequentZero(current: EtsStmt): List<ForwardTypeDomainFact> {
        if (current !is EtsAssignStmt) return listOf(Zero)

        val lhv = current.lhv.toPath()
        val result = mutableListOf<ForwardTypeDomainFact>(Zero)
        val (preAliases, _) = getAliases(current.method)[current]!!

        fun addTypeFactWithAliases(path: AccessPath, type: EtsTypeFact) {
            result += TypedVariable(path, type)
            if (path.accesses.isNotEmpty()) {
                check(path.accesses.size == 1)
                val base = AccessPath(path.base, emptyList())
                for (alias in preAliases.getAliases(base)) {
                    val newPath = alias + path.accesses.single()
                    result += TypedVariable(newPath, type)
                }
            }
        }

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

                val type = typeInfo[rhv.type]
                    ?: EtsTypeFact.ObjectEtsTypeFact(cls = rhv.type, properties = emptyMap())
                addTypeFactWithAliases(lhv, type)
            }

            is EtsNewArrayExpr -> {
                // TODO: check
                val elementType = EtsTypeFact.from(rhv.elementType)
                val type = EtsTypeFact.ArrayEtsTypeFact(elementType = elementType)
                result += TypedVariable(lhv, type)
                result += TypedVariable(lhv + ElementAccessor, elementType)
            }

            is EtsStringConstant -> {
                addTypeFactWithAliases(lhv, EtsTypeFact.StringEtsTypeFact)
            }

            is EtsNumberConstant -> {
                addTypeFactWithAliases(lhv, EtsTypeFact.NumberEtsTypeFact)
            }

            is EtsBooleanConstant -> {
                addTypeFactWithAliases(lhv, EtsTypeFact.BooleanEtsTypeFact)
            }

            is EtsNullConstant -> {
                addTypeFactWithAliases(lhv, EtsTypeFact.NullEtsTypeFact)
            }

            is EtsUndefinedConstant -> {
                addTypeFactWithAliases(lhv, EtsTypeFact.UndefinedEtsTypeFact)
            }

            // Note: do not handle cast in forward ff!
            // is EtsCastExpr -> {
            //     result += TypedVariable(lhv, EtsTypeFact.from(rhv.type))
            // }

            is EtsFieldRef -> {
                if (doAddKnownTypes && rhv.type != EtsUnknownType && rhv.type != EtsAnyType) {
                    val type = EtsTypeFact.from(rhv.type)
                    logger.debug { "Adding known type for $lhv from $rhv: $type" }
                    addTypeFactWithAliases(lhv, type)
                }
            }

            is EtsArithmeticExpr -> {
                result += TypedVariable(lhv, EtsTypeFact.StringEtsTypeFact)
                result += TypedVariable(lhv, EtsTypeFact.NumberEtsTypeFact)
            }

            is EtsRelationExpr -> {
                result += TypedVariable(lhv, EtsTypeFact.BooleanEtsTypeFact)
            }

            else -> {
                // logger.info { "TODO: forward assign $current" }
            }
        }

        return result
    }

    private fun sequentFact(current: EtsStmt, fact: TypedVariable): List<TypedVariable> {
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

        val (preAliases, _) = getAliases(current.method)[current]!!

        // Override LHS when RHS is const-like:
        if (rhv == null) {
            if (lhv.accesses.isEmpty()) {
                // x := const

                // TODO: `x := const as T`

                // x.*:T |= drop
                if (fact.variable.startsWith(lhv)) {
                    return emptyList()
                }

            } else {
                // x.f := const  OR  x[i] := const

                check(lhv.accesses.size == 1)
                when (val a = lhv.accesses.single()) {
                    // x.f := const
                    is FieldAccessor -> {
                        val base = AccessPath(lhv.base, emptyList())

                        // x.f.*:T |= drop
                        if (fact.variable.startsWith(lhv)) {
                            return emptyList()
                        }
                        // z in G(x), z.f.*:T |= drop
                        if (preAliases.getAliases(base).any { fact.variable.startsWith(it + a) }) {
                            return emptyList()
                        }
                    }

                    // x[i] := const
                    is ElementAccessor -> {
                        // do nothing, pass-through
                    }
                }
            }

            // Pass-through unrelated facts:
            return listOf(fact)
        }

        if (lhv.accesses.isEmpty() && rhv.accesses.isEmpty()) {
            // x := y

            // TODO: x := x
            // Note: handled outside

            // x.*:T |= drop
            if (fact.variable.startsWith(lhv)) {
                return emptyList()
            }

            // y.*:T |= y.*:T (keep) + x.*:T (same tail)
            if (fact.variable.startsWith(rhv)) {
                // Extra case with cast: `x := y as U`:
                // `y.*:T` |= keep + new fact `x.*:W`, where `W = T intersect U`
                // TODO: Currently, we just take the type from the CastExpr, without intersecting.
                //       The problem is that when we have fact `y:any`, the intersection (though probably correctly)
                //       produces `x:any`, so we just lose type information from the cast.
                //       Using the cast type directly is just a temporary solution to satisfy simple tests.
                if (current.rhv is EtsCastExpr) {
                    val path = AccessPath(lhv.base, fact.variable.accesses)
                    // val type = EtsTypeFact.from((current.rhv as EtsCastExpr).type).intersect(fact.type) ?: fact.type
                    val type = EtsTypeFact.from((current.rhv as EtsCastExpr).type)
                    return listOf(fact, TypedVariable(path, type))
                }

                val path = AccessPath(lhv.base, fact.variable.accesses)
                return listOf(fact, TypedVariable(path, fact.type))
            }

        } else if (lhv.accesses.isEmpty()) {
            // x := y.f  OR  x := y[i]

            // TODO: x := x.f
            // ??????? x.f:T |= drop

            // x.*:T |= drop
            if (fact.variable.startsWith(lhv)) {
                return emptyList()
            }

            check(rhv.accesses.size == 1)
            when (val a = rhv.accesses.single()) {
                // x := y.f
                is FieldAccessor -> {
                    // y.f.*:T |= y.f.*:T (keep) + x.*:T (same tail after .f)
                    if (fact.variable.startsWith(rhv)) {
                        val path = lhv + fact.variable.accesses.drop(1)
                        return listOf(fact, TypedVariable(path, fact.type))
                    }
                    // Note: the following is unnecessary due to `z := y` alias
                    // // z in G(y), z.f.*:T |= z.f.*:T (keep) + x.*:T (same tail after .f)
                    // val y = AccessPath(rhv.base, emptyList())
                    // for (z in preAliases.getAliases(y)) {
                    //     if (fact.variable.startsWith(z + a)) {
                    //         val path = lhv + fact.variable.accesses.drop(z.accesses.size + 1)
                    //         return listOf(fact, TypedVariable(path, fact.type))
                    //     }
                    // }
                }

                // x := y[i]
                is ElementAccessor -> {
                    // do nothing, pass-through
                    // TODO: ???

                    // TODO: do we need to add type fact `x.*:T` here?
                    // y[i].*:T |= y[i].*:T (keep) + x.*:T (same tail after [i])
                    if (fact.variable.startsWith(rhv)) {
                        val path = lhv + fact.variable.accesses.drop(1)
                        return listOf(fact, TypedVariable(path, fact.type))
                    }
                }
            }

        } else if (rhv.accesses.isEmpty()) {
            // x.f := y  OR  x[i] := y

            check(lhv.accesses.size == 1)
            when (val a = lhv.accesses.single()) {
                // x.f := y
                is FieldAccessor -> {
                    // TODO: x.f := x

                    // x.f.*:T |= drop
                    if (fact.variable.startsWith(lhv)) {
                        return emptyList()
                    }
                    // z in G(x), z.f.*:T |= drop
                    val x = AccessPath(lhv.base, emptyList())
                    if (preAliases.getAliases(x).any { z -> fact.variable.startsWith(z + a) }) {
                        return emptyList()
                    }

                    // x.*:T |= x.*:T (keep)
                    // Note: .* does NOT start with .f, which is handled above
                    if (fact.variable.base == lhv.base) {
                        return listOf(fact)
                    }

                    // y.*:T |= y.*:T (keep) + x.f.*:T (same tail) + aliases
                    // aliases: z in G(x), z.f.*:T |= x.f.*:T (same tail)
                    if (fact.variable.startsWith(rhv)) {
                        val result = mutableListOf(fact)
                        // skip duplicate fields
                        // if (fact.variable.accesses.firstOrNull() != a) {
                            val path1 = lhv + fact.variable.accesses
                            result += TypedVariable(path1, fact.type)
                        // }
                        for (z in preAliases.getAliases(x)) {
                            // skip duplicate fields
                            // if (z.accesses.firstOrNull() != a) {
                                // TODO: what about z.accesses.last == a ?
                                val path2 = z + a + fact.variable.accesses
                                result += TypedVariable(path2, fact.type)
                            // }
                        }
                        return result
                    }
                    // Note: the following is unnecessary due to `z := y` alias
                    // // z in G(y), z.*:T |= x.f.*:T (same tail)
                    // for (z in preAliases.getAliases(rhv)) {
                    //     if (fact.variable.startsWith(z)) {
                    //         val path = lhv + fact.variable.accesses
                    //         return listOf(fact, TypedVariable(path, fact.type))
                    //     }
                    // }
                }

                // x[i] := y
                is ElementAccessor -> {
                    // do nothing, pass-through
                    // TODO: ???

                    // TODO: do we really want to add type fact `x[i]:T` here?
                    // y.*:T |= y.*:T (keep) + x[i].*:T (same tail)
                    // TODO: what about aliases of x?
                    if (fact.variable.startsWith(rhv)) {
                        val path = lhv + fact.variable.accesses
                        return listOf(fact, TypedVariable(path, fact.type))
                    }
                }
            }

        } else {
            error("Incorrect 3AC: $current")
        }

        return listOf(fact)
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
    ): FlowFunction<ForwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            // TODO: add known return type of the function call
            Zero -> listOf(Zero)
            is TypedVariable -> call(callStatement, fact)
        }
    }

    private fun call(
        callStatement: EtsStmt,
        fact: TypedVariable,
    ): List<TypedVariable> {
        val callResultValue = (callStatement as? EtsAssignStmt)?.lhv?.toPath()
        if (callResultValue != null) {
            // Drop fact on LHS as it will be overwritten by the call result
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
            is TypedVariable -> start(callStatement, fact)
        }
    }

    private fun start(
        callStatement: EtsStmt,
        fact: TypedVariable,
    ): List<TypedVariable> {
        val result = mutableListOf<TypedVariable>()

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
            is TypedVariable -> exit(callStatement, exitStatement, fact)
        }
    }

    private fun exit(
        callStatement: EtsStmt,
        exitStatement: EtsStmt,
        fact: TypedVariable,
    ): List<TypedVariable> {
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

                val callResult = (callStatement as? EtsAssignStmt)?.lhv?.toPath() ?: return emptyList()
                check(callResult.accesses.isEmpty())

                val path = AccessPath(callResult.base, fact.variable.accesses)
                return listOf(TypedVariable(path, fact.type))
            }
        }
    }
}

private const val ACCESSES_LIMIT = 5
private const val DUPLICATE_FIELDS_LIMIT = 3

private fun Iterable<TypedVariable>.myFilter(): List<TypedVariable> = filter {
    if (it.variable.accesses.size > ACCESSES_LIMIT) {
        logger.warn { "Dropping too long fact: $it" }
        return@filter false
    }
    if (it.variable.accesses.hasDuplicateFields(DUPLICATE_FIELDS_LIMIT)) {
        logger.warn { "Dropping fact with duplicate fields: $it" }
        return@filter false
    }
    true
}
