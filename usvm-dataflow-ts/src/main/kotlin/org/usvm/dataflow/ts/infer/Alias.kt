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
import org.jacodb.ets.base.EtsCallExpr
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsConstant
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNewArrayExpr
import org.jacodb.ets.base.EtsNewExpr
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsRef
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.usvm.dataflow.ifds.ElementAccessor
import org.usvm.dataflow.ifds.FieldAccessor

sealed interface Allocation {
    class New : Allocation
    data class Arg(val index: Int) : Allocation
    class CallResult : Allocation
    class Imm : Allocation
}

// class Allocation2(val index: Int?) {
//     override fun equals(other: Any?): Boolean {
//         if (other === this) return true
//         if (other !is Allocation2) return false
//         if (index != null) return index == other.index
//         return false
//     }
// }

class AliasInfo(
    // B: Base -> Object
    val B: Map<AccessPathBase, Allocation>,
    // val B: PersistentMap<AccessPathBase, Allocation>,
    // F: Object x Field -> Object
    val F: Map<Allocation, Map<String, Allocation>>,
    // val F: PersistentMap<Allocation, PersistentMap<String, Allocation>>,
) {
    // A: Base x Field* -> Object
    fun find(path: AccessPath): Allocation? {
        val b = B[path.base] ?: return null
        if (path.accesses.isEmpty()) return b
        return when (val a = path.accesses.single()) {
            is FieldAccessor -> {
                val f = F[b] ?: return null
                f[a.name]
            }

            is ElementAccessor -> null
        }
    }

    fun merge(other: AliasInfo): AliasInfo {
        // Intersect B:
        val newB = this.B.filter { (base, obj) -> other.B[base] == obj }.toMap()
        // val newB = this.B.mutate { newB ->
        //     for ((base, obj) in this.B) {
        //         if (other.B[base] != obj) {
        //             newB.remove(base)
        //         }
        //     }
        // }

        // Intersect F:
        val newF = mutableMapOf<Allocation, Map<String, Allocation>>()
        for ((obj, fields) in this.F) {
            val otherFields = other.F[obj]
            if (otherFields != null) {
                newF[obj] = fields.filter { (field, alloc) -> otherFields[field] == alloc }.toMap()
            }
        }
        // val newF = this.F.mutate { newF ->
        //     for ((obj, fields) in this.F) {
        //         val otherFields = other.F[obj]
        //         if (otherFields != null) {
        //             // newF[obj] = fields.filter { (field, alloc) -> otherFields[field] == alloc }.toPersistentMap()
        //             newF[obj] = fields.mutate { newFields ->
        //                 for ((field, alloc) in fields) {
        //                     if (otherFields[field] != alloc) {
        //                         newFields.remove(field)
        //                     }
        //                 }
        //             }
        //         } else {
        //             newF.remove(obj)
        //         }
        //     }
        // }

        return AliasInfo(newB, newF)
    }

    /**
     * Returns the set of access paths that must alias with the given path (excluding the path itself).
     */
    fun getAliases(path: AccessPath): Set<AccessPath> {
        val obj = find(path) ?: return emptySet()
        return getAliases(obj) - path
    }

    private fun getAliases(obj: Allocation): Set<AccessPath> {
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
            val (cur, path) = queue.removeFirst()
            // TODO: eliminate loops as in computeAliases via DFS with PATH/STACK
            // TODO: think about loop-edges
            if (path.size > 10) continue
            if (cur in invB) {
                for (base in invB[cur]!!) {
                    paths.add(AccessPath(base, path.reversed()))
                }
            }
            if (cur in invF) {
                for ((field, objs) in invF[cur]!!) {
                    for (alloc in objs) {
                        queue.add(alloc to path + FieldAccessor(field))
                    }
                }
            }
        }

        return paths
    }
}

