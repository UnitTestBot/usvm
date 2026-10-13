package org.usvm.machine.interpreter

import org.jacodb.ets.model.EtsMethod

/** Solver uncertainty during initialization is distinct from invalid input constraints or an engine failure. */
class TsInitialStateSolverUnknownException(method: EtsMethod) : IllegalStateException(
    "Solver returned UNKNOWN while checking initial constraints for ${method.signature}",
)
