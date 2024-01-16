package org.usvm.fuzzer.util

import org.jacodb.api.cfg.JcInst
import org.usvm.instrumentation.testcase.api.*

fun UTestExecutionResult.getTrace(): List<JcInst> = when(this) {
    is UTestExecutionExceptionResult -> trace ?: listOf()
    is UTestExecutionFailedResult -> listOf()
    is UTestExecutionInitFailedResult -> listOf()
    is UTestExecutionSuccessResult -> trace ?: listOf()
    is UTestExecutionTimedOutResult -> listOf()
}