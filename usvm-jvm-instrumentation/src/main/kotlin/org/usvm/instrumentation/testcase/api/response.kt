package org.usvm.instrumentation.testcase.api

import org.jacodb.api.JcField
import org.jacodb.api.cfg.JcInst
import org.usvm.instrumentation.testcase.descriptor.UTestExceptionDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor

/**
 * Api for execution result packaging
 */
sealed class UTestExecutionResult

/**
 * In case of something wrong with RD
 * @param cause --- cause of RD failure
 */
data class UTestExecutionFailedResult(
    val cause: UTestExceptionDescriptor
): UTestExecutionResult()

/**
 * Execution timeout
 * @param cause --- if exception thrown
 */
data class UTestExecutionTimedOutResult(
    val cause: UTestExceptionDescriptor
): UTestExecutionResult()

/**
 * In case of problems in execution problems (serialization/deserialization errors or UTestExpression execution error)
 * @property cause --- cause of failure
 * @param trace --- trace before failure happened
 */
data class UTestExecutionInitFailedResult(
    val cause: UTestExceptionDescriptor,
    val trace: List<JcInst>?
): UTestExecutionResult()

/**
 * Everything fine
 * @property trace --- trace in jacodb instructions
 * @property result --- descriptor of function result
 * @property initialState --- Initial state before execution (see UTestExecutionState)
 * @property resultState ---  State after execution (see UTestExecutionState)
 */
data class UTestExecutionSuccessResult(
    val trace: List<JcInst>?,
    val result: UTestValueDescriptor?,
    val initialState: UTestExecutionState,
    val resultState: UTestExecutionState
): UTestExecutionResult()

/**
 * In case with analyzing program threw exception
 * @property cause --- exception message
 * @property trace --- trace in jacodb instructions before exception thrown
 */
data class UTestExecutionExceptionResult(
    val cause: UTestExceptionDescriptor,
    val trace: List<JcInst>?,
    val initialState: UTestExecutionState,
    val resultState: UTestExecutionState
): UTestExecutionResult()

/**
 * Description of program state before and after method execution
 * @param instanceDescriptor --- class instance descriptor
 * @param argsDescriptors --- method invocation arguments descriptors
 * @param statics --- descriptors of affected statics during execution
 */
data class UTestExecutionState(
    val instanceDescriptor: UTestValueDescriptor?,
    val argsDescriptors: List<UTestValueDescriptor?>?,
    val statics: MutableMap<JcField, UTestValueDescriptor>
)