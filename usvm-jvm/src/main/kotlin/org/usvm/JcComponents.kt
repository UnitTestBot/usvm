package org.usvm

import io.ksmt.solver.yices.KYicesSolver
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase

class JcComponents(
    val typeSystem: JcTypeSystem,
) : UComponents<JcField, JcType, JcMethod> {
    override fun mkSolver(ctx: UContext): USolverBase<JcField, JcType, JcMethod> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<JcField, JcType, JcMethod>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<JcField, JcType>(ctx)

        return USolverBase(ctx, KYicesSolver(ctx), translator, decoder, softConstraintsProvider)
    }

    override fun mkTypeSystem(ctx: UContext): JcTypeSystem {
        return typeSystem
    }
}