package org.usvm.machine.expr

import io.ksmt.utils.cast
import org.jacodb.ets.model.EtsFile
import org.usvm.UExpr
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.ensureGlobalsInitialized
import org.usvm.util.mkFieldLValue

internal fun TsContext.writeGlobal(
    scope: TsStepScope,
    file: EtsFile,
    name: String,
    expr: UExpr<*>,
): Unit? = scope.calcOnState {
    // Initialize globals in `file` if necessary
    ensureGlobalsInitialized(scope, file) ?: return@calcOnState null

    // Get the globals container object
    val dfltObject = getDfltObject(file)

    // Write the global variable as a field of the globals container object
    val lValue = mkFieldLValue(expr.sort, dfltObject, name)
    memory.write(lValue, expr.cast(), guard = trueExpr)

    // Save the sort of the global variable for future reads
    saveSortForDfltObjectField(file, name, expr.sort)
}
