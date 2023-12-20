package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.language.IntConst
import org.usvm.programs.LoopProgram
import kotlin.test.assertTrue
import kotlin.time.Duration


class LoopTest {
    private val programDecl = LoopProgram
    private val machine = SampleMachine(
        programDecl.program,
        UMachineOptions(listOf(PathSelectionStrategy.BFS), solverType = SolverType.Z3)
    )

    @Test
    fun runLoopLowIdx() {
        val results = machine.analyze(programDecl.loopLowIdx)

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 0 }
        }

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 1 }
        }
    }

    @Test
    fun runLoopHighIdx() {
        val results = machine.analyze(programDecl.loopHighIdx)

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 0 }
        }

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 1 }
        }
    }

    @Test
    fun runLoopSimple() {
        val results = machine.analyze(programDecl.loopSimple)
        println(results)
    }

    @Test
    fun runLoopHard() {
        val machine = SampleMachine(
            programDecl.program,
            UMachineOptions(listOf(PathSelectionStrategy.BFS), timeout = Duration.INFINITE, solverType = SolverType.Z3),
        )
        val results = machine.analyze(programDecl.loopHard)
        println(results)
    }

    @Test
    fun runLoopInfinite() {
        val machine = SampleMachine(
            programDecl.program,
            UMachineOptions(
                listOf(PathSelectionStrategy.DFS),
                stopOnCoverage = -1,
                timeout = Duration.INFINITE,
                stepLimit = 1_000_000UL,
            ),
        )
        val results = machine.analyze(programDecl.loopInfinite)
        println(results)
    }

    @Test
    fun runLoopCollatz() {
        val machine = SampleMachine(
            programDecl.program,
            UMachineOptions(
                listOf(PathSelectionStrategy.FORK_DEPTH),
                stopOnCoverage = -1,
                timeout = Duration.INFINITE,
                stepLimit = 1_000_000UL,
            ),
        )
        val results = machine.analyze(programDecl.loopCollatz)
        println(results)
    }
}
