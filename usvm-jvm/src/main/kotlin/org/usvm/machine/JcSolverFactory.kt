package org.usvm.machine

import io.ksmt.KContext
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KTheory
import io.ksmt.solver.runner.KSolverRunnerManager
import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.yices.KYicesSolverConfiguration
import io.ksmt.solver.yices.KYicesSolverUniversalConfiguration
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.symfpu.solver.KSymFpuSolver
import org.usvm.SolverType
import org.usvm.UContext

internal interface SolverFactory : AutoCloseable {
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
        SolverType.YICES -> KSymFpuSolver(KYicesSolver(ctx), ctx).apply {
            configure {
                // Fp theory is handled by the SymFpu
                optimizeForTheories(setOf(KTheory.Array, KTheory.BV, KTheory.UF))
            }
        }

        SolverType.Z3 -> KZ3Solver(ctx).apply {
            configure {
                optimizeForTheories(setOf(KTheory.Array, KTheory.BV, KTheory.UF, KTheory.FP))
            }
        }
    }

    override fun close() {
        // Do nothing
    }
}

private class AnotherProcessSolverFactory : SolverFactory {
    private val solverManager: KSolverRunnerManager = KSolverRunnerManager()

    init {
        // YicesWithSymFpu is custom solver, so we need to register it
        solverManager.registerSolver(YicesWithSymFpu::class, KYicesSolverUniversalConfiguration::class)
    }

    override fun <Context : UContext<USizeSort>> mkSolver(
        ctx: Context,
        solverType: SolverType
    ): KSolver<out KSolverConfiguration> = when (solverType) {
        // Yices with Fp support via SymFpu
        SolverType.YICES -> solverManager.createSolver(ctx, YicesWithSymFpu::class).apply {
            configure {
                // Fp theory is handled by the SymFpu
                optimizeForTheories(setOf(KTheory.Array, KTheory.BV, KTheory.UF))
            }
        }

        SolverType.Z3 -> solverManager.createSolver(ctx, KZ3Solver::class).apply {
            configure {
                optimizeForTheories(setOf(KTheory.Array, KTheory.BV, KTheory.UF, KTheory.FP))
            }
        }
    }

    override fun close() {
        solverManager.close()
    }

    class YicesWithSymFpu(ctx: KContext): KSymFpuSolver<KYicesSolverConfiguration>(KYicesSolver(ctx), ctx)
}
