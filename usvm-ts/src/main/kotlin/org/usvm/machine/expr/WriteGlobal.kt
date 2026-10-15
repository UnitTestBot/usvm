package org.usvm.machine.expr

import io.ksmt.utils.cast
import org.jacodb.ets.model.EtsFile
import org.usvm.UExpr
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.ensureGlobalsInitialized
import org.usvm.util.mkFieldLValue

fun TsContext.writeGlobal(
    scope: TsStepScope,
    file: EtsFile,
    name: String,
    expr: UExpr<*>,
): Unit? = scope.calcOnState {
    ensureGlobalsInitialized(scope, file) ?: return@calcOnState null

    val dfltObject = getDfltObject(file)

    val lValue = mkFieldLValue(expr.sort, dfltObject, name)
    memory.write(lValue, expr.cast(), guard = trueExpr)

    saveSortForDfltObjectField(file, name, expr.sort)
}
