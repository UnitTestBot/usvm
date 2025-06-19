package org.usvm.test.api

// TODO it is not a UTest, it is JcTest
class UTest(
    val initStatements: List<UTestInst>,
    val callMethodExpression: UTestCall
)
