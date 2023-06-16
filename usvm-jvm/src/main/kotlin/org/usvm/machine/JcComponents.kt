package org.usvm.machine

import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase

class JcComponents(
    val typeSystem: JcTypeSystem,
) : UComponents<JcField, JcType, JcMethod> {
    private val closeableResources = mutableListOf<AutoCloseable>()
    override fun mkSolver(ctx: UContext): USolverBase<JcField, JcType, JcMethod> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<JcField, JcType, JcMethod>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<JcField, JcType>(ctx)

        val smtSolver = KYicesSolver(ctx)
        closeableResources += smtSolver
        return USolverBase(ctx, smtSolver, translator, decoder, softConstraintsProvider)
    }

    fun close() {
        closeableResources.forEach(AutoCloseable::close)
    }

    override fun mkTypeSystem(ctx: UContext): JcTypeSystem {
        return typeSystem
    }
}