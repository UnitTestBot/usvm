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

import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.usvm.dataflow.ifds.Accessor
import org.usvm.dataflow.ifds.FieldAccessor

sealed interface Allocation {
    class New : Allocation
    data class Arg(val index: Int) : Allocation
    class CallResult : Allocation
}

class AliasingInfo(
    // B: Base -> Object
    val B: Map<AccessPathBase, Allocation>,
    // F: Object x Field -> Object
    val F: Map<Allocation, Map<String, Allocation>>,
) {
    fun find(path: AccessPath): Allocation? {
        val b = B[path.base] ?: return null
        if (path.accesses.isEmpty()) return b
        val f = F[b] ?: return null
        val a = path.accesses.single()
        check(a is FieldAccessor) {
            "Sorry, arrays are not supported, yet"
        }
        return f[a.name]
    }

    fun merge(other: AliasingInfo): AliasingInfo {
        // Intersect B:
        val newB = this.B.filter { (base, obj) -> other.B[base] == obj }.toMap()

        // Intersect F:
        val newF = mutableMapOf<Allocation, Map<String, Allocation>>()
        for ((obj, fields) in this.F) {
            val otherFields = other.F[obj]
            if (otherFields != null) {
                newF[obj] = fields.filter { (field, alloc) -> otherFields[field] == alloc }.toMap()
            }
        }

        return AliasingInfo(newB, newF)
    }

    fun getAliases(obj: Allocation): Set<AccessPath> {
        // TODO: traverse graph backward
        val paths = mutableSetOf<AccessPath>()

        val invF = mutableMapOf<Allocation, MutableMap<String, MutableList<Allocation>>>()
        for ((obj1, fields) in F) {
            for ((field, obj2) in fields) {
                invF.computeIfAbsent(obj2) { mutableMapOf() }
                    .computeIfAbsent(field) { mutableListOf() }
                    .add(obj1)
            }
        }

        val invB = mutableMapOf<Allocation, MutableList<AccessPathBase>>()
        for ((base, alloc) in B) {
            invB.computeIfAbsent(alloc) { mutableListOf() }
                .add(base)
        }

        val queue = ArrayDeque<Pair<Allocation, List<FieldAccessor>>>(listOf(obj to emptyList()))
        while (queue.isNotEmpty()) {
            val (cur, accessors) = queue.removeFirst()
            if (cur in invB) {
                for (base in invB[cur]!!) {
                    paths.add(AccessPath(base, accessors.reversed()))
                }
            }
            if (cur in invF) {
                for ((field, objs) in invF[cur]!!) {
                    for (alloc in objs) {
                        queue.add(alloc to accessors + FieldAccessor(field))
                    }
                }
            }
        }

        return paths
    }

    fun getSet(path: AccessPath) : Set<AccessPath> {
        val obj = find(path) ?: return emptySet()
        return getAliases(obj)
    }
}

fun computeAliases(method: EtsMethod): Map<EtsStmt, AliasingInfo> {
    val aliases = mutableMapOf<EtsStmt, AliasingInfo>()

    fun compute(stmt: EtsStmt): AliasingInfo {
        if (stmt in aliases) return aliases[stmt]!!

        val preds = method.cfg.predecessors(stmt).map { compute(it) }
        val merged = preds.reduceOrNull { a, b -> a.merge(b) } ?: AliasingInfo(emptyMap(), emptyMap())

        val newInfo = TODO("handle assignment")

        aliases[stmt] = newInfo
        return newInfo
    }

    for (stmt in method.cfg.stmts) {
        compute(stmt)
    }

    return aliases
}
