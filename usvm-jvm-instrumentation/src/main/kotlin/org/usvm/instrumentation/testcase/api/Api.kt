package org.usvm.instrumentation.testcase.api

import org.jacodb.api.*
import org.jacodb.api.ext.*
import org.jacodb.impl.types.JcArrayTypeImpl

/**
 * Api for UTestExpression
 * Used for specifying scenario of target method execution
 */

sealed interface UTestInst {
    fun <T> accept(visitor: UTestInstVisitor<T>): T
}

sealed interface UTestExpression : UTestInst {
    val type: JcType?
}

sealed class UTestMock(
    override val type: JcType,
    open val fields: Map<JcField, UTestExpression>,
    open val methods: Map<JcMethod, List<UTestExpression>>
) : UTestExpression

/**
 * Mock for specific object
 */
class UTestMockObject(
    override val type: JcType,
    override val fields: Map<JcField, UTestExpression>,
    override val methods: Map<JcMethod, List<UTestExpression>>
) : UTestMock(type, fields, methods) {

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestMockObject(this)
    }

}

/**
 * Mock for all objects of type
 */
class UTestGlobalMock(
    override val type: JcType,
    override val fields: Map<JcField, UTestExpression>,
    override val methods: Map<JcMethod, List<UTestExpression>>
) : UTestMock(type, fields, methods) {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestGlobalMock(this)
    }
}

class UTestLambdaMock(
    override val type: JcType,
    val values: List<UTestExpression>
): UTestExpression {

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestLambdaMock(this)
    }
}

sealed interface UTestCall : UTestExpression {
    val instance: UTestExpression?
    val method: JcMethod?
    val args: List<UTestExpression>
}

class UTestMethodCall(
    override val instance: UTestExpression, override val method: JcMethod, override val args: List<UTestExpression>
) : UTestCall {
    override val type: JcType? = method.enclosingClass.classpath.findTypeOrNull(method.returnType)

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestMethodCall(this)
    }
}

class UTestStaticMethodCall(
    override val method: JcMethod, override val args: List<UTestExpression>
) : UTestCall {
    override val instance: UTestExpression? = null
    override val type: JcType? = method.enclosingClass.classpath.findTypeOrNull(method.returnType)

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestStaticMethodCall(this)
    }
}

class UTestConstructorCall(
    override val method: JcMethod, override val args: List<UTestExpression>
) : UTestCall {
    override val instance: UTestExpression? = null
    override val type: JcType = method.enclosingClass.toType()

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestConstructorCall(this)
    }
}

class UTestAllocateMemoryCall(
    val clazz: JcClassOrInterface
) : UTestCall {
    override val instance: UTestExpression? = null
    override val method: JcMethod? = null
    override val args: List<UTestExpression> = listOf()
    override val type: JcType = clazz.toType()

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestAllocateMemoryCall(this)
    }
}

sealed interface UTestStatement : UTestInst

class UTestSetFieldStatement(
    val instance: UTestExpression, val field: JcField, val value: UTestExpression
) : UTestStatement {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestSetFieldStatement(this)
    }
}

class UTestSetStaticFieldStatement(
    val field: JcField, val value: UTestExpression
) : UTestStatement {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestSetStaticFieldStatement(this)
    }
}


class UTestBinaryConditionExpression(
    val conditionType: ConditionType,
    val lhv: UTestExpression,
    val rhv: UTestExpression,
    val trueBranch: UTestExpression,
    val elseBranch: UTestExpression
) : UTestExpression {
    //TODO!! What if trueBranch and elseBranch have different types of the last instruction? Shouldn't we find their LCA?

    init {
        check(trueBranch.type == elseBranch.type) { "True and else branches should be equal" }
    }

    //Probably add functionality in jacodb?
    override val type: JcType? = trueBranch.type

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestBinaryConditionExpression(this)
    }
}

class UTestBinaryConditionStatement(
    val conditionType: ConditionType,
    val lhv: UTestExpression,
    val rhv: UTestExpression,
    val trueBranch: List<UTestStatement>,
    val elseBranch: List<UTestStatement>
) : UTestStatement {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestBinaryConditionStatement(this)
    }
}

