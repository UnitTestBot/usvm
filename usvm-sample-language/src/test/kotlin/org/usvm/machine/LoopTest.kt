package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.language.IntConst
import org.usvm.programs.LoopProgram
import kotlin.test.assertTrue


class LoopTest {
    val programDecl = LoopProgram
    val machine = SampleMachine(programDecl.program, UMachineOptions(listOf(PathSelectionStrategy.DFS), solverType = SolverType.YICES))

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
}
