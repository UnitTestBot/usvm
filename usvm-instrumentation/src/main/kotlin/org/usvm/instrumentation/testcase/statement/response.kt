package org.usvm.instrumentation.testcase.statement

import org.jacodb.api.cfg.JcInst
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor

sealed class UTestExecutionResult

data class UTestExecutionFailedResult(
    val cause: String
): UTestExecutionResult()

data class UTestExecutionTimedOutResult(
    val cause: String
): UTestExecutionResult()

data class UTestExecutionInitFailedResult(
    val cause: String,
    val trace: List<JcInst>?
): UTestExecutionResult()

data class UTestExecutionSuccessResult(
    val trace: List<JcInst>?,
    val result: UTestValueDescriptor?,
    val initialState: ExecutionState,
    val resultState: ExecutionState
): UTestExecutionResult()

data class UTestExecutionExceptionResult(
    val cause: String,
    val trace: List<JcInst>?
): UTestExecutionResult()

data class ExecutionState(
    val instanceDescriptor: UTestValueDescriptor?,
    val argsDescriptors: List<UTestValueDescriptor?>?
)