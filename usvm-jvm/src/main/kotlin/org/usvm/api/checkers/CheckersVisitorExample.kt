package org.usvm.api.checkers

import io.ksmt.utils.cast
import org.jacodb.api.JcClasspath
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcBinaryExpr
import org.jacodb.api.cfg.JcCallInst
import org.jacodb.api.cfg.JcCatchInst
import org.jacodb.api.cfg.JcDivExpr
import org.jacodb.api.cfg.JcEnterMonitorInst
import org.jacodb.api.cfg.JcExitMonitorInst
import org.jacodb.api.cfg.JcGotoInst
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstVisitor
import org.jacodb.api.cfg.JcRemExpr
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcSwitchInst
import org.jacodb.api.cfg.JcThrowInst
import org.jacodb.api.ext.int
import org.usvm.UBvSort

// NOTE: THIS FILE MUST NOT BE MERGED!

class CheckersVisitorExample(
    private val usvmCheckersApi: UCheckersApi,
    private val cp: JcClasspath,
) : JcInstVisitor<Unit> {
    override fun visitJcAssignInst(inst: JcAssignInst) {
        val ctx = usvmCheckersApi.ctx

        val expr = inst.rhv
        if (expr is JcBinaryExpr && (expr is JcDivExpr || expr is JcRemExpr) && expr.rhv.type == cp.int) {
            val divider = usvmCheckersApi.resolveValue(expr.rhv) ?: return
            val sort = divider.sort
            if (sort !is UBvSort) {
                return
            }

            with(ctx) {
                val eqZero = mkEq(divider.cast(), mkBv(42, sort))
                val satResult = usvmCheckersApi.checkSat(eqZero)

                if (satResult is USatCheckResult) {
                    println("Division by 42 found")
                }
            }
        }
    }

    override fun visitExternalJcInst(inst: JcInst) {
        /*TODO("Not yet implemented")*/
    }

    override fun visitJcCallInst(inst: JcCallInst) {
        /*TODO("Not yet implemented")*/
    }

    override fun visitJcCatchInst(inst: JcCatchInst) {
        /*TODO("Not yet implemented")*/
    }

    override fun visitJcEnterMonitorInst(inst: JcEnterMonitorInst) {
        /*TODO("Not yet implemented")*/
    }

    override fun visitJcExitMonitorInst(inst: JcExitMonitorInst) {
        /*TODO("Not yet implemented")*/
    }

    override fun visitJcGotoInst(inst: JcGotoInst) {
        /*TODO("Not yet implemented")*/
    }

    override fun visitJcIfInst(inst: JcIfInst) {
        /*TODO("Not yet implemented")*/
    }

    override fun visitJcReturnInst(inst: JcReturnInst) {
        /*TODO("Not yet implemented")*/
    }

    override fun visitJcSwitchInst(inst: JcSwitchInst) {
        /*TODO("Not yet implemented")*/
    }

    override fun visitJcThrowInst(inst: JcThrowInst) {
        /*TODO("Not yet implemented")*/
    }
}
