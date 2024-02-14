package org.usvm.fuzzer.util

import org.jacodb.api.cfg.JcInst
import org.usvm.fuzzer.api.UTypedTestExpression
import org.usvm.fuzzer.api.UTypedTestGetFieldExpression
import org.usvm.fuzzer.api.UTypedTestMethodCall
import org.usvm.instrumentation.testcase.api.*

fun UTestExecutionResult.getTrace(): Map<JcInst, Long> = when (this) {
    is UTestExecutionExceptionResult -> trace ?: emptyMap()
    is UTestExecutionFailedResult -> emptyMap()
    is UTestExecutionInitFailedResult -> emptyMap()
    is UTestExecutionSuccessResult -> trace ?: emptyMap()
    is UTestExecutionTimedOutResult -> emptyMap()
}

fun UTypedTestExpression.unroll(): List<UTypedTestExpression> {
    val res = mutableListOf(this)
    var curInstance =
        when (this) {
            is UTypedTestGetFieldExpression -> instance
            is UTypedTestMethodCall -> instance
            else -> return emptyList()
        }
    while (curInstance is UTypedTestGetFieldExpression || curInstance is UTypedTestMethodCall) {
        res.add(curInstance)
        curInstance =
            when (curInstance) {
                is UTypedTestGetFieldExpression -> curInstance.instance
                is UTypedTestMethodCall -> curInstance.instance
                else -> return emptyList()
            }
    }
    return res
}


class UTestChildrenCollectVisitor : UTestInstVisitor<List<UTestInst>> {

    override fun visitUTestMockObject(uTestInst: UTestMockObject): List<UTestInst> {
        val childrenFields = uTestInst.fields.values.flatMap { it.accept(this) }
        val childrenMethods = uTestInst.methods.values.flatMap { it.map { it.accept(this) } }.flatten()
        return childrenFields + childrenMethods + uTestInst
    }

    override fun visitUTestGlobalMock(uTestInst: UTestGlobalMock): List<UTestInst> {
        val childrenFields = uTestInst.fields.values.flatMap { it.accept(this) }
        val childrenMethods = uTestInst.methods.values.flatMap { it.map { it.accept(this) } }.flatten()
        return childrenFields + childrenMethods + uTestInst
    }

    override fun visitUTestLambdaMock(uTestInst: UTestLambdaMock): List<UTestInst> {
        val values = uTestInst.values.flatMap { it.accept(this) }
        return values + uTestInst
    }


    override fun visitUTestMethodCall(uTestInst: UTestMethodCall): List<UTestInst> {
        val instanceChildren = uTestInst.instance.accept(this)
        val argsChildren = uTestInst.args.flatMap { it.accept(this) }
        return instanceChildren + argsChildren + uTestInst
    }

    override fun visitUTestStaticMethodCall(uTestInst: UTestStaticMethodCall): List<UTestInst> {
        val instanceChildren = uTestInst.instance?.accept(this) ?: emptyList()
        val argsChildren = uTestInst.args.flatMap { it.accept(this) }
        return instanceChildren + argsChildren + uTestInst
    }

    override fun visitUTestConstructorCall(uTestInst: UTestConstructorCall): List<UTestInst> {
        val instanceChildren = uTestInst.instance?.accept(this) ?: emptyList()
        val argsChildren = uTestInst.args.flatMap { it.accept(this) }
        return instanceChildren + argsChildren + uTestInst
    }

    override fun visitUTestAllocateMemoryCall(uTestInst: UTestAllocateMemoryCall): List<UTestInst> {
        val instanceChildren = uTestInst.instance?.accept(this) ?: emptyList()
        val argsChildren = uTestInst.args.flatMap { it.accept(this) }
        return instanceChildren + argsChildren + uTestInst
    }

    override fun visitUTestSetFieldStatement(uTestInst: UTestSetFieldStatement): List<UTestInst> {
        val instanceChildren = uTestInst.instance.accept(this)
        val valueChildren = uTestInst.value.accept(this)
        return instanceChildren + valueChildren + uTestInst
    }

    override fun visitUTestSetStaticFieldStatement(uTestInst: UTestSetStaticFieldStatement): List<UTestInst> =
        uTestInst.value.accept(this) + uTestInst

