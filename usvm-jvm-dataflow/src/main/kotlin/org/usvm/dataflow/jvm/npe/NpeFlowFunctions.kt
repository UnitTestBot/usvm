/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.usvm.dataflow.jvm.npe

import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcArgument
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCallExpr
import org.jacodb.api.jvm.cfg.JcEqExpr
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstanceCallExpr
import org.jacodb.api.jvm.cfg.JcNeqExpr
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcNullConstant
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.findType
import org.jacodb.impl.bytecode.isNullable
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.CopyAllMarks
import org.jacodb.taint.configuration.CopyMark
import org.jacodb.taint.configuration.RemoveAllMarks
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.TaintCleaner
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintEntryPointSource
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough
import org.usvm.dataflow.config.BasicConditionEvaluator
import org.usvm.dataflow.config.CallPositionToAccessPathResolver
import org.usvm.dataflow.config.CallPositionToValueResolver
import org.usvm.dataflow.config.EntryPointPositionToAccessPathResolver
import org.usvm.dataflow.config.EntryPointPositionToValueResolver
import org.usvm.dataflow.config.FactAwareConditionEvaluator
import org.usvm.dataflow.config.TaintActionEvaluator
import org.usvm.dataflow.ifds.AccessPath
import org.usvm.dataflow.ifds.ElementAccessor
import org.usvm.dataflow.ifds.FlowFunction
import org.usvm.dataflow.ifds.FlowFunctions
import org.usvm.dataflow.ifds.isOnHeap
import org.usvm.dataflow.ifds.isStatic
import org.usvm.dataflow.ifds.minus
import org.usvm.dataflow.jvm.graph.JcApplicationGraph
import org.usvm.dataflow.jvm.util.JcTraits
import org.usvm.dataflow.taint.TaintDomainFact
import org.usvm.dataflow.taint.TaintZeroFact
import org.usvm.dataflow.taint.Tainted
import org.usvm.dataflow.util.startsWith
import org.usvm.util.onSome

private val logger = mu.KotlinLogging.logger {}

