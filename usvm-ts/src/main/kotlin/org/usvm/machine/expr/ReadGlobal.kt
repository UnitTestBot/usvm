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
    ensureGlobalsInitialized(scope, file) ?: return@calcOnState null

    val savedSort = getSortForDfltObjectField(file, name)
    if (savedSort == null) {
        // No saved sort means this variable was never assigned to, which is an error to read.
        logger.error { "Trying to read unassigned global variable: $name in $file" }
        scope.assert(falseExpr)
        return@calcOnState null
    }

    val dfltObject = getDfltObject(file)

    val lValue = mkFieldLValue(savedSort, dfltObject, name)
    memory.read(lValue)
}
