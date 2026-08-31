package org.usvm.machine.call.intrinsic

import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallModelBackend
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelImplementation
import org.usvm.machine.call.TsUnknownCallModelImplementationKind
import org.usvm.machine.state.TsState

/** Builds constraint-level execution plans directly from a TypeScript symbolic state. */
fun interface TsIntrinsicUnknownCallModel {
    fun execute(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution
}

/** Opaque registry handle for a Kotlin intrinsic semantic model. */
class TsIntrinsicUnknownCallModelImplementation(
    val model: TsIntrinsicUnknownCallModel,
) : TsUnknownCallModelImplementation {
    override val kind: TsUnknownCallModelImplementationKind =
        TsUnknownCallModelImplementationKind.INTRINSIC
}

/** Executes intrinsic model handles without exposing them to the common registry or dispatcher contract. */
object TsIntrinsicUnknownCallModelBackend : TsUnknownCallModelBackend {
    override val kind: TsUnknownCallModelImplementationKind =
        TsUnknownCallModelImplementationKind.INTRINSIC

    override fun execute(
        implementation: TsUnknownCallModelImplementation,
        state: TsState,
        call: TsUnknownCall,
    ): TsUnknownCallModelExecution {
        val intrinsic = requireNotNull(implementation as? TsIntrinsicUnknownCallModelImplementation) {
            "INTRINSIC backend requires TsIntrinsicUnknownCallModelImplementation, got ${implementation::class}"
        }

        return intrinsic.model.execute(state, call)
    }
}
