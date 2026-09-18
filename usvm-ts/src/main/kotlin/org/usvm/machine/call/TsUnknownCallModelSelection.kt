package org.usvm.machine.call

/** Selects all registered models or an explicit set of model IDs; an empty set disables models. */
sealed interface TsUnknownCallModelSelection {
    /** Enables every discovered built-in model. */
    data object All : TsUnknownCallModelSelection

    data class Only(val ids: Set<String>) : TsUnknownCallModelSelection
}
