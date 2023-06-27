package org.usvm.machine

import org.junit.jupiter.api.Test
import org.usvm.MachineOptions
import org.usvm.PathSelectionStrategy
import org.usvm.language.ArrayCreation
import org.usvm.language.IntConst
import org.usvm.language.IntType
import org.usvm.programs.DfsProgram
import kotlin.test.assertTrue


class DfsTests {
    val programDecl = DfsProgram
    val machine = SampleMachine(programDecl.program)

    @Test
    fun testDfs() {
        machine.analyze(programDecl.dfs, MachineOptions(pathSelectionStrategies = listOf(PathSelectionStrategy.RANDOM_PATH)))
    }

    @Test
    fun testSumLoopGraph() {
        val results = machine.analyze(programDecl.calcSumLoop)
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