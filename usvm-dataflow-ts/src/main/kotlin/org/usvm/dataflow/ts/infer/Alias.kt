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

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
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

sealed interface Allocation {
    class New : Allocation
    data class Arg(val index: Int) : Allocation
    class CallResult : Allocation
    class Imm : Allocation
}

class AliasInfo(
    // B: Base -> Object
    val baseToAlloc: PersistentMap<AccessPathBase, Allocation>,
    // F: Object x Field -> Object
    val allocToFields: PersistentMap<Allocation, PersistentMap<String, Allocation>>,
) {
    // A: Base x Field* -> Object
    fun find(path: AccessPath): Allocation? {
        val b = baseToAlloc[path.base] ?: return null
        if (path.accesses.isEmpty()) return b
        // TODO: handle multiple accesses
        check(path.accesses.size == 1)
        return when (val a = path.accesses.single()) {
            is FieldAccessor -> {
                val f = allocToFields[b] ?: return null
                f[a.name]
            }

            is ElementAccessor -> {
                null
            }
        }
    }

    fun merge(other: AliasInfo): AliasInfo {
        // Intersect B:
        val newB = persistentHashMapOf<AccessPathBase, Allocation>().mutate { newB ->
            for ((base, obj) in this.baseToAlloc) {
                if (other.baseToAlloc[base] == obj) {
                    newB[base] = obj
                }
            }
        }

        // Intersect F:
        val newF = persistentHashMapOf<Allocation, PersistentMap<String, Allocation>>().mutate { newF ->
            for ((obj, fields) in this.allocToFields) {
                newF[obj] = persistentHashMapOf<String, Allocation>().mutate { newFields ->
                    val otherFields = other.allocToFields[obj]
                    if (otherFields != null) {
                        for ((field, alloc) in fields) {
                            if (otherFields[field] == alloc) {
                                newFields[field] = alloc
                            }
                        }
                    }
                }
            }
        }

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

        val allocToFields = hashMapOf<Allocation, MutableMap<String, MutableList<Allocation>>>()
        for ((obj1, fields) in this.allocToFields) {
            for ((field, obj2) in fields) {
                allocToFields.computeIfAbsent(obj2) { hashMapOf() }
                allocToFields.computeIfAbsent(obj2) { hashMapOf() }
                    .computeIfAbsent(field) { mutableListOf() }
                    .add(obj1)
            }
        }

        val allocToBases = hashMapOf<Allocation, MutableList<AccessPathBase>>()
        for ((base, alloc) in baseToAlloc) {
            allocToBases.computeIfAbsent(alloc) { mutableListOf() }
                .add(base)
        }

        val queue = ArrayDeque<Pair<Allocation, List<FieldAccessor>>>(listOf(obj to emptyList()))
        while (queue.isNotEmpty()) {
            val (cur, path) = queue.removeFirst()
            // TODO: eliminate loops as in computeAliases via DFS with PATH/STACK
            // TODO: think about loop-edges
            if (path.size > MAX_PATH_SIZE) continue
            if (cur in allocToBases) {
                for (base in allocToBases.getValue(cur)) {
                    paths.add(AccessPath(base, path.reversed()))
                }
            }
            if (cur in allocToFields) {
                for ((field, objs) in allocToFields.getValue(cur)) {
                    for (alloc in objs) {
                        queue.add(alloc to path + FieldAccessor(field))
                    }
                }
            }
        }

        return paths
    }

    companion object {
        // Maximum number of fields in an access path. Used to avoid infinite loops.
        private const val MAX_PATH_SIZE = 10
    }
}

fun computeAliases(method: EtsMethod): Map<EtsStmt, Pair<AliasInfo, AliasInfo>> {
    val preAliases = mutableMapOf<EtsStmt, AliasInfo>()
    val postAliases = mutableMapOf<EtsStmt, AliasInfo>()

    val visited: MutableSet<EtsStmt> = hashSetOf()
    val order: MutableList<EtsStmt> = mutableListOf()
    val preds: MutableMap<EtsStmt, MutableList<EtsStmt>> = hashMapOf()

    fun postOrderDfs(node: EtsStmt) {
        if (visited.add(node)) {
            for (next in method.cfg.successors(node)) {
                if (next !in visited) {
                    preds.computeIfAbsent(next) { mutableListOf() } += node
                }
                postOrderDfs(next)
            }
            order += node
        }
    }

    val root = method.cfg.stmts[0]
    postOrderDfs(root)
    order.reverse()

    fun computePostAliases(stmt: EtsStmt): AliasInfo {
        if (stmt in postAliases) return postAliases.getValue(stmt)

        val pre = preAliases.getValue(stmt)
        var newF = pre.allocToFields
        val newB = pre.baseToAlloc.mutate { newB ->
            newF = newF.mutate { newF ->
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
                                        newF[b] = newF.getOrElse(b) { persistentHashMapOf() }.mutate { f ->
                                            newB[lhs.base] = f.computeIfAbsent(a.name) { Allocation.Imm() }
                                        }
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
                                        newF[b] = newF.getOrElse(b) { persistentHashMapOf() }.mutate { f ->
                                            f[a.name] = newB.computeIfAbsent(rhs.base) { Allocation.Imm() }
                                        }
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
                                    newF[b] = newF.getOrElse(b) { persistentHashMapOf() }.mutate { f ->
                                        f.remove(a.name)
                                    }
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
                                    newF[b] = newF.getOrElse(b) { persistentHashMapOf() }.mutate { f ->
                                        f[a.name] = Allocation.New()
                                    }
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
            }
        }

        val newInfo = AliasInfo(newB, newF)
        postAliases[stmt] = newInfo
        return newInfo
    }

    fun computePreAliases(stmt: EtsStmt): AliasInfo {
        if (stmt in preAliases) return preAliases.getValue(stmt)

        val merged = preds[stmt]
            ?.map { postAliases.getValue(it) }
            ?.reduceOrNull { a, b -> a.merge(b) }
            ?: AliasInfo(persistentHashMapOf(), persistentHashMapOf())

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

    return method.cfg.stmts.associateWithTo(HashMap()) { stmt ->
        Pair(preAliases.getValue(stmt), postAliases.getValue(stmt))
    }
}
