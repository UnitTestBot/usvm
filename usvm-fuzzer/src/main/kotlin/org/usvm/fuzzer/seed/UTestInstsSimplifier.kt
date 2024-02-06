package org.usvm.fuzzer.seed

import org.usvm.instrumentation.testcase.api.*

class UTestInstsSimplifier(private val instructionsToRemove: MutableSet<UTestInst>): UTestInstVisitor<Unit> {
    override fun visitUTestMockObject(uTestInst: UTestMockObject) {
        return
    }

    override fun visitUTestGlobalMock(uTestInst: UTestGlobalMock) {
        return
    }

    override fun visitUTestMethodCall(uTestInst: UTestMethodCall) {
        if (uTestInst in instructionsToRemove || uTestInst.instance in instructionsToRemove) {
            instructionsToRemove.add(uTestInst)
            instructionsToRemove.addAll(uTestInst.args)
        }
    }

    override fun visitUTestStaticMethodCall(uTestInst: UTestStaticMethodCall) {
        return
    }

    override fun visitUTestConstructorCall(uTestInst: UTestConstructorCall) {
        return
    }

    override fun visitUTestAllocateMemoryCall(uTestInst: UTestAllocateMemoryCall) {
        return
    }

    override fun visitUTestSetFieldStatement(uTestInst: UTestSetFieldStatement) {
        if (uTestInst in instructionsToRemove || uTestInst.instance in instructionsToRemove) {
            instructionsToRemove.add(uTestInst)
            instructionsToRemove.add(uTestInst.value)
        }
    }

    override fun visitUTestSetStaticFieldStatement(uTestInst: UTestSetStaticFieldStatement) {
        if (uTestInst in instructionsToRemove) {
            instructionsToRemove.add(uTestInst.value)
        }
    }

    override fun visitUTestBinaryConditionExpression(uTestInst: UTestBinaryConditionExpression) {
        TODO("Not yet implemented")
    }

    override fun visitUTestBinaryConditionStatement(uTestInst: UTestBinaryConditionStatement) {
        TODO("Not yet implemented")
    }

    override fun visitUTestArithmeticExpression(uTestInst: UTestArithmeticExpression) {
        TODO("Not yet implemented")
    }

    override fun visitUTestGetStaticFieldExpression(uTestInst: UTestGetStaticFieldExpression) {
        return
    }

    override fun visitUTestBooleanExpression(uTestInst: UTestBooleanExpression) {
        return
    }

    override fun visitUTestByteExpression(uTestInst: UTestByteExpression) {
        return
    }

    override fun visitUTestShortExpression(uTestInst: UTestShortExpression) {
        return
    }

    override fun visitUTestIntExpression(uTestInst: UTestIntExpression) {
        return
    }

    override fun visitUTestLongExpression(uTestInst: UTestLongExpression) {
        return
    }

    override fun visitUTestFloatExpression(uTestInst: UTestFloatExpression) {
        return
    }

    override fun visitUTestDoubleExpression(uTestInst: UTestDoubleExpression) {
        return
    }

    override fun visitUTestCharExpression(uTestInst: UTestCharExpression) {
        return
    }

    override fun visitUTestStringExpression(uTestInst: UTestStringExpression) {
        return
    }

    override fun visitUTestNullExpression(uTestInst: UTestNullExpression) {
        return
    }

    override fun visitUTestGetFieldExpression(uTestInst: UTestGetFieldExpression) {
        return
    }

    override fun visitUTestArrayLengthExpression(uTestInst: UTestArrayLengthExpression) {
        return
    }

    override fun visitUTestArrayGetExpression(uTestInst: UTestArrayGetExpression) {
        return
    }

    override fun visitUTestArraySetStatement(uTestInst: UTestArraySetStatement) {
        if (uTestInst in instructionsToRemove || uTestInst.arrayInstance in instructionsToRemove) {
            instructionsToRemove.add(uTestInst)
            instructionsToRemove.add(uTestInst.index)
            instructionsToRemove.add(uTestInst.setValueExpression)
        }
    }

    override fun visitUTestCreateArrayExpression(uTestInst: UTestCreateArrayExpression) {
        return
    }

    override fun visitUTestCastExpression(uTestInst: UTestCastExpression) {
        return
    }

    override fun visitUTestClassExpression(uTestInst: UTestClassExpression) {
        return
    }
}