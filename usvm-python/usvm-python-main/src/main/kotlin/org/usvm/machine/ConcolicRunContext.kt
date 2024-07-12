package org.usvm.machine

import org.usvm.machine.interpreters.symbolic.operations.tracing.PathDiversionException
import org.usvm.machine.interpreters.symbolic.operations.tracing.SymbolicHandlerEvent
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.symbolicobjects.rendering.PyValueBuilder
import org.usvm.machine.symbolicobjects.rendering.PyValueRenderer
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.machine.utils.PythonMachineStatisticsOnFunction
import java.util.concurrent.Callable


class ConcolicRunContext(
    curState: PyState,
    val ctx: PyContext,
    val modelHolder: PyModelHolder,
    private val allowPathDiversion: Boolean,
    val statistics: PythonMachineStatisticsOnFunction,
    val maxInstructions: Int,
    val builder: PyValueBuilder,
    val renderer: PyValueRenderer,
    val isCancelled: Callable<Boolean>,
) {
    var curState: PyState?
    val forkedStates = mutableListOf<PyState>()
    var pathPrefix: List<SymbolicHandlerEvent<Any>>
    var instructionCounter = 0
    var usesVirtualInputs: Boolean = false

    val typeSystem: PythonTypeSystem
        get() = ctx.typeSystem<PythonType>() as PythonTypeSystem

    @JvmField
    var curOperation: MockHeader? = null

    init {
        this.curState = curState
        pathPrefix = curState.buildPathAsList()
    }

    @Throws(PathDiversionException::class)
    fun pathDiversion() {
        val state = curState
        if (state != null) {
            state.modelDied = true
        }
        curState = if (allowPathDiversion) {
            null
        } else {
            throw PathDiversionException()
        }
    }
}
