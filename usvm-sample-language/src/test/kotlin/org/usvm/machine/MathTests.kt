package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.language.BooleanConst
import org.usvm.language.DivisionByZero
import org.usvm.language.IntConst
import org.usvm.programs.MathProgram
import kotlin.test.assertTrue

class MathTests {
    val programDecl = MathProgram
    val machine = SampleMachine(programDecl.program)

    @Test
    fun testAbs() {
        val results = machine.analyze(programDecl.abs)
        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.inputModel.argumentExprs[0] as IntConst).const < 0 }
        }
        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.inputModel.argumentExprs[0] as IntConst).const >= 0 }
        }
    }

    @Test
    fun testDivByZero() {
        val results = machine.analyze(programDecl.division)
        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.inputModel.argumentExprs[1] as IntConst).const != 0 }
        }
        assertTrue {
            results.any {
                it is UnsuccessfulExecutionResult &&
                    (it.inputModel.argumentExprs[1] as IntConst).const == 0 &&
                    it.exception is DivisionByZero
            }
        }
    }

    @Test
    fun testAbsOverflow() {
        val results = machine.analyze(programDecl.absOverflow)
        assertTrue {
            results.any {
                it is SuccessfulExecutionResult &&
                    (it.outputModel.returnExpr as BooleanConst).const == false &&
                    (it.inputModel.argumentExprs[0] as IntConst).const == Int.MIN_VALUE
            }
        }

        assertTrue {
            results.any {
                it is SuccessfulExecutionResult &&
                    (it.outputModel.returnExpr as BooleanConst).const == true &&
                    (it.inputModel.argumentExprs[0] as IntConst).const != Int.MIN_VALUE
            }
        }
    }
}