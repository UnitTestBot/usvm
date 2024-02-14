package org.usvm.fuzzer.util

import org.usvm.fuzzer.api.UTypedTestExpression
import org.usvm.fuzzer.api.UTypedTestInst
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestInst

data class UTestValueRepresentation(val instance: UTypedTestExpression, val initStmts: List<UTypedTestInst>) {
    constructor(instance: UTypedTestExpression): this(instance, listOf())
}