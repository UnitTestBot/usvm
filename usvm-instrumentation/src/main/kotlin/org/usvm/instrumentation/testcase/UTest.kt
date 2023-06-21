package org.usvm.instrumentation.testcase

import org.usvm.instrumentation.testcase.api.UTestCall
import org.usvm.instrumentation.testcase.api.UTestExpression


class UTest(
    val initStatements: List<UTestExpression>,
    val callMethodExpression: UTestCall
)