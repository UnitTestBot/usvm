package org.usvm.ts.pbt.gen

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBinaryExpr
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsUnaryExpr

/**
 * Mines number/string literals from a method body (a classic PBT trick:
 * biasing generated inputs towards program constants and their neighbourhood
 * dramatically improves the odds of hitting comparison branches).
 */
data class MinedConstants(
    val numbers: List<Double>,
    val strings: List<String>,
) {
    companion object {
        fun of(method: EtsMethod): MinedConstants {
            val numbers = linkedSetOf<Double>()
            val strings = linkedSetOf<String>()

            fun visit(e: EtsEntity) {
                when (e) {
                    is EtsNumberConstant -> numbers += e.value
                    is EtsStringConstant -> strings += e.value
                    is EtsBinaryExpr -> {
                        visit(e.left)
                        visit(e.right)
                    }

                    is EtsUnaryExpr -> visit(e.arg)
                    is EtsCastExpr -> visit(e.arg)
                    is EtsInstanceOfExpr -> visit(e.arg)
                    is EtsNewArrayExpr -> visit(e.size)
                    else -> Unit
                }
            }

            for (stmt in method.cfg.stmts) {
                when (stmt) {
                    is EtsAssignStmt -> visit(stmt.rhv)
                    is EtsIfStmt -> visit(stmt.condition)
                    is EtsReturnStmt -> stmt.returnValue?.let { visit(it) }
                    is EtsCallStmt -> stmt.expr.args.forEach { visit(it) }
                    else -> Unit
                }
            }
            return MinedConstants(numbers.toList(), strings.toList())
        }
    }
}
