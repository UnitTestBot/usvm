package org.usvm.interpreter

import org.junit.jupiter.api.Test
import org.usvm.language.IntConst
import org.usvm.programs.LoopProgram
import kotlin.test.assertTrue


class LoopTests {
    val programDecl = LoopProgram
    val runner = Runner(programDecl.program, 40)

    @Test
    fun runLoopLowIdx() {
        val results = runner.run(programDecl.loopLowIdx)

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 0 }
        }

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 1 }
        }
    }

    @Test
    fun runLoopHighIdx() {
        val results = runner.run(programDecl.loopHighIdx)

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 0 }
        }

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 1 }
        }
    }
}