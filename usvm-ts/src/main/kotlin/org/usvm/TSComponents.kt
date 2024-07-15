package org.usvm

import org.jacodb.ets.base.EtsType
import org.usvm.solver.USolverBase
import org.usvm.types.UTypeSystem

class TSComponents(
    private val typeSystem: TSTypeSystem,
    private val options: UMachineOptions
) : UComponents<EtsType, TSSizeSort> {
    override val useSolverForForks: Boolean
        get() = TODO("Not yet implemented")

    override fun <Context : UContext<TSSizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<TSSizeSort> {
        TODO("Not yet implemented")
    }

    override fun mkTypeSystem(ctx: UContext<TSSizeSort>): UTypeSystem<EtsType> {
        TODO("Not yet implemented")
    }

    override fun <Context : UContext<TSSizeSort>> mkSolver(ctx: Context): USolverBase<EtsType> {
        TODO("Not yet implemented")
    }
}
