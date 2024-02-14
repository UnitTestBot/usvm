package org.usvm.fuzzer.seed

import org.usvm.fuzzer.api.UTypedTestInst
import org.usvm.fuzzer.api.UTypedTestInstVisitor
import org.usvm.fuzzer.api.*
import org.usvm.instrumentation.testcase.api.*

class UTypedTestInstsSimplifier(private val instructionsToRemove: MutableSet<UTypedTestInst>):
    UTypedTestInstVisitor<Unit> {
    override fun visitUTypedTestMockObject(uTypedTestInst: UTypedTestMockObject) {
        return
    }

    override fun visitUTypedTestGlobalMock(uTypedTestInst: UTypedTestGlobalMock) {
        return
    }

    override fun visitUTypedTestLambdaMock(uTypedTestInst: UTypedTestLambdaMock) {
        return
    }

    override fun visitUTypedTestMethodCall(uTypedTestInst: UTypedTestMethodCall) {
        if (uTypedTestInst in instructionsToRemove || uTypedTestInst.instance in instructionsToRemove) {
            instructionsToRemove.add(uTypedTestInst)
            instructionsToRemove.addAll(uTypedTestInst.args)
        }
    }

    override fun visitUTypedTestStaticMethodCall(uTypedTestInst: UTypedTestStaticMethodCall) {
        return
    }

    override fun visitUTypedTestConstructorCall(uTypedTestInst: UTypedTestConstructorCall) {
        return
    }

    override fun visitUTypedTestAllocateMemoryCall(uTypedTestInst: UTypedTestAllocateMemoryCall) {
        return
    }

    override fun visitUTypedTestSetFieldStatement(uTypedTestInst: UTypedTestSetFieldStatement) {
        if (uTypedTestInst in instructionsToRemove || uTypedTestInst.instance in instructionsToRemove) {
            instructionsToRemove.add(uTypedTestInst)
            instructionsToRemove.add(uTypedTestInst.value)
        }
    }

    override fun visitUTypedTestSetStaticFieldStatement(uTypedTestInst: UTypedTestSetStaticFieldStatement) {
        if (uTypedTestInst in instructionsToRemove) {
            instructionsToRemove.add(uTypedTestInst.value)
        }
    }

    override fun visitUTypedTestBinaryConditionExpression(uTypedTestInst: UTypedTestBinaryConditionExpression) {
        TODO("Not yet implemented")
    }

    override fun visitUTypedTestBinaryConditionStatement(uTypedTestInst: UTypedTestBinaryConditionStatement) {
        TODO("Not yet implemented")
    }

    override fun visitUTypedTestArithmeticExpression(uTypedTestInst: UTypedTestArithmeticExpression) {
        TODO("Not yet implemented")
    }

    override fun visitUTypedTestGetStaticFieldExpression(uTypedTestInst: UTypedTestGetStaticFieldExpression) {
        return
    }

    override fun visitUTypedTestBooleanExpression(uTypedTestInst: UTypedTestBooleanExpression) {
        return
    }

    override fun visitUTypedTestByteExpression(uTypedTestInst: UTypedTestByteExpression) {
        return
    }

    override fun visitUTypedTestShortExpression(uTypedTestInst: UTypedTestShortExpression) {
        return
    }

    override fun visitUTypedTestIntExpression(uTypedTestInst: UTypedTestIntExpression) {
        return
    }

    override fun visitUTypedTestLongExpression(uTypedTestInst: UTypedTestLongExpression) {
        return
    }

    override fun visitUTypedTestFloatExpression(uTypedTestInst: UTypedTestFloatExpression) {
        return
    }

    override fun visitUTypedTestDoubleExpression(uTypedTestInst: UTypedTestDoubleExpression) {
        return
    }

    override fun visitUTypedTestCharExpression(uTypedTestInst: UTypedTestCharExpression) {
        return
    }

    override fun visitUTypedTestStringExpression(uTypedTestInst: UTypedTestStringExpression) {
        return
    }

    override fun visitUTypedTestNullExpression(uTypedTestInst: UTypedTestNullExpression) {
        return
    }

    override fun visitUTypedTestGetFieldExpression(uTypedTestInst: UTypedTestGetFieldExpression) {
        return
    }

    override fun visitUTypedTestArrayLengthExpression(uTypedTestInst: UTypedTestArrayLengthExpression) {
        return
    }

    override fun visitUTypedTestArrayGetExpression(uTypedTestInst: UTypedTestArrayGetExpression) {
        return
    }

    override fun visitUTypedTestArraySetStatement(uTypedTestInst: UTypedTestArraySetStatement) {
        if (uTypedTestInst in instructionsToRemove || uTypedTestInst.arrayInstance in instructionsToRemove) {
            instructionsToRemove.add(uTypedTestInst)
            instructionsToRemove.add(uTypedTestInst.index)
            instructionsToRemove.add(uTypedTestInst.setValueExpression)
        }
    }

    override fun visitUTypedTestCreateArrayExpression(uTypedTestInst: UTypedTestCreateArrayExpression) {
        return
    }

    override fun visitUTypedTestCastExpression(uTypedTestInst: UTypedTestCastExpression) {
        return
    }

    override fun visitUTypedTestClassExpression(uTypedTestInst: UTypedTestClassExpression) {
        return
    }
}