package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.language.IntConst
import org.usvm.programs.MergingProgram
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MergingTest {
    val programDecl = MergingProgram

    val options = UMachineOptions(
        listOf(PathSelectionStrategy.BFS),
        solverType = SolverType.YICES,
        useMerging = true,
        exceptionsPropagation = false, // todo: incompatible with merging (see MergingPathSelector implementation)
    )

    val machine = SampleMachine(programDecl.program, options)

    @Test
    fun runIfMerging() {
        val results = machine.analyze(programDecl.ifMerging)

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 0 }
        }

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 1 }
        }
    }

    @Test
    fun runIfLongMerging() {
        val results = machine.analyze(programDecl.ifLongMerging)

        assertTrue(results.size == 1)
        val result = assertIs<SuccessfulExecutionResult>(results.first())
        val inputArg = (result.inputModel.argumentExprs.first() as IntConst).const
        val returnValue = ((result.outputModel.returnExpr) as IntConst).const
        assertEquals(inputArg.inv().and(31), returnValue)
    }

    @Test
    fun runLoopMerging() {
        val results = machine.analyze(programDecl.loopMerging)

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 0 }
        }

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 1 }
        }
    }
}
