package org.usvm.instrumentation.testcase

import org.usvm.instrumentation.testcase.api.UTestCall
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestInst


class UTest(
    val initStatements: List<UTestInst>,
    val callMethodExpression: UTestCall
)