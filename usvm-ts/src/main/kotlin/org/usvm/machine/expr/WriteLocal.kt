package org.usvm.machine.expr

import io.ksmt.utils.cast
import mu.KotlinLogging
import org.jacodb.ets.model.EtsLocal
import org.usvm.UExpr
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.util.mkRegisterStackLValue

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleAssignToLocal(
    local: EtsLocal,
    expr: UExpr<*>,
): Unit? = ctx.assignToLocal(scope, local, expr)

fun TsContext.assignToLocal(
    scope: TsStepScope,
    local: EtsLocal,
    expr: UExpr<*>,
): Unit? {
    val currentMethod = scope.calcOnState { lastEnteredMethod }

    val idx = getLocalIdx(local, currentMethod)

    if (idx != null) {
        return scope.doWithState {
            saveSortForLocal(idx, expr.sort)

            val lValue = mkRegisterStackLValue(expr.sort, idx)
            memory.write(lValue, expr.cast(), guard = trueExpr)
        }
    }

    val file = currentMethod.enclosingClass!!.declaringFile!!
    logger.warn {
        "Assigning to a global variable: ${local.name} in $file"
    }
    return writeGlobal(scope, file, local.name, expr)
}
