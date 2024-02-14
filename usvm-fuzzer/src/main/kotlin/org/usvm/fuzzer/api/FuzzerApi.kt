package org.usvm.fuzzer.api

import org.jacodb.api.*
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.instrumentation.testcase.api.*

/**
 * Api for UTypedTestExpression
 * Used for specifying scenario of target method execution
 */

sealed interface UTypedTestInst {
    fun <T> accept(visitor: UTypedTestInstVisitor<T>): T
}

sealed interface UTypedTestExpression : UTypedTestInst {
    val type: JcTypeWrapper?
}

sealed class UTypedTestMock(
    override val type: JcTypeWrapper,
    open val fields: Map<JcField, UTypedTestExpression>,
    open val methods: Map<JcMethod, List<UTypedTestExpression>>
) : UTypedTestExpression

/**
 * Mock for specific object
 */
class UTypedTestMockObject(
    override val type: JcTypeWrapper,
    override val fields: Map<JcField, UTypedTestExpression>,
    override val methods: Map<JcMethod, List<UTypedTestExpression>>
) : UTypedTestMock(type, fields, methods) {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestMockObject(this)
    }

}

/**
 * Mock for all objects of type
 */
class UTypedTestGlobalMock(
    override val type: JcTypeWrapper,
    override val fields: Map<JcField, UTypedTestExpression>,
    override val methods: Map<JcMethod, List<UTypedTestExpression>>
) : UTypedTestMock(type, fields, methods) {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestGlobalMock(this)
    }
}

class UTypedTestLambdaMock(
    override val type: JcTypeWrapper,
    val values: List<UTypedTestExpression>
): UTypedTestExpression {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestLambdaMock(this)
    }
}


sealed interface UTypedTestCall : UTypedTestExpression {
    val instance: UTypedTestExpression?
    val method: JcMethod?
    val args: List<UTypedTestExpression>
}

class UTypedTestMethodCall(
    override val instance: UTypedTestExpression,
    override val method: JcMethod,
    override val args: List<UTypedTestExpression>,
    override val type: JcTypeWrapper? = null
) : UTypedTestCall {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestMethodCall(this)
    }
}

class UTypedTestStaticMethodCall(
    override val method: JcMethod,
    override val args: List<UTypedTestExpression>,
    override val type: JcTypeWrapper? = null
) : UTypedTestCall {
    override val instance: UTypedTestExpression? = null


    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestStaticMethodCall(this)
    }
}

class UTypedTestConstructorCall(
    override val method: JcMethod,
    override val args: List<UTypedTestExpression>,
    override val type: JcTypeWrapper?
) : UTypedTestCall {
    override val instance: UTypedTestExpression? = null


    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestConstructorCall(this)
    }
}

class UTypedTestAllocateMemoryCall(
    val clazz: JcClassOrInterface, override val type: JcTypeWrapper
) : UTypedTestCall {
    override val instance: UTypedTestExpression? = null
    override val method: JcMethod? = null
    override val args: List<UTypedTestExpression> = listOf()


    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestAllocateMemoryCall(this)
    }
}


sealed interface UTypedTestStatement : UTypedTestInst

class UTypedTestSetFieldStatement(
    val instance: UTypedTestExpression, val field: JcField, val value: UTypedTestExpression
) : UTypedTestStatement {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestSetFieldStatement(this)
    }
}

class UTypedTestSetStaticFieldStatement(
    val field: JcField, val value: UTypedTestExpression
) : UTypedTestStatement {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestSetStaticFieldStatement(this)
    }
}


class UTypedTestBinaryConditionExpression(
    val conditionType: ConditionType,
    val lhv: UTypedTestExpression,
    val rhv: UTypedTestExpression,
    val trueBranch: UTypedTestExpression,
    val elseBranch: UTypedTestExpression
) : UTypedTestExpression {
    //TODO!! What if trueBranch and elseBranch have different types of the last instruction? Shouldn't we find their LCA?

    init {
        check(trueBranch.type == elseBranch.type) { "True and else branches should be equal" }
    }

    //Probably add functionality in jacodb?
    override val type: JcTypeWrapper? = trueBranch.type

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestBinaryConditionExpression(this)
    }
}

