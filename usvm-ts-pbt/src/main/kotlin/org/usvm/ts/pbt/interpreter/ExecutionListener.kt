package org.usvm.ts.pbt.interpreter

import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt

/**
 * Observation hooks for concrete execution.
 * Coverage tracking and runtime type profiling are implemented as listeners.
 */
interface ExecutionListener {
    fun onMethodEnter(method: EtsMethod, thisValue: VValue, args: List<VValue>) {}
    fun onMethodExit(method: EtsMethod, result: VValue) {}
    fun onStmt(stmt: EtsStmt) {}
    fun onBranch(ifStmt: EtsIfStmt, taken: EtsStmt, condition: Boolean) {}

    companion object {
        val NONE: ExecutionListener = object : ExecutionListener {}

        fun composite(listeners: List<ExecutionListener>): ExecutionListener =
            object : ExecutionListener {
                override fun onMethodEnter(method: EtsMethod, thisValue: VValue, args: List<VValue>) =
                    listeners.forEach { it.onMethodEnter(method, thisValue, args) }

                override fun onMethodExit(method: EtsMethod, result: VValue) =
                    listeners.forEach { it.onMethodExit(method, result) }

                override fun onStmt(stmt: EtsStmt) =
                    listeners.forEach { it.onStmt(stmt) }

                override fun onBranch(ifStmt: EtsIfStmt, taken: EtsStmt, condition: Boolean) =
                    listeners.forEach { it.onBranch(ifStmt, taken, condition) }
            }
    }
}
