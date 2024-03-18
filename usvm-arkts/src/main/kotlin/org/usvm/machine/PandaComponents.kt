package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UMachineOptions
import org.usvm.USizeExprProvider
import org.usvm.solver.USolverBase


class PandaComponents(
    private val typeSystem: PandaTypeSystem,
    private val options: UMachineOptions
) : UComponents<PandaType, UBv32Sort> { // TODO Usizesort????????? {
    override val useSolverForForks: Boolean
        get() = TODO("Not yet implemented")

    override fun <Context : UContext<UBv32Sort>> mkSolver(ctx: Context): USolverBase<PandaType> {
        TODO("Not yet implemented")
    }

    override fun mkTypeSystem(ctx: UContext<UBv32Sort>): PandaTypeSystem {
        TODO("Not yet implemented")
    }

    override fun <Context : UContext<UBv32Sort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<UBv32Sort> {
        TODO("Not yet implemented")
    }
}