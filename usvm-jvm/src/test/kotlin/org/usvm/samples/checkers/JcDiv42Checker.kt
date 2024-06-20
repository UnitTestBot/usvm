package org.usvm.samples.checkers

import io.ksmt.utils.asExpr
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBinaryExpr
import org.jacodb.api.jvm.cfg.JcDivExpr
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstVisitor
import org.jacodb.api.jvm.cfg.JcRemExpr
import org.jacodb.api.jvm.ext.int
import org.usvm.UBoolExpr
import org.usvm.api.checkers.JcCheckerApi
import org.usvm.api.checkers.JcCheckerSatResult

interface JcAssignInstChecker : JcInstVisitor.Default<Unit> {
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

    override fun defaultVisitJcInst(inst: JcInst) {
        // ignore
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
