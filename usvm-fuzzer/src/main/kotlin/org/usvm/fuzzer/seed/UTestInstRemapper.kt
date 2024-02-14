package org.usvm.fuzzer.seed

import org.usvm.fuzzer.api.*
import org.usvm.instrumentation.testcase.api.*

class UTypedTestInstRemapper(
    private val remappedInstructions: MutableMap<UTypedTestInst, UTypedTestInst>
) : UTypedTestInstVisitor<UTypedTestInst> {

    override fun visitUTypedTestMockObject(uTypedTestInst: UTypedTestMockObject): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newFieldValues = uTypedTestInst.fields.mapValues { it.value.accept(this) as UTypedTestExpression }
        val newMethodValues = uTypedTestInst.methods.mapValues { it.value.map { it.accept(this) as UTypedTestExpression } }
        return if (newFieldValues == uTypedTestInst.fields && newMethodValues == uTypedTestInst.methods) {
            uTypedTestInst
        } else {
            val newMock = UTypedTestMockObject(
                uTypedTestInst.type,
                newFieldValues,
                newMethodValues
            )
            remappedInstructions[uTypedTestInst] = newMock
            newMock
        }
    }

    override fun visitUTypedTestGlobalMock(uTypedTestInst: UTypedTestGlobalMock): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newFieldValues = uTypedTestInst.fields.mapValues { it.value.accept(this) as UTypedTestExpression }
        val newMethodValues = uTypedTestInst.methods.mapValues { it.value.map { it.accept(this) as UTypedTestExpression } }
        return if (newFieldValues == uTypedTestInst.fields && newMethodValues == uTypedTestInst.methods) {
            uTypedTestInst
        } else {
            val newMock = UTypedTestGlobalMock(
                uTypedTestInst.type,
                newFieldValues,
                newMethodValues
            )
            remappedInstructions[uTypedTestInst] = newMock
            newMock
        }
    }

    override fun visitUTypedTestLambdaMock(uTypedTestInst: UTypedTestLambdaMock): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newValues = uTypedTestInst.values.map { it.accept(this) as UTypedTestExpression }
        return if (newValues == uTypedTestInst.values) {
            uTypedTestInst
        } else {
            val newMock = UTypedTestLambdaMock(
                uTypedTestInst.type,
                newValues
            )
            remappedInstructions[uTypedTestInst] = newMock
            newMock
        }
    }

    override fun visitUTypedTestMethodCall(uTypedTestInst: UTypedTestMethodCall): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newInstanceValue = uTypedTestInst.instance.accept(this) as UTypedTestExpression
        val newArgsValues = uTypedTestInst.args.map { it.accept(this) as UTypedTestExpression }
        return if (newInstanceValue == uTypedTestInst.instance && newArgsValues == uTypedTestInst.args) {
            uTypedTestInst
        } else {
            val newMethodCall = UTypedTestMethodCall(
                newInstanceValue,
                uTypedTestInst.method,
                newArgsValues
            )
            remappedInstructions[uTypedTestInst] = newMethodCall
            newMethodCall
        }
    }

    override fun visitUTypedTestStaticMethodCall(uTypedTestInst: UTypedTestStaticMethodCall): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newArgsValues = uTypedTestInst.args.map { it.accept(this) as UTypedTestExpression }
        return if (newArgsValues == uTypedTestInst.args) {
            uTypedTestInst
        } else {
            val newMethodCall = UTypedTestStaticMethodCall(
                uTypedTestInst.method,
                newArgsValues
            )
            remappedInstructions[uTypedTestInst] = newMethodCall
            newMethodCall
        }
    }

    override fun visitUTypedTestConstructorCall(uTypedTestInst: UTypedTestConstructorCall): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newArgsValues = uTypedTestInst.args.map { it.accept(this) as UTypedTestExpression }
        return if (newArgsValues == uTypedTestInst.args) {
            uTypedTestInst
        } else {
            val newMethodCall = UTypedTestConstructorCall(
                uTypedTestInst.method,
                newArgsValues,
                uTypedTestInst.type
            )
            remappedInstructions[uTypedTestInst] = newMethodCall
            newMethodCall
        }
    }

    override fun visitUTypedTestAllocateMemoryCall(uTypedTestInst: UTypedTestAllocateMemoryCall): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestSetFieldStatement(uTypedTestInst: UTypedTestSetFieldStatement): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newInstanceValue = uTypedTestInst.instance.accept(this) as UTypedTestExpression
        val newArgValue = uTypedTestInst.value.accept(this) as UTypedTestExpression
        return if (newInstanceValue == uTypedTestInst.instance && newArgValue == uTypedTestInst.value) {
            uTypedTestInst
        } else {
            val newUTypedTestSetFieldStmt = UTypedTestSetFieldStatement(
                newInstanceValue,
                uTypedTestInst.field,
                newArgValue
            )
            remappedInstructions[uTypedTestInst] = newUTypedTestSetFieldStmt
            newUTypedTestSetFieldStmt
        }
    }

    override fun visitUTypedTestSetStaticFieldStatement(uTypedTestInst: UTypedTestSetStaticFieldStatement): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newArgValue = uTypedTestInst.value.accept(this) as UTypedTestExpression
        return if (newArgValue == uTypedTestInst.value) {
            uTypedTestInst
        } else {
            val newUTypedTestSetFieldStmt = UTypedTestSetStaticFieldStatement(
                uTypedTestInst.field,
                newArgValue
            )
            remappedInstructions[uTypedTestInst] = newUTypedTestSetFieldStmt
            newUTypedTestSetFieldStmt
        }
    }

    override fun visitUTypedTestBinaryConditionExpression(uTypedTestInst: UTypedTestBinaryConditionExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        TODO()
//        return UTypedTestBinaryConditionExpression(
//            UTypedTestInst.conditionType,
//            UTypedTestInst.lhv.accept(this) as UTypedTestExpression,
//            UTypedTestInst.rhv.accept(this) as UTypedTestExpression,
//            UTypedTestInst.trueBranch.accept(this) as UTypedTestExpression,
//            UTypedTestInst.elseBranch.accept(this) as UTypedTestExpression,
//        )
    }

    override fun visitUTypedTestBinaryConditionStatement(uTypedTestInst: UTypedTestBinaryConditionStatement): UTypedTestInst {
        TODO()
//        if (oldInst == uTypedTestInst) return newInst
//        return UTypedTestBinaryConditionStatement(
//            UTypedTestInst.conditionType,
//            UTypedTestInst.lhv.accept(this) as UTypedTestExpression,
//            UTypedTestInst.rhv.accept(this) as UTypedTestExpression,
//            UTypedTestInst.trueBranch.map { it.accept(this) as UTypedTestStatement },
//            UTypedTestInst.elseBranch.map { it.accept(this) as UTypedTestStatement }
//        )
    }

    override fun visitUTypedTestArithmeticExpression(uTypedTestInst: UTypedTestArithmeticExpression): UTypedTestInst {
        TODO()
//        if (oldInst == uTypedTestInst) return newInst
//        return UTypedTestArithmeticExpression(
//            UTypedTestInst.operationType,
//            UTypedTestInst.lhv.accept(this) as UTypedTestExpression,
//            UTypedTestInst.rhv.accept(this) as UTypedTestExpression,
//            UTypedTestInst.type
//        )
    }

    override fun visitUTypedTestGetStaticFieldExpression(uTypedTestInst: UTypedTestGetStaticFieldExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestBooleanExpression(uTypedTestInst: UTypedTestBooleanExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestByteExpression(uTypedTestInst: UTypedTestByteExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestShortExpression(uTypedTestInst: UTypedTestShortExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestIntExpression(uTypedTestInst: UTypedTestIntExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestLongExpression(uTypedTestInst: UTypedTestLongExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestFloatExpression(uTypedTestInst: UTypedTestFloatExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestDoubleExpression(uTypedTestInst: UTypedTestDoubleExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestCharExpression(uTypedTestInst: UTypedTestCharExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestStringExpression(uTypedTestInst: UTypedTestStringExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestNullExpression(uTypedTestInst: UTypedTestNullExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }

    override fun visitUTypedTestGetFieldExpression(uTypedTestInst: UTypedTestGetFieldExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newInstanceValue = uTypedTestInst.instance.accept(this) as UTypedTestExpression
        return if (newInstanceValue == uTypedTestInst.instance) {
            uTypedTestInst
        } else {
            val newUTypedTestGetField = UTypedTestGetFieldExpression(
                newInstanceValue,
                uTypedTestInst.field,
                uTypedTestInst.type
            )
            remappedInstructions[uTypedTestInst] = newUTypedTestGetField
            newUTypedTestGetField
        }
    }

    override fun visitUTypedTestArrayLengthExpression(uTypedTestInst: UTypedTestArrayLengthExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newArrayInstance = uTypedTestInst.arrayInstance.accept(this) as UTypedTestExpression
        return if (newArrayInstance == uTypedTestInst.arrayInstance) {
            uTypedTestInst
        } else {
            val newUTypedTestArrayLength = UTypedTestArrayLengthExpression(
                newArrayInstance,
                uTypedTestInst.type
            )
            remappedInstructions[uTypedTestInst] = newUTypedTestArrayLength
            newUTypedTestArrayLength
        }
    }

    override fun visitUTypedTestArrayGetExpression(uTypedTestInst: UTypedTestArrayGetExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newArrayInstance = uTypedTestInst.arrayInstance.accept(this) as UTypedTestExpression
        val newIndex = uTypedTestInst.arrayInstance.accept(this) as UTypedTestExpression
        return if (newArrayInstance == uTypedTestInst.arrayInstance && newIndex == uTypedTestInst.index) {
            uTypedTestInst
        } else {
            val newUTypedTestArrayGet = UTypedTestArrayGetExpression(
                newArrayInstance,
                newIndex,
                uTypedTestInst.type
            )
            remappedInstructions[uTypedTestInst] = newUTypedTestArrayGet
            newUTypedTestArrayGet
        }
    }

    override fun visitUTypedTestArraySetStatement(uTypedTestInst: UTypedTestArraySetStatement): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newArrayInstance = uTypedTestInst.arrayInstance.accept(this) as UTypedTestExpression
        val newIndex = uTypedTestInst.index.accept(this) as UTypedTestExpression
        val newValue = uTypedTestInst.setValueExpression.accept(this) as UTypedTestExpression
        return if (newArrayInstance == uTypedTestInst.arrayInstance && newIndex == uTypedTestInst.index && newValue == uTypedTestInst.setValueExpression) {
            uTypedTestInst
        } else {
            val newUTypedTestArraySet = UTypedTestArraySetStatement(
                newArrayInstance,
                newIndex,
                newValue
            )
            remappedInstructions[uTypedTestInst] = newUTypedTestArraySet
            newUTypedTestArraySet
        }
    }

    override fun visitUTypedTestCreateArrayExpression(uTypedTestInst: UTypedTestCreateArrayExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newSize = uTypedTestInst.size.accept(this) as UTypedTestExpression
        return if (newSize == uTypedTestInst.size) {
            uTypedTestInst
        } else {
            val newUTypedTestCreate = UTypedTestCreateArrayExpression(
                uTypedTestInst.elementType,
                newSize,
                uTypedTestInst.type
            )
            remappedInstructions[uTypedTestInst] = newUTypedTestCreate
            newUTypedTestCreate
        }
    }

    override fun visitUTypedTestCastExpression(uTypedTestInst: UTypedTestCastExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        val newExpr = uTypedTestInst.expr.accept(this) as UTypedTestExpression
        return if (newExpr == uTypedTestInst.expr) {
            uTypedTestInst
        } else {
            val newCast = UTypedTestCastExpression(
                newExpr,
                uTypedTestInst.type
            )
            remappedInstructions[uTypedTestInst] = newCast
            newCast
        }
    }

    override fun visitUTypedTestClassExpression(uTypedTestInst: UTypedTestClassExpression): UTypedTestInst {
        if (uTypedTestInst in remappedInstructions) return remappedInstructions[uTypedTestInst]!!
        return uTypedTestInst
    }
}