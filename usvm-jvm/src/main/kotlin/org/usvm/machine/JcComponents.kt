package org.usvm.machine

import io.ksmt.KContext
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.runner.KSolverRunnerManager
import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.yices.KYicesSolverConfiguration
import io.ksmt.solver.yices.KYicesSolverUniversalConfiguration
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.symfpu.solver.KSymFpuSolver
import org.jacodb.api.JcType
import org.usvm.SolverType
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USizeExprProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver

class JcComponents(
    private val typeSystem: JcTypeSystem,
    private val solverType: SolverType,
    override val useSolverForForks: Boolean,
    private val runSolverInAnotherProcess: Boolean,
) : UComponents<JcType, USizeSort> {
    private val closeableResources = mutableListOf<AutoCloseable>()

    override fun <Context : UContext<USizeSort>> mkSolver(ctx: Context): USolverBase<JcType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)

        val solverFactory = SolverFactory.mkFactory(runSolverInAnotherProcess)
        val smtSolver = solverFactory.mkSolver(ctx, solverType)
        val typeSolver = UTypeSolver(typeSystem)
        closeableResources += smtSolver
        closeableResources += solverFactory

        return USolverBase(ctx, smtSolver, typeSolver, translator, decoder)
    }

    fun close() {
        closeableResources.forEach(AutoCloseable::close)
    }

    override fun mkTypeSystem(ctx: UContext<USizeSort>): JcTypeSystem {
        return typeSystem
    }

    override fun <Context : UContext<USizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<USizeSort> =
        UBv32SizeExprProvider(ctx)

}

private interface SolverFactory : AutoCloseable {
    fun <Context : UContext<USizeSort>> mkSolver(
        ctx: Context,
        solverType: SolverType
    ): KSolver<out KSolverConfiguration>

    companion object {
        fun mkFactory(runSolverInAnotherProcess: Boolean): SolverFactory =
            if (runSolverInAnotherProcess) AnotherProcessSolverFactory() else SameProcessSolverFactory
    }
}

private object SameProcessSolverFactory : SolverFactory {
    override fun <Context : UContext<USizeSort>> mkSolver(
        ctx: Context,
        solverType: SolverType
    ): KSolver<out KSolverConfiguration> = when (solverType) {
        // Yices with Fp support via SymFpu
        SolverType.YICES -> KSymFpuSolver(KYicesSolver(ctx), ctx)
        SolverType.Z3 -> KZ3Solver(ctx)
    }

    override fun close() {
        // Do nothing
    }
}

private class AnotherProcessSolverFactory : SolverFactory {
    private val solverManager: KSolverRunnerManager = KSolverRunnerManager()

    init {
        solverManager.registerSolver(YicesWithSymFpu::class, KYicesSolverUniversalConfiguration::class)
    }

    override fun <Context : UContext<USizeSort>> mkSolver(
        ctx: Context,
        solverType: SolverType
    ): KSolver<out KSolverConfiguration> = when (solverType) {
        // Yices with Fp support via SymFpu
        SolverType.YICES -> solverManager.createSolver(ctx, YicesWithSymFpu::class)
        SolverType.Z3 -> solverManager.createSolver(ctx, KZ3Solver::class)
    }

    override fun close() {
        solverManager.close()
    }

    class YicesWithSymFpu(ctx: KContext): KSymFpuSolver<KYicesSolverConfiguration>(KYicesSolver(ctx), ctx)
}