class UTestArithmeticExpression(
    val operationType: ArithmeticOperationType,
    val lhv: UTestExpression,
    val rhv: UTestExpression,
    override val type: JcType
) : UTestExpression {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestArithmeticExpression(this)
    }
}

class UTestGetStaticFieldExpression(
    val field: JcField
) : UTestExpression {
    override val type: JcType? = field.enclosingClass.classpath.findTypeOrNull(field.type)

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestGetStaticFieldExpression(this)
    }
}

sealed class UTestConstExpression<T> : UTestExpression {
    abstract val value: T
}

class UTestBooleanExpression(
    override val value: Boolean, override val type: JcType
) : UTestConstExpression<Boolean>() {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestBooleanExpression(this)
    }
}

class UTestByteExpression(
    override val value: Byte, override val type: JcType
) : UTestConstExpression<Byte>() {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestByteExpression(this)
    }
}

class UTestShortExpression(
    override val value: Short, override val type: JcType
) : UTestConstExpression<Short>() {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestShortExpression(this)
    }
}

class UTestIntExpression(
    override val value: Int, override val type: JcType
) : UTestConstExpression<Int>() {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestIntExpression(this)
    }
}

class UTestLongExpression(
    override val value: Long, override val type: JcType
) : UTestConstExpression<Long>() {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestLongExpression(this)
    }
}

class UTestFloatExpression(
    override val value: Float, override val type: JcType
) : UTestConstExpression<Float>() {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestFloatExpression(this)
    }
}

class UTestDoubleExpression(
    override val value: Double, override val type: JcType
) : UTestConstExpression<Double>() {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestDoubleExpression(this)
    }
}

class UTestCharExpression(
    override val value: Char, override val type: JcType
) : UTestConstExpression<Char>() {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestCharExpression(this)
    }
}

class UTestStringExpression(
    override val value: String, override val type: JcType
) : UTestConstExpression<String>() {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestStringExpression(this)
    }
}

class UTestNullExpression(
    override val type: JcType
) : UTestConstExpression<Any?>() {
    override val value = null

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestNullExpression(this)
    }
}

class UTestGetFieldExpression(
    val instance: UTestExpression, val field: JcField
) : UTestExpression {
    override val type: JcType? = field.enclosingClass.classpath.findTypeOrNull(field.type)

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestGetFieldExpression(this)
    }
}

class UTestArrayLengthExpression(
    val arrayInstance: UTestExpression
) : UTestExpression {
    override val type: JcType? = arrayInstance.type?.classpath?.int

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestArrayLengthExpression(this)
    }
}

class UTestArrayGetExpression(
    val arrayInstance: UTestExpression, val index: UTestExpression
) : UTestExpression {
    override val type: JcType? = (arrayInstance.type as? JcArrayType)?.elementType

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestArrayGetExpression(this)
    }
}

class UTestArraySetStatement(
    val arrayInstance: UTestExpression, val index: UTestExpression, val setValueExpression: UTestExpression
) : UTestStatement {

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestArraySetStatement(this)
    }
}

class UTestCreateArrayExpression(
    val elementType: JcType, val size: UTestExpression
) : UTestExpression {
    override val type: JcType = JcArrayTypeImpl(elementType)

    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestCreateArrayExpression(this)
    }
}

class UTestCastExpression(
    val expr: UTestExpression, override val type: JcType
) : UTestExpression {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestCastExpression(this)
    }
}

class UTestClassExpression(
    override val type: JcType
) : UTestExpression {
    override fun <T> accept(visitor: UTestInstVisitor<T>): T {
        return visitor.visitUTestClassExpression(this)
    }
}


enum class ConditionType {
    EQ, NEQ, GEQ, GT
}

enum class ArithmeticOperationType {

    //Arithmetic
    PLUS, SUB, MUL, DIV, REM,

    //Relational
    EQ, NEQ, GT, GEQ, LT, LEQ,

    //Bitwise
    OR, AND, XOR
}
