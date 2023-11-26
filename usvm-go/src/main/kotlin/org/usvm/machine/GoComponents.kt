package org.usvm.machine

import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UMachineOptions
import org.usvm.USizeExprProvider
import org.usvm.solver.USolverBase
import org.usvm.types.UTypeSystem

class GoComponents(
    private val typeSystem: GoTypeSystem,
    private val options: UMachineOptions,
): UComponents<GoType, USizeSort> {
    override val useSolverForForks: Boolean
        get() = TODO("Not yet implemented")

    override fun <Context : UContext<USizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<USizeSort> {
        TODO("Not yet implemented")
    }

    override fun mkTypeSystem(ctx: UContext<USizeSort>): UTypeSystem<GoType> {
        return typeSystem
    }

    override fun <Context : UContext<USizeSort>> mkSolver(ctx: Context): USolverBase<GoType> {
        TODO("Not yet implemented")
    }
}