package org.usvm.dataflow.ts.util

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.model.EtsMethod

private val realLocalsCache: MutableMap<EtsMethod, Set<EtsLocal>> = hashMapOf()

/**
 * Returns the set of "real" locals in this method, i.e. the locals that are assigned to in the method.
 */
fun EtsMethod.getRealLocals(): Set<EtsLocal> =
    realLocalsCache.computeIfAbsent(this) {
        cfg.stmts
            .asSequence()
            .filterIsInstance<EtsAssignStmt>()
            .map { it.lhv }
            .filterIsInstance<EtsLocal>()
            .toHashSet()
    }
