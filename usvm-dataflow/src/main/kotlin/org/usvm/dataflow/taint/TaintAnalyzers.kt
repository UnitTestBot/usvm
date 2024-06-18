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

package org.usvm.dataflow.taint

import mu.KLogging
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintMethodSink
import org.usvm.dataflow.ifds.Analyzer
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.Reason
import org.usvm.dataflow.util.Traits

private val logger = object : KLogging() {}.logger

context(Traits<Method, Statement>)
class TaintAnalyzer<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
    private val getConfigForMethod: (Method) -> List<TaintConfigurationItem>?,
) : Analyzer<TaintDomainFact, TaintEvent<Statement>, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override val flowFunctions: ForwardTaintFlowFunctions<Method, Statement> by lazy {
        ForwardTaintFlowFunctions(graph, getConfigForMethod)
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(graph.methodOf(statement))
    }

    override fun handleNewEdge(
        edge: TaintEdge<Statement>,
    ): List<TaintEvent<Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }

        run {
            val callExpr = edge.to.statement.getCallExpr() ?: return@run

            val callee = callExpr.callee

            val config = getConfigForMethod(callee) ?: return@run

            // TODO: not always we want to skip sinks on Zero facts.
            //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
            if (edge.to.fact !is Tainted) {
                return@run
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = org.usvm.dataflow.config.FactAwareConditionEvaluator(
                edge.to.fact,
                org.usvm.dataflow.config.CallPositionToValueResolver(edge.to.statement),
            )
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    val message = item.ruleNote
                    val vulnerability = TaintVulnerability(message, sink = edge.to, rule = item)
                    logger.info {
                        "Found sink=${vulnerability.sink} in ${vulnerability.method} on $item"
                    }
                    add(NewVulnerability(vulnerability))
                }
            }
        }

        if (TaintAnalysisOptions.UNTRUSTED_LOOP_BOUND_SINK) {
            val statement = edge.to.statement
            val fact = edge.to.fact
            if (fact is Tainted && fact.mark.name == "UNTRUSTED") {
                val branchExprCondition = statement.getBranchExprCondition()
                if (branchExprCondition != null && statement.isLoopHead()) {
                    val conditionOperands = branchExprCondition.getValues()
                    for (s in conditionOperands) {
                        val p = s.toPath()
                        if (p == fact.variable) {
                            val message = "Untrusted loop bound"
                            val vulnerability = TaintVulnerability(message, sink = edge.to)
                            add(NewVulnerability(vulnerability))
                        }
                    }
                }
            }
        }
        if (TaintAnalysisOptions.UNTRUSTED_ARRAY_SIZE_SINK) {
            val statement = edge.to.statement
            val fact = edge.to.fact
            if (fact is Tainted && fact.mark.name == "UNTRUSTED") {
                val arrayAllocation = statement.getArrayAllocation()
                if (arrayAllocation != null) {
                    for (arg in arrayAllocation.getValues()) {
                        if (arg.toPath() == fact.variable) {
                            val message = "Untrusted array size"
                            val vulnerability = TaintVulnerability(message, sink = edge.to)
                            add(NewVulnerability(vulnerability))
                        }
                    }
                }
            }
        }
        if (TaintAnalysisOptions.UNTRUSTED_INDEX_ARRAY_ACCESS_SINK) {
            val statement = edge.to.statement
            val fact = edge.to.fact
            if (fact is Tainted && fact.mark.name == "UNTRUSTED") {
                val arrayAccessIndex = statement.getArrayAccessIndex()
                if (arrayAccessIndex != null) {
                    if (arrayAccessIndex.toPath() == fact.variable) {
                        val message = "Untrusted index for access array"
                        val vulnerability = TaintVulnerability(message, sink = edge.to)
                        add(NewVulnerability(vulnerability))
                    }
                }
            }
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex<Statement>,
        callee: TaintVertex<Statement>,
    ): List<TaintEvent<Statement>> = buildList {
        add(EdgeForOtherRunner(TaintEdge(callee, callee), Reason.CrossUnitCall(caller)))
    }
}

context(Traits<Method, Statement>)
class BackwardTaintAnalyzer<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
) : Analyzer<TaintDomainFact, TaintEvent<Statement>, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override val flowFunctions: BackwardTaintFlowFunctions<Method, Statement> by lazy {
        BackwardTaintFlowFunctions(graph)
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(graph.methodOf(statement))
    }

    override fun handleNewEdge(
        edge: TaintEdge<Statement>,
    ): List<TaintEvent<Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(EdgeForOtherRunner(Edge(edge.to, edge.to), reason = Reason.External))
        }
    }

    override fun handleCrossUnitCall(
        caller: TaintVertex<Statement>,
        callee: TaintVertex<Statement>,
    ): List<TaintEvent<Statement>> {
        return emptyList()
    }
}
