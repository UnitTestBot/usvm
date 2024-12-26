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

package org.usvm.dataflow.jvm.unused

import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.usvm.dataflow.graph.ApplicationGraph
import org.usvm.dataflow.ifds.FlowFunction
import org.usvm.dataflow.ifds.FlowFunctions
import org.usvm.dataflow.ifds.isOnHeap
import org.usvm.dataflow.util.Traits

class UnusedVariableFlowFunctions<Method, Statement>(
    private val traits: Traits<Method, Statement>,
    private val graph: ApplicationGraph<Method, Statement>,
) : FlowFunctions<UnusedVariableDomainFact, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override fun obtainPossibleStartFacts(
        method: Method,
    ): Collection<UnusedVariableDomainFact> {
        return setOf(UnusedVariableZeroFact)
    }

    override fun obtainSequentFlowFunction(
        current: Statement,
        next: Statement,
    ) = with(traits) {
        FlowFunction<UnusedVariableDomainFact> { fact ->
            if (current !is CommonAssignInst) {
                return@FlowFunction setOf(fact)
            }

            if (fact == UnusedVariableZeroFact) {
                val toPath = convertToPath(current.lhv)
                if (!toPath.isOnHeap) {
                    return@FlowFunction setOf(UnusedVariableZeroFact, UnusedVariable(toPath, current))
                } else {
                    return@FlowFunction setOf(UnusedVariableZeroFact)
                }
            }
            check(fact is UnusedVariable)

            val toPath = convertToPath(current.lhv)
            val default = if (toPath == fact.variable) emptySet() else setOf(fact)
            val fromPath = convertToPathOrNull(current.rhv)
                ?: return@FlowFunction default

            if (fromPath.isOnHeap || toPath.isOnHeap) {
                return@FlowFunction default
            }

            if (fromPath == fact.variable) {
                return@FlowFunction default + fact.copy(variable = toPath)
            }

            default
        }
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement,
    ) = obtainSequentFlowFunction(callStatement, returnSite)

    override fun obtainCallToStartFlowFunction(
        callStatement: Statement,
        calleeStart: Statement,
    ) = with(traits) {
        FlowFunction<UnusedVariableDomainFact> { fact ->
            val callExpr = getCallExpr(callStatement)
                ?: error("Call statement should have non-null callExpr")

            if (fact == UnusedVariableZeroFact) {
                // FIXME: use common?
                if (callExpr !is JcStaticCallExpr && callExpr !is JcSpecialCallExpr) {
                    return@FlowFunction setOf(UnusedVariableZeroFact)
                }
                return@FlowFunction buildSet {
                    add(UnusedVariableZeroFact)
                    val callee = graph.methodOf(calleeStart)
                    val formalParams = getArgumentsOf(callee)
                    for (formal in formalParams) {
                        add(UnusedVariable(convertToPath(formal), callStatement))
                    }
                }
            }
            check(fact is UnusedVariable)

            emptySet()
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement,
        exitStatement: Statement,
    ) = FlowFunction<UnusedVariableDomainFact> { fact ->
        if (fact == UnusedVariableZeroFact) {
            setOf(UnusedVariableZeroFact)
        } else {
            emptySet()
        }
    }
}
