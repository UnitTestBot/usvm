/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.graph

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt

private class BackwardEtsApplicationGraphImpl(
    val forward: EtsApplicationGraph,
) : EtsApplicationGraph {

    override val cp: EtsScene
        get() = forward.cp

    override fun predecessors(node: EtsStmt) = forward.successors(node)
    override fun successors(node: EtsStmt) = forward.predecessors(node)

    override fun callees(node: EtsStmt) = forward.callees(node)
    override fun callers(method: EtsMethod) = forward.callers(method)

    override fun entryPoints(method: EtsMethod) = forward.exitPoints(method)
    override fun exitPoints(method: EtsMethod) = forward.entryPoints(method)

    override fun methodOf(node: EtsStmt) = forward.methodOf(node)
}

val EtsApplicationGraph.reversed: EtsApplicationGraph
    get() = when (this) {
        is BackwardEtsApplicationGraphImpl -> this.forward
        else -> BackwardEtsApplicationGraphImpl(this)
    }
