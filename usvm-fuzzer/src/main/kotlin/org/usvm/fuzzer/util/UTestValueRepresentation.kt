package org.usvm.fuzzer.util

import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestInst

data class UTestValueRepresentation(val instance: UTestExpression, val initStmts: List<UTestInst>) {
    constructor(instance: UTestExpression): this(instance, listOf())
}