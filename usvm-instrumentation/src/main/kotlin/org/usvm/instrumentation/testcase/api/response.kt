package org.usvm.instrumentation.testcase.api

import org.jacodb.api.JcField
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
    val initialState: UTestExecutionState,
    val resultState: UTestExecutionState
): UTestExecutionResult()

data class UTestExecutionExceptionResult(
    val cause: String,
    val trace: List<JcInst>?
): UTestExecutionResult()

data class UTestExecutionState(
    val instanceDescriptor: UTestValueDescriptor?,
    val argsDescriptors: List<UTestValueDescriptor?>?,
    val statics: MutableMap<JcField, UTestValueDescriptor>
)