class ForwardNpeFlowFunctions(
    private val traits: JcTraits,
    private val graph: JcApplicationGraph,
    private val getConfigForMethod: (JcMethod) -> List<TaintConfigurationItem>?,
) : FlowFunctions<TaintDomainFact, JcMethod, JcInst> {

    private val cp: JcClasspath
        get() = graph.cp

    override fun obtainPossibleStartFacts(
        method: JcMethod,
    ): Collection<TaintDomainFact> = with(traits) {
        buildSet {
            addAll(obtainPossibleStartFactsBasic(method))

            // Possibly null arguments:
            for (p in method.parameters.filter { it.isNullable != false }) {
                val t = cp.findType(p.type.typeName)
                val arg = JcArgument.of(p.index, p.name, t)
                val path = convertToPath(arg)
                add(Tainted(path, TaintMark.NULLNESS))
            }
        }
    }

    private fun obtainPossibleStartFactsBasic(
        method: JcMethod,
    ): Collection<TaintDomainFact> = buildSet {
        // Zero (reachability) fact always present at entrypoint:
        add(TaintZeroFact)

        // Extract initial facts from the config:
        val config = getConfigForMethod(method)

        if (config != null) {
            val conditionEvaluator = BasicConditionEvaluator(
                traits,
                EntryPointPositionToValueResolver(traits, method)
            )
            val actionEvaluator = TaintActionEvaluator(
                EntryPointPositionToAccessPathResolver(traits, method)
            )

            // Handle EntryPointSource config items:
            for (item in config.filterIsInstance<TaintEntryPointSource>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    for (action in item.actionsAfter) {
                        val result = when (action) {
                            is AssignMark -> actionEvaluator.evaluate(action)
                            else -> error("$action is not supported for $item")
                        }
                        result.onSome { addAll(it) }
                    }
                }
            }
        }
    }

    private fun transmitTaintAssign(
        fact: Tainted,
        from: CommonExpr,
        to: CommonValue,
    ): Collection<Tainted> = with(traits) {
        from as JcExpr
        to as JcValue

        val toPath = convertToPath(to)
        val fromPath = convertToPathOrNull(from)

        if (fact.mark == TaintMark.NULLNESS) {
            // TODO: consider
            //  if (from is JcNewExpr
            //      || from is JcNewArrayExpr
            //      || from is JcConstant
            //      || (from is JcCallExpr && from.method.method.isNullable != true))
            if (fact.variable.startsWith(toPath)) {
                // NULLNESS is overridden:
                return emptySet()
            }
        }

        if (fromPath != null) {
            // Adhoc taint array:
            if (fromPath.accesses.isNotEmpty()
                && fromPath.accesses.last() is ElementAccessor
                && fromPath == (fact.variable + ElementAccessor)
            ) {
                val newTaint = fact.copy(variable = toPath)
                return setOf(fact, newTaint)
            }

            val tail = fact.variable - fromPath
            if (tail != null) {
                // Both 'from' and 'to' are tainted now:
                val newPath = toPath + tail
                val newTaint = fact.copy(variable = newPath)
                return setOf(fact, newTaint)
            }
        }

        return buildSet {
            if (from is JcNullConstant) {
                add(Tainted(toPath, TaintMark.NULLNESS))
            }

            if (fact.variable.startsWith(toPath)) {
                // 'to' was (sub-)tainted, but it is now overridden by 'from':
                return@buildSet
            } else {
                // Neither 'from' nor 'to' are tainted:
                add(fact)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun transmitTaintNormal(
        fact: Tainted,
        inst: JcInst,
    ): List<Tainted> {
        // Pass-through:
        return listOf(fact)
    }

    private fun generates(
        inst: JcInst,
    ): Collection<TaintDomainFact> = with(traits) {
        buildList {
            if (inst is CommonAssignInst) {
                val toPath = convertToPath(inst.lhv as JcValue)
                val from = inst.rhv
                if (from is JcNullConstant || (from is JcCallExpr && from.method.method.isNullable == true)) {
                    add(Tainted(toPath, TaintMark.NULLNESS))
                } else if (from is JcNewArrayExpr && (from.type as JcArrayType).elementType.nullable != false) {
                    val accessors = List((from.type as JcArrayType).dimensions) { ElementAccessor }
                    val path = toPath + accessors
                    add(Tainted(path, TaintMark.NULLNESS))
                }
            }
        }
    }

    private val JcIfInst.pathComparedWithNull: AccessPath?
        get() = with(traits) {
            val expr = condition
            return if (expr.rhv is JcNullConstant) {
                convertToPathOrNull(expr.lhv)
            } else if (expr.lhv is JcNullConstant) {
                convertToPathOrNull(expr.rhv)
            } else {
                null
            }
        }

    override fun obtainSequentFlowFunction(
        current: JcInst,
        next: JcInst,
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
            if (fact.variable.isDereferencedAt(traits, current)) {
                return@FlowFunction emptySet()
            }
        }

        if (current is JcIfInst) {
            val nextIsTrueBranch = next.location.index == current.trueBranch.index
            val pathComparedWithNull = current.pathComparedWithNull
            if (fact == TaintZeroFact) {
                if (pathComparedWithNull != null) {
                    if ((current.condition is JcEqExpr && nextIsTrueBranch) ||
                        (current.condition is JcNeqExpr && !nextIsTrueBranch)
                    ) {
                        // This is a hack: instructions like `return null` in branch of next will be considered only if
                        //  the fact holds (otherwise we could not get there)
                        // Note the absence of 'Zero' here!
                        return@FlowFunction listOf(Tainted(pathComparedWithNull, TaintMark.NULLNESS))
                    }
                }
            } else if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
                val expr = current.condition
                if (pathComparedWithNull != fact.variable) {
                    return@FlowFunction listOf(fact)
                }
                if ((expr is JcEqExpr && nextIsTrueBranch) || (expr is JcNeqExpr && !nextIsTrueBranch)) {
                    // comparedPath is null in this branch
                    return@FlowFunction listOf(TaintZeroFact)
                } else {
                    return@FlowFunction emptyList()
                }
            }
        }

        if (fact is TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact) + generates(current)
        }
        check(fact is Tainted)

        if (current is JcAssignInst) {
            transmitTaintAssign(fact, from = current.rhv, to = current.lhv)
        } else {
            transmitTaintNormal(fact, current)
        }
    }

    private fun transmitTaint(
        fact: Tainted,
        at: JcInst,
        from: JcValue,
        to: JcValue,
    ): Collection<Tainted> = with(traits) {
        buildSet {
            if (fact.mark == TaintMark.NULLNESS) {
                if (fact.variable.isDereferencedAt(traits, at)) {
                    return@buildSet
                }
            }

            val fromPath = convertToPath(from)
            val toPath = convertToPath(to)

            val tail = (fact.variable - fromPath) ?: return@buildSet
            val newPath = toPath + tail
            val newTaint = fact.copy(variable = newPath)
            add(newTaint)
        }
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        at: JcInst,
        from: JcValue, // actual
        to: JcValue, // formal
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        at: JcInst,
        from: JcValue, // formal
        to: JcValue, // actual
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        at: JcInst,
        from: JcValue, // instance
        to: JcThis, // this
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        at: JcInst,
        from: JcThis, // this
        to: JcValue, // instance
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintReturn(
        fact: Tainted,
        at: JcInst,
        from: JcValue,
        to: JcValue,
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst, // FIXME: unused?
    ) = with(traits) {
        FlowFunction<TaintDomainFact> { fact ->
            if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
                if (fact.variable.isDereferencedAt(traits, callStatement)) {
                    return@FlowFunction emptySet()
                }
            }

            val callExpr = getCallExpr(callStatement)
                ?: error("Call statement should have non-null callExpr")

            val callee = getCallee(callExpr)
            val config = getConfigForMethod(callee)

            if (fact == TaintZeroFact) {
                return@FlowFunction buildSet {
                    add(TaintZeroFact)

                    if (callStatement is JcAssignInst) {
                        val toPath = convertToPath(callStatement.lhv)
                        val from = callStatement.rhv
                        if (from is JcNullConstant || (from is JcCallExpr && from.method.method.isNullable == true)) {
                            add(Tainted(toPath, TaintMark.NULLNESS))
                        } else if (from is JcNewArrayExpr && (from.type as JcArrayType).elementType.nullable != false) {
                            val size = (from.type as JcArrayType).dimensions
                            val accessors = List(size) { ElementAccessor }
                            val path = toPath + accessors
                            add(Tainted(path, TaintMark.NULLNESS))
                        }
                    }

                    if (config != null) {
                        val conditionEvaluator = BasicConditionEvaluator(
                            traits,
                            CallPositionToValueResolver(traits, callStatement)
                        )
                        val actionEvaluator = TaintActionEvaluator(
                            CallPositionToAccessPathResolver(traits, callStatement)
                        )

                        // Handle MethodSource config items:
                        for (item in config.filterIsInstance<TaintMethodSource>()) {
                            if (item.condition.accept(conditionEvaluator)) {
                                for (action in item.actionsAfter) {
                                    val result = when (action) {
                                        is AssignMark -> actionEvaluator.evaluate(action)
                                        else -> error("$action is not supported for $item")
                                    }
                                    result.onSome {
                                        addAll(it)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            check(fact is Tainted)

            val statementPassThrough = taintPassThrough(callStatement)
            if (statementPassThrough != null) {
                for ((from, to) in statementPassThrough) {
                    if (convertToPath(from) == fact.variable) {
                        return@FlowFunction setOf(
                            fact,
                            fact.copy(variable = convertToPath(to))
                        )
                    }
                }

                return@FlowFunction setOf(fact)
            }

            if (config != null) {
                // FIXME: adhoc
                if (callee.enclosingClass.name == "java.lang.StringBuilder" && callee.name == "append") {
                    // Skip rules for StringBuilder::append in NPE analysis.
                } else {
                    val facts = mutableSetOf<Tainted>()
                    val conditionEvaluator = FactAwareConditionEvaluator(
                        traits,
                        fact,
                        CallPositionToValueResolver(traits, callStatement)
                    )
                    val actionEvaluator = TaintActionEvaluator(
                        CallPositionToAccessPathResolver(traits, callStatement)
                    )
                    var defaultBehavior = true

                    // Handle PassThrough config items:
                    for (item in config.filterIsInstance<TaintPassThrough>()) {
                        if (item.condition.accept(conditionEvaluator)) {
                            for (action in item.actionsAfter) {
                                val result = when (action) {
                                    is CopyMark -> actionEvaluator.evaluate(action, fact)
                                    is CopyAllMarks -> actionEvaluator.evaluate(action, fact)
                                    is RemoveMark -> actionEvaluator.evaluate(action, fact)
                                    is RemoveAllMarks -> actionEvaluator.evaluate(action, fact)
                                    else -> error("$action is not supported for $item")
                                }
                                result.onSome {
                                    facts += it
                                    defaultBehavior = false
                                }
                            }
                        }
                    }

                    // Handle Cleaner config items:
                    for (item in config.filterIsInstance<TaintCleaner>()) {
                        if (item.condition.accept(conditionEvaluator)) {
                            for (action in item.actionsAfter) {
                                val result = when (action) {
                                    is RemoveMark -> actionEvaluator.evaluate(action, fact)
                                    is RemoveAllMarks -> actionEvaluator.evaluate(action, fact)
                                    else -> error("$action is not supported for $item")
                                }
                                result.onSome {
                                    facts += it
                                    defaultBehavior = false
                                }
                            }
                        }
                    }

                    if (!defaultBehavior) {
                        if (facts.size > 0) {
                            logger.trace { "Got ${facts.size} facts from config for $callee: $facts" }
                        }
                        return@FlowFunction facts
                    } else {
                        // Fall back to the default behavior, as if there were no config at all.
                    }
                }
            }

            // FIXME: adhoc for constructors:
            if (callee.isConstructor) {
                return@FlowFunction listOf(fact)
            }

            // TODO: CONSIDER REFACTORING THIS
            //   Default behavior for "analyzable" method calls is to remove ("temporarily")
            //    all the marks from the 'instance' and arguments, in order to allow them "pass through"
            //    the callee (when it is going to be analyzed), i.e. through "call-to-start" and
            //    "exit-to-return" flow functions.
            //   When we know that we are NOT going to analyze the callee, we do NOT need
            //    to remove any marks from 'instance' and arguments.
            //   Currently, "analyzability" of the callee depends on the fact that the callee
            //    is "accessible" through the JcApplicationGraph::callees().
            if (callee in graph.callees(callStatement)) {

                if (fact.variable.isStatic) {
                    return@FlowFunction emptyList()
                }

                for (actual in callExpr.args) {
                    // Possibly tainted actual parameter:
                    val p = convertToPathOrNull(actual)
                    if (fact.variable.startsWith(p)) {
                        return@FlowFunction emptyList() // Will be handled by summary edge
                    }
                }

                if (callExpr is JcInstanceCallExpr) {
                    // Possibly tainted instance:
                    val p = convertToPathOrNull(callExpr.instance)
                    if (fact.variable.startsWith(p)) {
                        return@FlowFunction emptyList() // Will be handled by summary edge
                    }
                }

            }

            if (callStatement is JcAssignInst) {
                // Possibly tainted lhv:
                val p = convertToPathOrNull(callStatement.lhv)
                if (fact.variable.startsWith(p)) {
                    return@FlowFunction emptyList() // Overridden by rhv
                }
            }

            // The "most default" behaviour is encapsulated here:
            transmitTaintNormal(fact, callStatement)
        }
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        calleeStart: JcInst,
    ) = with(traits) {
        FlowFunction<TaintDomainFact> { fact ->
            val callee = graph.methodOf(calleeStart)

            if (fact == TaintZeroFact) {
                return@FlowFunction obtainPossibleStartFactsBasic(callee)
            }
            check(fact is Tainted)

            val callExpr = getCallExpr(callStatement)
                ?: error("Call statement should have non-null callExpr")

            buildSet {
                // Transmit facts on arguments (from 'actual' to 'formal'):
                val actualParams = callExpr.args
                val formalParams = getArgumentsOf(callee)
                for ((formal, actual) in formalParams.zip(actualParams)) {
                    addAll(
                        transmitTaintArgumentActualToFormal(
                            fact = fact,
                            at = callStatement,
                            from = actual,
                            to = formal
                        )
                    )
                }

                // Transmit facts on instance (from 'instance' to 'this'):
                if (callExpr is JcInstanceCallExpr) {
                    addAll(
                        transmitTaintInstanceToThis(
                            fact = fact,
                            at = callStatement,
                            from = callExpr.instance,
                            to = getThisInstance(callee)
                        )
                    )
                }

                // Transmit facts on static values:
                if (fact.variable.isStatic) {
                    add(fact)
                }
            }
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst, // unused
        exitStatement: JcInst,
    ) = with(traits) {
        FlowFunction<TaintDomainFact> { fact ->
            // TODO: do we even need to return non-empty list for zero fact here?
            if (fact == TaintZeroFact) {
                // return@FlowFunction listOf(Zero)
                return@FlowFunction buildSet {
                    add(TaintZeroFact)
                    if (exitStatement is JcReturnInst && callStatement is JcAssignInst) {
                        // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                        exitStatement.returnValue?.let { returnValue ->
                            if (returnValue is JcNullConstant) {
                                val toPath = convertToPath(callStatement.lhv)
                                add(Tainted(toPath, TaintMark.NULLNESS))
                            }
                        }
                    }
                }
            }
            check(fact is Tainted)

            val callExpr = getCallExpr(callStatement)
                ?: error("Call statement should have non-null callExpr")
            val callee = graph.methodOf(exitStatement)

            buildSet {
                // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
                if (fact.variable.isOnHeap) {
                    val actualParams = callExpr.args
                    val formalParams = getArgumentsOf(callee)
                    for ((formal, actual) in formalParams.zip(actualParams)) {
                        addAll(
                            transmitTaintArgumentFormalToActual(
                                fact = fact,
                                at = callStatement,
                                from = formal,
                                to = actual
                            )
                        )
                    }
                }

                // Transmit facts on instance (from 'this' to 'instance'):
                if (callExpr is JcInstanceCallExpr) {
                    addAll(
                        transmitTaintThisToInstance(
                            fact = fact,
                            at = callStatement,
                            from = getThisInstance(callee),
                            to = callExpr.instance,
                        )
                    )
                }

                // Transmit facts on static values:
                if (fact.variable.isStatic) {
                    add(fact)
                }

                // Transmit facts on return value (from 'returnValue' to 'lhv'):
                if (exitStatement is JcReturnInst && callStatement is JcAssignInst) {
                    // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                    exitStatement.returnValue?.let { returnValue ->
                        addAll(
                            transmitTaintReturn(
                                fact = fact,
                                at = callStatement,
                                from = returnValue,
                                to = callStatement.lhv,
                            )
                        )
                    }
                }
            }
        }
    }
}

// TODO: class BackwardNpeFlowFunctions
