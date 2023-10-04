package org.usvm.samples.checkers

import io.ksmt.utils.asExpr
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
import org.usvm.UBoolExpr
import org.usvm.api.checkers.JcCheckerApi
import org.usvm.api.checkers.JcCheckerSatResult

interface JcAssignInstChecker : JcInstVisitor<Unit> {
    val api: JcCheckerApi

    fun matchAst(inst: JcAssignInst): Boolean

    fun checkSymbolic(inst: JcAssignInst): UBoolExpr

    fun reportError(inst: JcAssignInst)

    override fun visitJcAssignInst(inst: JcAssignInst) {
        if (!matchAst(inst)) return

        val symbolicConstraint = checkSymbolic(inst)
        val satResult = api.checkSat(symbolicConstraint)

        if (satResult is JcCheckerSatResult) {
            reportError(inst)
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

class JcDiv42Checker(
    override val api: JcCheckerApi,
    private val cp: JcClasspath,
) : JcAssignInstChecker {
    private val _targetStatements: MutableSet<JcAssignInst> = mutableSetOf()
    val targetStatements: Set<JcAssignInst> = _targetStatements

    override fun matchAst(inst: JcAssignInst): Boolean {
        val expr = inst.rhv
        return expr is JcBinaryExpr && (expr is JcDivExpr || expr is JcRemExpr) && expr.rhv.type == cp.int
    }

    override fun checkSymbolic(inst: JcAssignInst): UBoolExpr = with(api.ctx) {
        val expr = inst.rhv as JcBinaryExpr
        val divider = api.resolveValue(expr.rhv).asExpr(integerSort)
        mkEq(divider, mkBv(42, divider.sort))
    }

    override fun reportError(inst: JcAssignInst) {
        _targetStatements += inst
    }
}
