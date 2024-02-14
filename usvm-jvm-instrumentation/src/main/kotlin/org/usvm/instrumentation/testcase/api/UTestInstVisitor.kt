package org.usvm.instrumentation.testcase.api

interface UTestInstVisitor<T> {
    fun visitUTestMockObject(uTestInst: UTestMockObject): T
    fun visitUTestGlobalMock(uTestInst: UTestGlobalMock): T
    fun visitUTestLambdaMock(uTestInst: UTestLambdaMock): T
    fun visitUTestMethodCall(uTestInst: UTestMethodCall): T
    fun visitUTestStaticMethodCall(uTestInst: UTestStaticMethodCall): T
    fun visitUTestConstructorCall(uTestInst: UTestConstructorCall): T
    fun visitUTestAllocateMemoryCall(uTestInst: UTestAllocateMemoryCall): T
    fun visitUTestSetFieldStatement(uTestInst: UTestSetFieldStatement): T
    fun visitUTestSetStaticFieldStatement(uTestInst: UTestSetStaticFieldStatement): T
    fun visitUTestBinaryConditionExpression(uTestInst: UTestBinaryConditionExpression): T
    fun visitUTestBinaryConditionStatement(uTestInst: UTestBinaryConditionStatement): T
    fun visitUTestArithmeticExpression(uTestInst: UTestArithmeticExpression): T
    fun visitUTestGetStaticFieldExpression(uTestInst: UTestGetStaticFieldExpression): T
    fun visitUTestBooleanExpression(uTestInst: UTestBooleanExpression): T
    fun visitUTestByteExpression(uTestInst: UTestByteExpression): T
    fun visitUTestShortExpression(uTestInst: UTestShortExpression): T
    fun visitUTestIntExpression(uTestInst: UTestIntExpression): T
    fun visitUTestLongExpression(uTestInst: UTestLongExpression): T
    fun visitUTestFloatExpression(uTestInst: UTestFloatExpression): T
    fun visitUTestDoubleExpression(uTestInst: UTestDoubleExpression): T
    fun visitUTestCharExpression(uTestInst: UTestCharExpression): T
    fun visitUTestStringExpression(uTestInst: UTestStringExpression): T
    fun visitUTestNullExpression(uTestInst: UTestNullExpression): T
    fun visitUTestGetFieldExpression(uTestInst: UTestGetFieldExpression): T
    fun visitUTestArrayLengthExpression(uTestInst: UTestArrayLengthExpression): T
    fun visitUTestArrayGetExpression(uTestInst: UTestArrayGetExpression): T
    fun visitUTestArraySetStatement(uTestInst: UTestArraySetStatement): T
    fun visitUTestCreateArrayExpression(uTestInst: UTestCreateArrayExpression): T
    fun visitUTestCastExpression(uTestInst: UTestCastExpression): T
    fun visitUTestClassExpression(uTestInst: UTestClassExpression): T
}
