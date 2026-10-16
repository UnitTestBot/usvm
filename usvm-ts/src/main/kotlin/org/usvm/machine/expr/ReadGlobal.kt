package org.usvm.machine.expr

import org.jacodb.ets.model.EtsFile
import org.usvm.UExpr
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.ensureGlobalsInitialized
import org.usvm.machine.state.recordInitialModuleBinding
import org.usvm.util.mkFieldLValue

fun TsContext.readGlobal(
    scope: TsStepScope,
    file: EtsFile,
    name: String,
): UExpr<*>? = scope.calcOnState {
    ensureGlobalsInitialized(scope, file) ?: return@calcOnState null
    recordInitialModuleBinding(file, name, isWrite = false)

    val savedSort = getSortForDfltObjectField(file, name)
    if (savedSort == null) {
        // Missing storage also covers native globals that this runtime has not modeled.
        // It does not establish that the reached path is unsatisfiable.
        terminateAsUnsupported(
            reason = "GLOBAL_BINDING_NOT_MODELED: Global binding '$name' has no initialized storage in $file",
        )
        return@calcOnState null
    }

    val dfltObject = getDfltObject(file)

    val lValue = mkFieldLValue(savedSort, dfltObject, name)
    memory.read(lValue)
}
