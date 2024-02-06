package org.usvm.fuzzer.seed

import org.usvm.instrumentation.testcase.api.*

class UTestInstRemapper(
    private val remappedInstructions: MutableMap<UTestInst, UTestInst>
) : UTestInstVisitor<UTestInst> {

    override fun visitUTestMockObject(uTestInst: UTestMockObject): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newFieldValues = uTestInst.fields.mapValues { it.value.accept(this) as UTestExpression }
        val newMethodValues = uTestInst.methods.mapValues { it.value.map { it.accept(this) as UTestExpression } }
        return if (newFieldValues == uTestInst.fields && newMethodValues == uTestInst.methods) {
            uTestInst
        } else {
            val newMock = UTestMockObject(
                uTestInst.type,
                newFieldValues,
                newMethodValues
            )
            remappedInstructions[uTestInst] = newMock
            newMock
        }
    }

    override fun visitUTestGlobalMock(uTestInst: UTestGlobalMock): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newFieldValues = uTestInst.fields.mapValues { it.value.accept(this) as UTestExpression }
        val newMethodValues = uTestInst.methods.mapValues { it.value.map { it.accept(this) as UTestExpression } }
        return if (newFieldValues == uTestInst.fields && newMethodValues == uTestInst.methods) {
            uTestInst
        } else {
            val newMock = UTestGlobalMock(
                uTestInst.type,
                newFieldValues,
                newMethodValues
            )
            remappedInstructions[uTestInst] = newMock
            newMock
        }
    }

    override fun visitUTestMethodCall(uTestInst: UTestMethodCall): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newInstanceValue = uTestInst.instance.accept(this) as UTestExpression
        val newArgsValues = uTestInst.args.map { it.accept(this) as UTestExpression }
        return if (newInstanceValue == uTestInst.instance && newArgsValues == uTestInst.args) {
            uTestInst
        } else {
            val newMethodCall = UTestMethodCall(
                newInstanceValue,
                uTestInst.method,
                newArgsValues
            )
            remappedInstructions[uTestInst] = newMethodCall
            newMethodCall
        }
    }

    override fun visitUTestStaticMethodCall(uTestInst: UTestStaticMethodCall): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newArgsValues = uTestInst.args.map { it.accept(this) as UTestExpression }
        return if (newArgsValues == uTestInst.args) {
            uTestInst
        } else {
            val newMethodCall = UTestStaticMethodCall(
                uTestInst.method,
                newArgsValues
            )
            remappedInstructions[uTestInst] = newMethodCall
            newMethodCall
        }
    }

    override fun visitUTestConstructorCall(uTestInst: UTestConstructorCall): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newArgsValues = uTestInst.args.map { it.accept(this) as UTestExpression }
        return if (newArgsValues == uTestInst.args) {
            uTestInst
        } else {
            val newMethodCall = UTestConstructorCall(
                uTestInst.method,
                newArgsValues
            )
            remappedInstructions[uTestInst] = newMethodCall
            newMethodCall
        }
    }

    override fun visitUTestAllocateMemoryCall(uTestInst: UTestAllocateMemoryCall): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestSetFieldStatement(uTestInst: UTestSetFieldStatement): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newInstanceValue = uTestInst.instance.accept(this) as UTestExpression
        val newArgValue = uTestInst.value.accept(this) as UTestExpression
        return if (newInstanceValue == uTestInst.instance && newArgValue == uTestInst.value) {
            uTestInst
        } else {
            val newUTestSetFieldStmt = UTestSetFieldStatement(
                newInstanceValue,
                uTestInst.field,
                newArgValue
            )
            remappedInstructions[uTestInst] = newUTestSetFieldStmt
            newUTestSetFieldStmt
        }
    }

    override fun visitUTestSetStaticFieldStatement(uTestInst: UTestSetStaticFieldStatement): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newArgValue = uTestInst.value.accept(this) as UTestExpression
        return if (newArgValue == uTestInst.value) {
            uTestInst
        } else {
            val newUTestSetFieldStmt = UTestSetStaticFieldStatement(
                uTestInst.field,
                newArgValue
            )
            remappedInstructions[uTestInst] = newUTestSetFieldStmt
            newUTestSetFieldStmt
        }
    }

    override fun visitUTestBinaryConditionExpression(uTestInst: UTestBinaryConditionExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        TODO()
//        return UTestBinaryConditionExpression(
//            uTestInst.conditionType,
//            uTestInst.lhv.accept(this) as UTestExpression,
//            uTestInst.rhv.accept(this) as UTestExpression,
//            uTestInst.trueBranch.accept(this) as UTestExpression,
//            uTestInst.elseBranch.accept(this) as UTestExpression,
//        )
    }

    override fun visitUTestBinaryConditionStatement(uTestInst: UTestBinaryConditionStatement): UTestInst {
        TODO()
//        if (oldInst == uTestInst) return newInst
//        return UTestBinaryConditionStatement(
//            uTestInst.conditionType,
//            uTestInst.lhv.accept(this) as UTestExpression,
//            uTestInst.rhv.accept(this) as UTestExpression,
//            uTestInst.trueBranch.map { it.accept(this) as UTestStatement },
//            uTestInst.elseBranch.map { it.accept(this) as UTestStatement }
//        )
    }

    override fun visitUTestArithmeticExpression(uTestInst: UTestArithmeticExpression): UTestInst {
        TODO()
//        if (oldInst == uTestInst) return newInst
//        return UTestArithmeticExpression(
//            uTestInst.operationType,
//            uTestInst.lhv.accept(this) as UTestExpression,
//            uTestInst.rhv.accept(this) as UTestExpression,
//            uTestInst.type
//        )
    }

    override fun visitUTestGetStaticFieldExpression(uTestInst: UTestGetStaticFieldExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestBooleanExpression(uTestInst: UTestBooleanExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestByteExpression(uTestInst: UTestByteExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestShortExpression(uTestInst: UTestShortExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestIntExpression(uTestInst: UTestIntExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestLongExpression(uTestInst: UTestLongExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestFloatExpression(uTestInst: UTestFloatExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestDoubleExpression(uTestInst: UTestDoubleExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestCharExpression(uTestInst: UTestCharExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestStringExpression(uTestInst: UTestStringExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestNullExpression(uTestInst: UTestNullExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }

    override fun visitUTestGetFieldExpression(uTestInst: UTestGetFieldExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newInstanceValue = uTestInst.instance.accept(this) as UTestExpression
        return if (newInstanceValue == uTestInst.instance) {
            uTestInst
        } else {
            val newUTestGetField = UTestGetFieldExpression(
                newInstanceValue,
                uTestInst.field
            )
            remappedInstructions[uTestInst] = newUTestGetField
            newUTestGetField
        }
    }

    override fun visitUTestArrayLengthExpression(uTestInst: UTestArrayLengthExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newArrayInstance = uTestInst.arrayInstance.accept(this) as UTestExpression
        return if (newArrayInstance == uTestInst.arrayInstance) {
            uTestInst
        } else {
            val newUTestArrayLength = UTestArrayLengthExpression(
                newArrayInstance
            )
            remappedInstructions[uTestInst] = newUTestArrayLength
            newUTestArrayLength
        }
    }

    override fun visitUTestArrayGetExpression(uTestInst: UTestArrayGetExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newArrayInstance = uTestInst.arrayInstance.accept(this) as UTestExpression
        val newIndex = uTestInst.arrayInstance.accept(this) as UTestExpression
        return if (newArrayInstance == uTestInst.arrayInstance && newIndex == uTestInst.index) {
            uTestInst
        } else {
            val newUTestArrayGet = UTestArrayGetExpression(
                newArrayInstance,
                newIndex
            )
            remappedInstructions[uTestInst] = newUTestArrayGet
            newUTestArrayGet
        }
    }

    override fun visitUTestArraySetStatement(uTestInst: UTestArraySetStatement): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newArrayInstance = uTestInst.arrayInstance.accept(this) as UTestExpression
        val newIndex = uTestInst.index.accept(this) as UTestExpression
        val newValue = uTestInst.setValueExpression.accept(this) as UTestExpression
        return if (newArrayInstance == uTestInst.arrayInstance && newIndex == uTestInst.index && newValue == uTestInst.setValueExpression) {
            uTestInst
        } else {
            val newUTestArraySet = UTestArraySetStatement(
                newArrayInstance,
                newIndex,
                newValue
            )
            remappedInstructions[uTestInst] = newUTestArraySet
            newUTestArraySet
        }
    }

    override fun visitUTestCreateArrayExpression(uTestInst: UTestCreateArrayExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newSize = uTestInst.size.accept(this) as UTestExpression
        return if (newSize == uTestInst.size) {
            uTestInst
        } else {
            val newUTestCreate = UTestCreateArrayExpression(
                uTestInst.elementType,
                newSize
            )
            remappedInstructions[uTestInst] = newUTestCreate
            newUTestCreate
        }
    }

    override fun visitUTestCastExpression(uTestInst: UTestCastExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        val newExpr = uTestInst.expr.accept(this) as UTestExpression
        return if (newExpr == uTestInst.expr) {
            uTestInst
        } else {
            val newCast = UTestCastExpression(
                newExpr,
                uTestInst.type
            )
            remappedInstructions[uTestInst] = newCast
            newCast
        }
    }

    override fun visitUTestClassExpression(uTestInst: UTestClassExpression): UTestInst {
        if (uTestInst in remappedInstructions) return remappedInstructions[uTestInst]!!
        return uTestInst
    }
}