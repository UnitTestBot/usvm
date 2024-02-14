package org.usvm.fuzzer.api

import org.usvm.instrumentation.testcase.api.*

interface UTypedTestInstVisitor<T> {
    fun visitUTypedTestMockObject(uTypedTestInst: UTypedTestMockObject): T
    fun visitUTypedTestGlobalMock(uTypedTestInst: UTypedTestGlobalMock): T
    fun visitUTypedTestLambdaMock(uTypedTestInst: UTypedTestLambdaMock): T
    fun visitUTypedTestMethodCall(uTypedTestInst: UTypedTestMethodCall): T
    fun visitUTypedTestStaticMethodCall(uTypedTestInst: UTypedTestStaticMethodCall): T
    fun visitUTypedTestConstructorCall(uTypedTestInst: UTypedTestConstructorCall): T
    fun visitUTypedTestAllocateMemoryCall(uTypedTestInst: UTypedTestAllocateMemoryCall): T
    fun visitUTypedTestSetFieldStatement(uTypedTestInst: UTypedTestSetFieldStatement): T
    fun visitUTypedTestSetStaticFieldStatement(uTypedTestInst: UTypedTestSetStaticFieldStatement): T
    fun visitUTypedTestBinaryConditionExpression(uTypedTestInst: UTypedTestBinaryConditionExpression): T
    fun visitUTypedTestBinaryConditionStatement(uTypedTestInst: UTypedTestBinaryConditionStatement): T
    fun visitUTypedTestArithmeticExpression(uTypedTestInst: UTypedTestArithmeticExpression): T
    fun visitUTypedTestGetStaticFieldExpression(uTypedTestInst: UTypedTestGetStaticFieldExpression): T
    fun visitUTypedTestBooleanExpression(uTypedTestInst: UTypedTestBooleanExpression): T
    fun visitUTypedTestByteExpression(uTypedTestInst: UTypedTestByteExpression): T
    fun visitUTypedTestShortExpression(uTypedTestInst: UTypedTestShortExpression): T
    fun visitUTypedTestIntExpression(uTypedTestInst: UTypedTestIntExpression): T
    fun visitUTypedTestLongExpression(uTypedTestInst: UTypedTestLongExpression): T
    fun visitUTypedTestFloatExpression(uTypedTestInst: UTypedTestFloatExpression): T
    fun visitUTypedTestDoubleExpression(uTypedTestInst: UTypedTestDoubleExpression): T
    fun visitUTypedTestCharExpression(uTypedTestInst: UTypedTestCharExpression): T
    fun visitUTypedTestStringExpression(uTypedTestInst: UTypedTestStringExpression): T
    fun visitUTypedTestNullExpression(uTypedTestInst: UTypedTestNullExpression): T
    fun visitUTypedTestGetFieldExpression(uTypedTestInst: UTypedTestGetFieldExpression): T
    fun visitUTypedTestArrayLengthExpression(uTypedTestInst: UTypedTestArrayLengthExpression): T
    fun visitUTypedTestArrayGetExpression(uTypedTestInst: UTypedTestArrayGetExpression): T
    fun visitUTypedTestArraySetStatement(uTypedTestInst: UTypedTestArraySetStatement): T
    fun visitUTypedTestCreateArrayExpression(uTypedTestInst: UTypedTestCreateArrayExpression): T
    fun visitUTypedTestCastExpression(uTypedTestInst: UTypedTestCastExpression): T
    fun visitUTypedTestClassExpression(uTypedTestInst: UTypedTestClassExpression): T
}
