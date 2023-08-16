package org.usvm.fuzzer.strategy

import org.usvm.instrumentation.testcase.api.*

class ExecutionEstimator {

    fun estimate(el: Selectable, executionResult: UTestExecutionResult) {
        val score =
            when (executionResult) {
                is UTestExecutionExceptionResult -> executionResult.trace?.size ?: 0
                is UTestExecutionFailedResult -> 0
                is UTestExecutionInitFailedResult -> 0
                is UTestExecutionSuccessResult -> executionResult.trace?.size ?: 0
                is UTestExecutionTimedOutResult -> 0
            }
        val newAverageScore = ((el.averageScore * el.numberOfChooses) + score) / (el.numberOfChooses + 1)
        el.averageScore = newAverageScore
        el.numberOfChooses += 1
    }

}