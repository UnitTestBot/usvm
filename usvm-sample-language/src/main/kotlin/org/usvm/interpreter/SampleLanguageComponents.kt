package org.usvm.interpreter

import io.ksmt.solver.yices.KYicesSolver
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UTypeSystem
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.SampleType
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase

class SampleLanguageComponents(
    private val typeSystem: SampleTypeSystem
) : UComponents<Field<*>, SampleType, Method<*>> {
    override fun mkSolver(ctx: UContext): USolverBase<Field<*>, SampleType, Method<*>> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Field<*>, SampleType, Method<*>>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<Field<*>, SampleType>(ctx)

        return USolverBase(ctx, KYicesSolver(ctx), translator, decoder, softConstraintsProvider)
    }

    override fun mkTypeSystem(ctx: UContext): UTypeSystem<SampleType> = typeSystem
}