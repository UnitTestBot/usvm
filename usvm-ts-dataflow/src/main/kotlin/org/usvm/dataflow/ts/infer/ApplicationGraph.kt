/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.graph.EtsApplicationGraphImpl

fun createApplicationGraph(cp: EtsScene): EtsApplicationGraph {
    val base = EtsApplicationGraphImpl(cp)
    val explicit = EtsApplicationGraphWithExplicitEntryPoint(base)
    return explicit
}

class EtsApplicationGraphWithExplicitEntryPoint(
    private val graph: EtsApplicationGraphImpl,
) : EtsApplicationGraph {

    override val cp: EtsScene
        get() = graph.cp

    override fun methodOf(node: EtsStmt): EtsMethod = node.location.method

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> = graph.exitPoints(method)

    private fun methodEntryPoint(method: EtsMethod): EtsStmt =
        EtsNopStmt(EtsStmtLocation(method, index = -1))

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> = sequenceOf(methodEntryPoint(method))

    override fun callers(method: EtsMethod): Sequence<EtsStmt> = graph.callers(method)

    override fun callees(node: EtsStmt): Sequence<EtsMethod> = graph.callees(node)

    override fun successors(node: EtsStmt): Sequence<EtsStmt> {
        if (node.location.index == -1) {
            require(node is EtsNopStmt)
            return graph.entryPoints(node.method)
        }
        return graph.successors(node)
    }

    override fun predecessors(node: EtsStmt): Sequence<EtsStmt> {
        if (node.location.index == -1) {
            require(node is EtsNopStmt)
            return emptySequence()
        }
        if (node.location.index == 0) {
            return entryPoints(node.method)
        }
        return graph.predecessors(node)
    }
}
