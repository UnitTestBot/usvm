package org.usvm.samples.approximations

import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.samplesWithApproximationsKey
import kotlin.test.assertEquals

abstract class ApproximationsTestRunner : JavaMethodTestRunner() {

    override val jacodbCpKey: String
        get() = samplesWithApproximationsKey

    class FixedExecutionVerifier(
        totalExecutions: Int,
        val exceptionalExecutions: Set<Int> = emptySet()
    ) {
        private val expectedExecutions = (0 until totalExecutions).toSet()
        private val failedExecutions = mutableSetOf<Int>()
        private val checkedExecutions = mutableSetOf<Int>()

        fun onExecution(execution: Int, check: Boolean): Boolean {
            if (check) {
                checkedExecutions += execution
            } else {
                failedExecutions += execution
            }
            return check
        }

        fun verifyStatus(execution: Int, status: Int): Boolean {
            if (status < 0) {
                failedExecutions += execution
                return false
            }

            if (execution in exceptionalExecutions) return false
            if (execution !in expectedExecutions) return false

            val check = execution == status
            return onExecution(execution, check)
        }

        fun check() {
            assertEquals(emptySet(), failedExecutions, "failed executions")
            assertEquals(expectedExecutions, checkedExecutions, "expected executions")
        }
    }
}
