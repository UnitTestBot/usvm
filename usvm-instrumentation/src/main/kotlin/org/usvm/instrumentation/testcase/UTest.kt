package org.usvm.instrumentation.testcase

import org.usvm.instrumentation.testcase.statement.UTestCall
import org.usvm.instrumentation.testcase.statement.UTestExpression


class UTest(
    val initStatements: List<UTestExpression>,
    val callMethodExpression: UTestCall
)