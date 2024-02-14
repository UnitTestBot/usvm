package org.usvm.fuzzer.api

import org.usvm.fuzzer.seed.Seed
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import java.util.IdentityHashMap

class UTypedTest2UTestConverter : UTypedTestInstVisitor<UTestInst> {

    private val cache = IdentityHashMap<UTypedTestInst, UTestInst>()

    fun convertToUTest(seed: Seed) {

    }

    override fun visitUTypedTestMockObject(uTypedTestInst: UTypedTestMockObject): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestMockObject(
                uTypedTestInst.type.type,
                uTypedTestInst.fields.mapValues { it.value.accept(this) as UTestExpression },
                uTypedTestInst.methods.mapValues { it.value.map { it.accept(this) as UTestExpression } }
            )
        }


    override fun visitUTypedTestGlobalMock(uTypedTestInst: UTypedTestGlobalMock): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestGlobalMock(
                uTypedTestInst.type.type,
                uTypedTestInst.fields.mapValues { it.value.accept(this) as UTestExpression },
                uTypedTestInst.methods.mapValues { it.value.map { it.accept(this) as UTestExpression } }
            )
        }

    override fun visitUTypedTestLambdaMock(uTypedTestInst: UTypedTestLambdaMock): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestLambdaMock(
                uTypedTestInst.type.type,
                uTypedTestInst.values.map { it.accept(this) as UTestExpression }
            )
        }

    override fun visitUTypedTestMethodCall(uTypedTestInst: UTypedTestMethodCall): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestMethodCall(
                uTypedTestInst.instance.accept(this) as UTestExpression,
                uTypedTestInst.method,
                uTypedTestInst.args.map { it.accept(this) as UTestExpression }
            )
        }

    override fun visitUTypedTestStaticMethodCall(uTypedTestInst: UTypedTestStaticMethodCall): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestStaticMethodCall(
                uTypedTestInst.method,
                uTypedTestInst.args.map { it.accept(this) as UTestExpression }
            )
        }

    override fun visitUTypedTestConstructorCall(uTypedTestInst: UTypedTestConstructorCall): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestConstructorCall(
                uTypedTestInst.method,
                uTypedTestInst.args.map { it.accept(this) as UTestExpression }
            )
        }

    override fun visitUTypedTestAllocateMemoryCall(uTypedTestInst: UTypedTestAllocateMemoryCall): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestAllocateMemoryCall(uTypedTestInst.clazz)
        }

    override fun visitUTypedTestSetFieldStatement(uTypedTestInst: UTypedTestSetFieldStatement): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestSetFieldStatement(
                uTypedTestInst.instance.accept(this) as UTestExpression,
                uTypedTestInst.field,
                uTypedTestInst.value.accept(this) as UTestExpression
            )
        }

    override fun visitUTypedTestSetStaticFieldStatement(uTypedTestInst: UTypedTestSetStaticFieldStatement): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestSetStaticFieldStatement(
                uTypedTestInst.field,
                uTypedTestInst.value.accept(this) as UTestExpression
            )
        }

    override fun visitUTypedTestBinaryConditionExpression(uTypedTestInst: UTypedTestBinaryConditionExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestBinaryConditionExpression(
                uTypedTestInst.conditionType,
                uTypedTestInst.lhv.accept(this) as UTestExpression,
                uTypedTestInst.rhv.accept(this) as UTestExpression,
                uTypedTestInst.trueBranch.accept(this) as UTestExpression,
                uTypedTestInst.elseBranch.accept(this) as UTestExpression
            )
        }

    override fun visitUTypedTestBinaryConditionStatement(uTypedTestInst: UTypedTestBinaryConditionStatement): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestBinaryConditionStatement(
                uTypedTestInst.conditionType,
                uTypedTestInst.lhv.accept(this) as UTestExpression,
                uTypedTestInst.rhv.accept(this) as UTestExpression,
                uTypedTestInst.trueBranch.map { it.accept(this) as UTestStatement },
                uTypedTestInst.elseBranch.map { it.accept(this) as UTestStatement }
            )
        }

    override fun visitUTypedTestArithmeticExpression(uTypedTestInst: UTypedTestArithmeticExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestArithmeticExpression(
                uTypedTestInst.operationType,
                uTypedTestInst.lhv.accept(this) as UTestExpression,
                uTypedTestInst.rhv.accept(this) as UTestExpression,
                uTypedTestInst.type.type
            )
        }

    override fun visitUTypedTestGetStaticFieldExpression(uTypedTestInst: UTypedTestGetStaticFieldExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestGetStaticFieldExpression(
                uTypedTestInst.field
            )
        }

    override fun visitUTypedTestBooleanExpression(uTypedTestInst: UTypedTestBooleanExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestBooleanExpression(
                uTypedTestInst.value,
                uTypedTestInst.type.type
            )
        }

    override fun visitUTypedTestByteExpression(uTypedTestInst: UTypedTestByteExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestByteExpression(
                uTypedTestInst.value,
                uTypedTestInst.type.type
            )
        }

    override fun visitUTypedTestShortExpression(uTypedTestInst: UTypedTestShortExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestShortExpression(
                uTypedTestInst.value,
                uTypedTestInst.type.type
            )
        }

    override fun visitUTypedTestIntExpression(uTypedTestInst: UTypedTestIntExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestIntExpression(
                uTypedTestInst.value,
                uTypedTestInst.type.type
            )
        }

    override fun visitUTypedTestLongExpression(uTypedTestInst: UTypedTestLongExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestLongExpression(
                uTypedTestInst.value,
                uTypedTestInst.type.type
            )
        }

    override fun visitUTypedTestFloatExpression(uTypedTestInst: UTypedTestFloatExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestFloatExpression(
                uTypedTestInst.value,
                uTypedTestInst.type.type
            )
        }

    override fun visitUTypedTestDoubleExpression(uTypedTestInst: UTypedTestDoubleExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestDoubleExpression(
                uTypedTestInst.value,
                uTypedTestInst.type.type
            )
        }

    override fun visitUTypedTestCharExpression(uTypedTestInst: UTypedTestCharExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestCharExpression(
                uTypedTestInst.value,
                uTypedTestInst. type.type
            )
        }

    override fun visitUTypedTestStringExpression(uTypedTestInst: UTypedTestStringExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestStringExpression(
                uTypedTestInst. value,
                uTypedTestInst.type.type
            )
        }

    override fun visitUTypedTestNullExpression(uTypedTestInst: UTypedTestNullExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestNullExpression(
                uTypedTestInst.type.type
            )
        }

    override fun visitUTypedTestGetFieldExpression(uTypedTestInst: UTypedTestGetFieldExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestGetFieldExpression(
                uTypedTestInst.instance.accept(this) as UTestExpression,
                uTypedTestInst.field
            )
        }

    override fun visitUTypedTestArrayLengthExpression(uTypedTestInst: UTypedTestArrayLengthExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestArrayLengthExpression(
                uTypedTestInst.arrayInstance.accept(this) as UTestExpression
            )
        }

    override fun visitUTypedTestArrayGetExpression(uTypedTestInst: UTypedTestArrayGetExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestArrayGetExpression(
                uTypedTestInst.arrayInstance.accept(this) as UTestExpression,
                uTypedTestInst.index.accept(this) as UTestExpression
            )
        }

    override fun visitUTypedTestArraySetStatement(uTypedTestInst: UTypedTestArraySetStatement): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestArraySetStatement(
                uTypedTestInst.arrayInstance.accept(this) as UTestExpression,
                uTypedTestInst.index.accept(this) as UTestExpression,
                uTypedTestInst.setValueExpression.accept(this) as UTestExpression
            )
        }

    override fun visitUTypedTestCreateArrayExpression(uTypedTestInst: UTypedTestCreateArrayExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestCreateArrayExpression(
                uTypedTestInst.elementType.type,
                uTypedTestInst.size.accept(this) as UTestExpression
            )
        }

    override fun visitUTypedTestCastExpression(uTypedTestInst: UTypedTestCastExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestCastExpression(
                uTypedTestInst.expr.accept(this) as UTestExpression,
                uTypedTestInst.type.type
            )
        }

    override fun visitUTypedTestClassExpression(uTypedTestInst: UTypedTestClassExpression): UTestInst =
        cache.getOrPut(uTypedTestInst) {
            UTestClassExpression(
                uTypedTestInst.type.type
            )
        }
}