class UTypedTestBinaryConditionStatement(
    val conditionType: ConditionType,
    val lhv: UTypedTestExpression,
    val rhv: UTypedTestExpression,
    val trueBranch: List<UTypedTestStatement>,
    val elseBranch: List<UTypedTestStatement>
) : UTypedTestStatement {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestBinaryConditionStatement(this)
    }
}

class UTypedTestArithmeticExpression(
    val operationType: ArithmeticOperationType,
    val lhv: UTypedTestExpression,
    val rhv: UTypedTestExpression,
    override val type: JcTypeWrapper
) : UTypedTestExpression {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestArithmeticExpression(this)
    }
}

class UTypedTestGetStaticFieldExpression(
    val field: JcField, override val type: JcTypeWrapper?
) : UTypedTestExpression {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestGetStaticFieldExpression(this)
    }
}

sealed class UTypedTestConstExpression<T> : UTypedTestExpression {
    abstract val value: T
}

class UTypedTestBooleanExpression(
    override val value: Boolean, override val type: JcTypeWrapper
) : UTypedTestConstExpression<Boolean>() {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestBooleanExpression(this)
    }
}

class UTypedTestByteExpression(
    override val value: Byte, override val type: JcTypeWrapper
) : UTypedTestConstExpression<Byte>() {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestByteExpression(this)
    }
}

class UTypedTestShortExpression(
    override val value: Short, override val type: JcTypeWrapper
) : UTypedTestConstExpression<Short>() {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestShortExpression(this)
    }
}

class UTypedTestIntExpression(
    override val value: Int, override val type: JcTypeWrapper
) : UTypedTestConstExpression<Int>() {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestIntExpression(this)
    }
}

class UTypedTestLongExpression(
    override val value: Long, override val type: JcTypeWrapper
) : UTypedTestConstExpression<Long>() {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestLongExpression(this)
    }
}

class UTypedTestFloatExpression(
    override val value: Float, override val type: JcTypeWrapper
) : UTypedTestConstExpression<Float>() {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestFloatExpression(this)
    }
}

class UTypedTestDoubleExpression(
    override val value: Double, override val type: JcTypeWrapper
) : UTypedTestConstExpression<Double>() {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestDoubleExpression(this)
    }
}

class UTypedTestCharExpression(
    override val value: Char, override val type: JcTypeWrapper
) : UTypedTestConstExpression<Char>() {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestCharExpression(this)
    }
}

class UTypedTestStringExpression(
    override val value: String, override val type: JcTypeWrapper
) : UTypedTestConstExpression<String>() {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestStringExpression(this)
    }
}

class UTypedTestNullExpression(
    override val type: JcTypeWrapper
) : UTypedTestConstExpression<Any?>() {
    override val value = null

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestNullExpression(this)
    }
}

class UTypedTestGetFieldExpression(
    val instance: UTypedTestExpression,
    val field: JcField,
    override val type: JcTypeWrapper?
) : UTypedTestExpression {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestGetFieldExpression(this)
    }
}

class UTypedTestArrayLengthExpression(
    val arrayInstance: UTypedTestExpression,
    override val type: JcTypeWrapper
) : UTypedTestExpression {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestArrayLengthExpression(this)
    }
}

class UTypedTestArrayGetExpression(
    val arrayInstance: UTypedTestExpression,
    val index: UTypedTestExpression,
    override val type: JcTypeWrapper?
) : UTypedTestExpression {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestArrayGetExpression(this)
    }
}

class UTypedTestArraySetStatement(
    val arrayInstance: UTypedTestExpression,
    val index: UTypedTestExpression,
    val setValueExpression: UTypedTestExpression
) : UTypedTestStatement {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestArraySetStatement(this)
    }
}

class UTypedTestCreateArrayExpression(
    val elementType: JcTypeWrapper,
    val size: UTypedTestExpression,
    override val type: JcTypeWrapper
) : UTypedTestExpression {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestCreateArrayExpression(this)
    }
}

class UTypedTestCastExpression(
    val expr: UTypedTestExpression, override val type: JcTypeWrapper
) : UTypedTestExpression {
    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestCastExpression(this)
    }
}

class UTypedTestClassExpression(
    override val type: JcTypeWrapper
) : UTypedTestExpression {

    override fun <T> accept(visitor: UTypedTestInstVisitor<T>): T {
        return visitor.visitUTypedTestClassExpression(this)
    }
}
