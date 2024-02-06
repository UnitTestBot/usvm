package org.usvm.fuzzer.strategy

import org.usvm.fuzzer.mutation.Mutation
import org.usvm.fuzzer.mutation.MutationInfo
import org.usvm.fuzzer.seed.Seed
import org.usvm.instrumentation.testcase.api.*

class ExecutionEstimator {

//    fun estimate(el: Selectable, executionResult: UTestExecutionResult) {
//        val score =
//            when (executionResult) {
//                is UTestExecutionExceptionResult -> executionResult.trace?.size ?: 0
//                is UTestExecutionFailedResult -> 0
//                is UTestExecutionInitFailedResult -> 0
//                is UTestExecutionSuccessResult -> executionResult.trace?.size ?: 0
//                is UTestExecutionTimedOutResult -> 0
//            }
//        val newAverageScore = ((el.averageScore * el.numberOfChooses) + score) / (el.numberOfChooses + 1)
//        el.averageScore = newAverageScore
//        el.numberOfChooses += 1
//    }

    fun estimateExecution(seed: Seed, mutation: Mutation, mutationInfo: MutationInfo, executionResult: UTestExecutionResult) {
        val score =
            when (executionResult) {
                is UTestExecutionExceptionResult -> executionResult.trace?.size ?: 0
                is UTestExecutionFailedResult -> 0
                is UTestExecutionInitFailedResult -> 0
                is UTestExecutionSuccessResult -> executionResult.trace?.size ?: 0
                is UTestExecutionTimedOutResult -> 0
            }.toDouble()
        calcAndAssignNewAverageScore(seed, score)
        calcAndAssignNewAverageScore(mutation, score)
        mutationInfo.mutatedArg?.let { calcAndAssignNewAverageScore(it, score) }
        mutationInfo.mutatedField?.let { mutatedField ->
            Seed.fieldInfo.getFieldInfo(mutatedField)?.let { calcAndAssignNewAverageScore(it, score) }
        }
    }

    private fun calcAndAssignNewAverageScore(el: Selectable, score: Double) {
        val newScore = ((el.score * el.numberOfChooses) + score) / (el.numberOfChooses + 1)
        el.score = newScore
        el.numberOfChooses++
    }

}