    override fun visitUTestBinaryConditionExpression(uTestInst: UTestBinaryConditionExpression): List<UTestInst> {
        val conditionLhvChildren = uTestInst.lhv.accept(this)
        val conditionRhvChildren = uTestInst.rhv.accept(this)
        val conditionTrueBranchChildren = uTestInst.trueBranch.accept(this)
        val conditionElseBranchChildren = uTestInst.elseBranch.accept(this)
        return conditionLhvChildren + conditionRhvChildren + conditionTrueBranchChildren + conditionElseBranchChildren + uTestInst
    }

    override fun visitUTestBinaryConditionStatement(uTestInst: UTestBinaryConditionStatement): List<UTestInst> {
        val conditionLhvChildren = uTestInst.lhv.accept(this)
        val conditionRhvChildren = uTestInst.rhv.accept(this)
        val conditionTrueBranchChildren = uTestInst.trueBranch.flatMap{it.accept(this)}
        val conditionElseBranchChildren = uTestInst.elseBranch.flatMap{it.accept(this)}
        return conditionLhvChildren + conditionRhvChildren + conditionTrueBranchChildren + conditionElseBranchChildren + uTestInst
    }

    override fun visitUTestArithmeticExpression(uTestInst: UTestArithmeticExpression): List<UTestInst> {
        val exprLhvChildren = uTestInst.lhv.accept(this)
        val exprRhvChildren = uTestInst.rhv.accept(this)
        return exprLhvChildren + exprRhvChildren + uTestInst
    }

    override fun visitUTestGetStaticFieldExpression(uTestInst: UTestGetStaticFieldExpression): List<UTestInst> =
        listOf(uTestInst)


    override fun visitUTestBooleanExpression(uTestInst: UTestBooleanExpression): List<UTestInst> =
        listOf(uTestInst)

    override fun visitUTestByteExpression(uTestInst: UTestByteExpression): List<UTestInst> =
        listOf(uTestInst)

    override fun visitUTestShortExpression(uTestInst: UTestShortExpression): List<UTestInst> =
        listOf(uTestInst)

    override fun visitUTestIntExpression(uTestInst: UTestIntExpression): List<UTestInst> =
        listOf(uTestInst)

    override fun visitUTestLongExpression(uTestInst: UTestLongExpression): List<UTestInst> =
        listOf(uTestInst)

    override fun visitUTestFloatExpression(uTestInst: UTestFloatExpression): List<UTestInst> =
        listOf(uTestInst)

    override fun visitUTestDoubleExpression(uTestInst: UTestDoubleExpression): List<UTestInst> =
        listOf(uTestInst)

    override fun visitUTestCharExpression(uTestInst: UTestCharExpression): List<UTestInst> =
        listOf(uTestInst)

    override fun visitUTestStringExpression(uTestInst: UTestStringExpression): List<UTestInst> =
        listOf(uTestInst)

    override fun visitUTestNullExpression(uTestInst: UTestNullExpression): List<UTestInst> =
        listOf(uTestInst)

    override fun visitUTestGetFieldExpression(uTestInst: UTestGetFieldExpression): List<UTestInst> {
        return uTestInst.instance.accept(this) + uTestInst
    }

    override fun visitUTestArrayLengthExpression(uTestInst: UTestArrayLengthExpression): List<UTestInst> {
        return uTestInst.arrayInstance.accept(this) + uTestInst
    }

    override fun visitUTestArrayGetExpression(uTestInst: UTestArrayGetExpression): List<UTestInst> {
        return uTestInst.arrayInstance.accept(this) + uTestInst.index.accept(this) + uTestInst
    }

    override fun visitUTestArraySetStatement(uTestInst: UTestArraySetStatement): List<UTestInst> {
        return uTestInst.arrayInstance.accept(this) + uTestInst.index.accept(this) +
                uTestInst.setValueExpression.accept(this) + uTestInst
    }

    override fun visitUTestCreateArrayExpression(uTestInst: UTestCreateArrayExpression): List<UTestInst> {
        return uTestInst.size.accept(this) + uTestInst
    }

    override fun visitUTestCastExpression(uTestInst: UTestCastExpression): List<UTestInst> {
        return uTestInst.expr.accept(this) + uTestInst
    }

    override fun visitUTestClassExpression(uTestInst: UTestClassExpression): List<UTestInst> {
        return listOf(uTestInst)
    }

}