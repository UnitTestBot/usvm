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

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsRef
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.usvm.algorithms.DisjointSets

fun computeAliases(method: EtsMethod): Map<EtsStmt, DisjointSets<AccessPath>> {
    val aliases = mutableMapOf<EtsStmt, DisjointSets<AccessPath>>()
    var dsu = DisjointSets<AccessPath>()

    for (stmt in method.cfg.stmts) {
        when (stmt) {
            is EtsAssignStmt -> {
                if (stmt.rhv is EtsRef || (stmt.rhv is EtsCastExpr && (stmt.rhv as EtsCastExpr).arg is EtsRef)) {
                    val lhs = stmt.lhv.toPath()
                    val rhs = stmt.rhv.toPath()

                    dsu = dsu.clone()
                    dsu.union(lhs, rhs)

                    if (rhs.accesses.isNotEmpty()) {
                        check(rhs.accesses.size == 1)
                        val accessor = rhs.accesses.single()
                        for (alias in dsu.getSet(AccessPath(rhs.base, emptyList()))) {
                            dsu.union(lhs , alias + accessor)
                        }
                    }
                }
            }
        }
        aliases[stmt] = dsu
    }

    return aliases
}
