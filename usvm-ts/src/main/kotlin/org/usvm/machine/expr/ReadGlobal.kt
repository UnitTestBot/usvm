package org.usvm.machine.expr

import mu.KotlinLogging
import org.jacodb.ets.model.EtsFile
import org.usvm.UExpr
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.ensureGlobalsInitialized
import org.usvm.util.mkFieldLValue

private val logger = KotlinLogging.logger {}

fun TsContext.readGlobal(
    scope: TsStepScope,
    file: EtsFile,
    name: String,
): UExpr<*>? = scope.calcOnState {
    // Initialize globals in `file` if necessary
    ensureGlobalsInitialized(scope, file) ?: return@calcOnState null

    // Restore the sort of the requested global variable
    val savedSort = getSortForDfltObjectField(file, name)
    if (savedSort == null) {
        // No saved sort means this variable was never assigned to, which is an error to read.
        logger.error { "Trying to read unassigned global variable: $name in $file" }
        scope.assert(falseExpr)
        return@calcOnState null
    }

    // Get the globals container object
    val dfltObject = getDfltObject(file)

    // Read the global variable as a field of the globals container object
    val lValue = mkFieldLValue(savedSort, dfltObject, name)
    memory.read(lValue)
}
