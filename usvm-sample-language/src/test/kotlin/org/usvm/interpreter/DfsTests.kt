package org.usvm.interpreter

import org.junit.jupiter.api.Test
import org.usvm.language.ArrayCreation
import org.usvm.language.IntConst
import org.usvm.language.IntType
import org.usvm.programs.DfsProgram
import kotlin.test.assertTrue


class DfsTests {
    val programDecl = DfsProgram
    val runner = Runner(programDecl.program, 13)

    @Test
    fun testDfs() {
        runner.run(programDecl.dfs)
    }

    @Test
    fun testSumLoopGraph() {
        val results = runner.run(programDecl.calcSumLoop)
        println(results.joinToString("\n"))
        assertTrue {
            results.any { result ->
                if (result !is SuccessfulExecutionResult) {
                    return@any false
                }
                @Suppress("UNCHECKED_CAST")
                val array = (result.inputModel.argumentExprs[0] as ArrayCreation<IntType>)
                    .values
                    .map { (it as IntConst).const }
                array.size == 7 && array.distinct().size == 1 && array.sum() % 7 != 0
            }
        }
    }
}