fun computeAliases(method: EtsMethod): Map<EtsStmt, Pair<AliasInfo, AliasInfo>> {
    val preAliases = mutableMapOf<EtsStmt, AliasInfo>()
    val postAliases = mutableMapOf<EtsStmt, AliasInfo>()

    val root = method.cfg.stmts[0]
    val queue = ArrayDeque(listOf(root))
    val preds = mutableMapOf<EtsStmt, MutableList<EtsStmt>>()
    val order = mutableListOf<EtsStmt>()
    val visited = mutableSetOf<EtsStmt>()

    while (queue.isNotEmpty()) {
        val cur = queue.first()

        if (visited.add(cur)) {
            for (next in method.cfg.successors(cur)) {
                if (next !in visited) {
                    queue.addFirst(next)
                }
            }
        } else {
            order.add(cur)
            queue.removeFirst()
        }
    }

    fun computePostAliases(stmt: EtsStmt): AliasInfo {
        if (stmt in postAliases) return postAliases[stmt]!!

        val pre = preAliases[stmt]!!
        val newB = pre.B.toMap(HashMap())
        val newF = pre.F.mapValuesTo(HashMap()) { it.value.toMutableMap() }

        if (stmt is EtsAssignStmt) {
            val lhv = stmt.lhv
            val rhv = stmt.rhv

            if (rhv is EtsLocal || rhv is EtsRef || (rhv is EtsCastExpr && rhv.arg is EtsRef)) {
                val lhs = lhv.toPath()
                val rhs = rhv.toPath()

                if (rhv is EtsParameterRef) {
                    check(lhs.accesses.isEmpty())
                    newB[lhs.base] = Allocation.Arg(rhv.index)
                } else {
                    if (lhs.accesses.isEmpty() && rhs.accesses.isEmpty()) {
                        // x := y
                        newB[lhs.base] = newB.computeIfAbsent(rhs.base) { Allocation.Imm() }
                    } else if (lhs.accesses.isEmpty()) {
                        // x := y.f  OR  x := y[i]
                        when (val a = rhs.accesses.single()) {
                            is FieldAccessor -> {
                                // x := y.f
                                val b: Allocation = newB.computeIfAbsent(rhs.base) { Allocation.Imm() }
                                val f: MutableMap<String, Allocation> = newF.computeIfAbsent(b) { hashMapOf() }
                                newB[lhs.base] = f.computeIfAbsent(a.name) { Allocation.Imm() }
                            }

                            is ElementAccessor -> {
                                // x := y[i]
                                newB.remove(lhs.base)
                            }
                        }
                    } else if (rhs.accesses.isEmpty()) {
                        // x.f := y  OR  x[i] := y
                        when (val a = lhs.accesses.single()) {
                            is FieldAccessor -> {
                                // x.f := y
                                val b: Allocation = newB.computeIfAbsent(rhs.base) { Allocation.Imm() }
                                val f: MutableMap<String, Allocation> = newF.computeIfAbsent(b) { hashMapOf() }
                                f[a.name] = newB.computeIfAbsent(rhs.base) { Allocation.Imm() }
                            }

                            is ElementAccessor -> {
                                // x[i] := y
                                // do nothing
                            }
                        }
                    } else {
                        error("Incorrect 3AC: $stmt")
                    }
                }
            }

            if (rhv is EtsConstant || (rhv is EtsCastExpr && rhv.arg is EtsConstant)) {
                val lhs = lhv.toPath()
                if (lhs.accesses.isEmpty()) {
                    // x := const
                    newB.remove(lhs.base)
                } else {
                    when (val a = lhs.accesses.single()) {
                        is FieldAccessor -> {
                            // x.f := const
                            val b = newB.computeIfAbsent(lhs.base) { Allocation.Imm() }
                            val f = newF.computeIfAbsent(b) { hashMapOf() }
                            f.remove(a.name)
                        }

                        is ElementAccessor -> {
                            // x[i] := const
                            // do nothing
                        }
                    }
                }
            }

            if (rhv is EtsNewExpr || rhv is EtsNewArrayExpr) {
                val lhs = lhv.toPath()
                if (lhs.accesses.isEmpty()) {
                    // x := new()
                    newB.computeIfAbsent(lhs.base) { Allocation.New() }
                } else {
                    when (val a = lhs.accesses.single()) {
                        is FieldAccessor -> {
                            // x.f := new()
                            val b = newB.computeIfAbsent(lhs.base) { Allocation.Imm() }
                            val f = newF.computeIfAbsent(b) { hashMapOf() }
                            f[a.name] = Allocation.New()
                        }

                        is ElementAccessor -> {
                            // x[i] := new()
                            // do nothing
                        }
                    }
                }
            }

            if (rhv is EtsCastExpr) {
                check(rhv.arg !is EtsCallExpr)
                check(rhv.arg is EtsLocal || rhv.arg is EtsRef || rhv.arg is EtsConstant)
            }

            if (rhv is EtsCallExpr) {
                val lhs = lhv.toPath()
                check(lhs.accesses.isEmpty())
                newB[lhs.base] = Allocation.CallResult()
            }
        }

        val newInfo = AliasInfo(newB, newF)
        postAliases[stmt] = newInfo
        return newInfo
    }

    fun computePreAliases(stmt: EtsStmt): AliasInfo {
        if (stmt in preAliases) return preAliases[stmt]!!

        val merged = preds[stmt].orEmpty()
            // .map { computePostAliases(it) }
            .map { postAliases[it]!! }
            .reduceOrNull { a, b -> a.merge(b) }
            ?: AliasInfo(emptyMap(), emptyMap())

        preAliases[stmt] = merged
        return merged
    }

    for (stmt in order) {
        computePreAliases(stmt)
        computePostAliases(stmt)
    }

    for (stmt in method.cfg.stmts) {
        check(stmt in preAliases)
        check(stmt in postAliases)
    }

    return method.cfg.stmts.associateWithTo(HashMap()) { stmt -> Pair(preAliases[stmt]!!, postAliases[stmt]!!) }
}
