package org.usvm.machine

import org.usvm.machine.interpreters.symbolic.operations.tracing.PathDiversionException
import org.usvm.machine.interpreters.symbolic.operations.tracing.SymbolicHandlerEvent
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.symbolicobjects.rendering.PyValueBuilder
import org.usvm.machine.symbolicobjects.rendering.PyValueRenderer
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.machine.utils.PythonMachineStatisticsOnFunction
import java.util.concurrent.Callable


class ConcolicRunContext(
    curState: PyState,
    ctx: PyContext,
    modelHolder: PyModelHolder,
    typeSystem: PythonTypeSystem,
    allowPathDiversion: Boolean,
    statistics: PythonMachineStatisticsOnFunction,
    maxInstructions: Int,
    isCancelled: Callable<Boolean>
) {
    var curState: PyState?
    val ctx: PyContext
    val forkedStates = mutableListOf<PyState>()
    var pathPrefix: List<SymbolicHandlerEvent<Any>>
    @JvmField
    var curOperation: MockHeader? = null
    val modelHolder: PyModelHolder
    val allowPathDiversion: Boolean
    val typeSystem: PythonTypeSystem
    val statistics: PythonMachineStatisticsOnFunction
    val maxInstructions: Int
    var instructionCounter = 0
    var usesVirtualInputs = false
    var isCancelled: Callable<Boolean>
    var builder: PyValueBuilder? = null
    var renderer: PyValueRenderer? = null

    init {
        this.curState = curState
        this.ctx = ctx
        this.modelHolder = modelHolder
        this.allowPathDiversion = allowPathDiversion
        this.typeSystem = typeSystem
        pathPrefix = curState.buildPathAsList()
        this.statistics = statistics
        this.maxInstructions = maxInstructions
        this.isCancelled = isCancelled
    }

    @Throws(PathDiversionException::class)
    fun pathDiversion() {
        if (curState != null) curState!!.meta.modelDied = true
        curState = if (allowPathDiversion) {
            null
        } else {
            throw PathDiversionException()
        }
    }
}