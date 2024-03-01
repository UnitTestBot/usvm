package org.usvm.instrumentation.testcase

import org.usvm.instrumentation.testcase.api.UTestCall
import org.usvm.instrumentation.testcase.api.UTestInst

// TODO it is not a UTest, it is JcTest
class UTest(
    val initStatements: List<UTestInst>,
    val callMethodExpression: UTestCall
)