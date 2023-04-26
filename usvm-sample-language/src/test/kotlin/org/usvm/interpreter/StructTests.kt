package org.usvm.interpreter

import org.junit.jupiter.api.Test
import org.usvm.language.BooleanConst
import org.usvm.language.IntConst
import org.usvm.programs.StructProgram
import kotlin.test.assertTrue

class StructTests {
    val programDecl = StructProgram
    val analyzer = SampleAnalyzer(programDecl.program)

    @Test
    fun testCheckRefEquality() {
        val results = analyzer.analyze(programDecl.checkRefEquality)
        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as BooleanConst).const }
        }

        assertTrue {
            results.any { it is SuccessfulExecutionResult && !(it.outputModel.returnExpr as BooleanConst).const }
        }
    }

    @Test
    fun testCheckImplicitRefEquality() {
        val results = analyzer.analyze(programDecl.checkImplicitRefEquality)
        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 0 }
        }

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 1 }
        }

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 2 }
        }

        assertTrue {
            results.any { it is SuccessfulExecutionResult && (it.outputModel.returnExpr as IntConst).const == 3 }
        }
    }
}
