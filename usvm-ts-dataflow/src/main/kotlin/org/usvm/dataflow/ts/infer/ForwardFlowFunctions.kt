package org.usvm.dataflow.ts.infer

import mu.KotlinLogging
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArithmeticExpr
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldRef
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNegExpr
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsNotExpr
import org.jacodb.ets.model.EtsNullConstant
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsRelationExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedConstant
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.callExpr
import org.jacodb.ets.utils.getLocals
import org.usvm.dataflow.ifds.FlowFunction
import org.usvm.dataflow.ifds.FlowFunctions
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.infer.ForwardTypeDomainFact.TypedVariable
import org.usvm.dataflow.ts.infer.ForwardTypeDomainFact.Zero
import org.usvm.dataflow.ts.util.fixAnyToUnknown
import org.usvm.dataflow.ts.util.getRealLocals
import org.usvm.dataflow.ts.util.toStringLimited
import org.usvm.dataflow.ts.util.unwrapPromise

private val logger = KotlinLogging.logger {}

class ForwardFlowFunctions(
    val graph: EtsApplicationGraph,
    val methodInitialTypes: Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>>,
    val typeInfo: Map<EtsType, EtsTypeFact>,
    val doAddKnownTypes: Boolean = true,
    val doAliasAnalysis: Boolean = true,
    val doLiveVariablesAnalysis: Boolean = true,
) : FlowFunctions<ForwardTypeDomainFact, EtsMethod, EtsStmt> {

    private val typeProcessor = TypeFactProcessor(graph.cp)

    private val aliasesCache: MutableMap<EtsMethod, List<StmtAliasInfo>> = hashMapOf()
    private fun getAliases(method: EtsMethod): List<StmtAliasInfo> {
        return aliasesCache.computeIfAbsent(method) {
            if (doAliasAnalysis) {
                MethodAliasInfoImpl(method).computeAliases()
            } else {
                NoMethodAliasInfo(method).computeAliases()
            }
        }
    }

    private val liveVariablesCache = hashMapOf<EtsMethod, LiveVariables>()
    private fun liveVariables(method: EtsMethod) =
        liveVariablesCache.computeIfAbsent(method) {
            if (doLiveVariablesAnalysis) {
                LiveVariables.from(method)
            } else {
                AlwaysAlive
            }
        }

    override fun obtainPossibleStartFacts(method: EtsMethod): Collection<ForwardTypeDomainFact> {
        val initialTypes = methodInitialTypes[method] ?: return listOf(Zero)

        val result = mutableListOf<ForwardTypeDomainFact>(Zero)

        if (doAddKnownTypes) {
            val fakeLocals = method.getLocals() - method.getRealLocals()

            for ((base, type) in initialTypes) {
                if (base is AccessPathBase.Local) {
                    val fake = fakeLocals.find { it.toBase() == base }
                    if (fake != null) {
                        val path = AccessPath(base, emptyList())
                        val realType = EtsTypeFact.from(fake.type).fixAnyToUnknown()
                        val type2 = typeProcessor.intersect(type, realType) ?: run {
                            logger.warn { "Empty intersection: ${type.toStringLimited()} & ${realType.toStringLimited()}" }
                            type
                        }
                        addTypes(path, type2, result)
                        continue
                    }
                }

                val path = AccessPath(base, emptyList())
                addTypes(path, type, result)
            }

        } else {
            for ((base, type) in initialTypes) {
                val path = AccessPath(base, emptyList())
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
            is TypedVariable -> {
                val liveVars = liveVariables(current.method)
                sequentFact(current, fact).myFilter()
                    .filter {
                        when (val base = it.variable.base) {
                            is AccessPathBase.Local -> liveVars.isAliveAt(base.name, current)
                            else -> true
                        }
                    }
            }
        }
    }

    private fun sequentZero(current: EtsStmt): List<ForwardTypeDomainFact> {
        if (current !is EtsAssignStmt) return listOf(Zero)

        val lhv = current.lhv.toPath()
        val result = mutableListOf<ForwardTypeDomainFact>(Zero)
        val preAliases = getAliases(current.method)[current.location.index]

        fun addTypeFactWithAliases(path: AccessPath, type: EtsTypeFact) {
            result += TypedVariable(path, type)
            if (path.accesses.isNotEmpty()) {
                check(path.accesses.size == 1)
                val base = AccessPath(path.base, emptyList())
                val aliases = preAliases.getAliases(base).filter {
                    when (val b = it.base) {
                        is AccessPathBase.Local -> liveVariables(current.method).isAliveAt(b.name, current)
                        else -> true
                    }
                }
                for (alias in aliases) {
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

            is EtsArrayAccess -> {
                // TODO
            }

            is EtsArithmeticExpr -> {
                result += TypedVariable(lhv, EtsTypeFact.NumberEtsTypeFact)
                result += TypedVariable(lhv, EtsTypeFact.StringEtsTypeFact)
            }

            is EtsRelationExpr -> {
                result += TypedVariable(lhv, EtsTypeFact.BooleanEtsTypeFact)
            }

            is EtsNegExpr -> {
                result += TypedVariable(lhv, EtsTypeFact.NumberEtsTypeFact)
            }

            is EtsNotExpr -> {
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
            is EtsLocal -> r.toPath()
            is EtsThis -> r.toPath()
            is EtsParameterRef -> r.toPath()
            is EtsFieldRef -> r.toPath()
            is EtsArrayAccess -> r.toPath()
            is EtsCastExpr -> r.toPath()
            is EtsAwaitExpr -> r.toPath()
            else -> {
                // logger.info { "TODO forward assign: $current" }
                null
            }
        }

        val preAliases = getAliases(current.method)[current.location.index]

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
                    val type = EtsTypeFact.from((current.rhv as EtsCastExpr).type)

                    return listOf(fact, TypedVariable(path, type))
                } else if (current.rhv is EtsAwaitExpr) {
                    val path = AccessPath(lhv.base, fact.variable.accesses)
                    val promiseType = fact.type

                    if (promiseType is EtsTypeFact.ObjectEtsTypeFact) {
                        val promiseClass = promiseType.cls

                        if (promiseClass is EtsClassType && promiseClass.signature.name == "Promise") {
                            val type = EtsTypeFact.from(
                                type = promiseClass.typeParameters.singleOrNull() ?: return listOf(fact)
                            )
                            return listOf(fact, TypedVariable(path, type))
                        }

                        if (promiseClass is EtsUnclearRefType && promiseClass.name.startsWith("Promise")) {
                            val type = EtsTypeFact.from(
                                type = promiseClass.typeParameters.singleOrNull() ?: return listOf(fact)
                            )
                            return listOf(fact, TypedVariable(path, type))
                        }
                    }
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

                        val aliases = preAliases.getAliases(x).filter {
                            when (val b = it.base) {
                                is AccessPathBase.Local -> liveVariables(current.method).isAliveAt(b.name, current)
                                else -> true
                            }
                        }

                        for (z in aliases) {
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
            Zero -> {
                val callExpr = callStatement.callExpr ?: error("No call in $callStatement")

                val result = mutableListOf<ForwardTypeDomainFact>(Zero)

                if (doAddKnownTypes) {
                    // `x := f()`
                    if (callStatement is EtsAssignStmt) {
                        val left = callStatement.lhv.toPath()
                        val type = EtsTypeFact.from(callExpr.callee.returnType.unwrapPromise())
                        addTypes(left, type, result)
                    }
                }

                result
            }

            is TypedVariable -> call(callStatement, fact)
        }
    }

    private fun call(
        callStatement: EtsStmt,
        fact: TypedVariable,
    ): List<TypedVariable> {
        @Suppress("UNUSED_VARIABLE")
        val callExpr = callStatement.callExpr ?: error("No call in $callStatement")

        // Note: we DO NOT drop any type facts on calls!

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
        // logger.warn { "Dropping too long fact: $it" }
        return@filter false
    }
    if (it.variable.accesses.hasDuplicateFields(DUPLICATE_FIELDS_LIMIT)) {
        // logger.warn { "Dropping fact with duplicate fields: $it" }
        return@filter false
    }
    true
}
