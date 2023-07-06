package org.usvm.instrumentation.testcase.api

import org.jacodb.api.*
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.int
import org.jacodb.api.ext.toType
import org.jacodb.api.ext.void
import org.jacodb.impl.types.JcArrayTypeImpl

/**
 * Api for UTestExpression
 * Used for specifying scenario of target method execution
 */
sealed class UTestExpression {
    abstract val type: JcType?
}

class UTestMockObject(
    override val type: JcType?,
    val fields: Map<JcField, UTestExpression>,
    val methods: Map<JcMethod, UTestExpression>
) : UTestExpression()

sealed class UTestCall : UTestExpression() {
    abstract val instance: UTestExpression?
    abstract val method: JcMethod?
    abstract val args: List<UTestExpression>
}

class UTestMethodCall(
    override val instance: UTestExpression,
    override val method: JcMethod,
    override val args: List<UTestExpression>
) : UTestCall() {
    override val type: JcType? = method.enclosingClass.classpath.findTypeOrNull(method.returnType)
}

class UTestStaticMethodCall(
    override val method: JcMethod,
    override val args: List<UTestExpression>
) : UTestCall() {
    override val instance: UTestExpression? = null
    override val type: JcType? = method.enclosingClass.classpath.findTypeOrNull(method.returnType)
}

class UTestConstructorCall(
    override val method: JcMethod,
    override val args: List<UTestExpression>
) : UTestCall() {
    override val instance: UTestExpression? = null
    override val type: JcType = method.enclosingClass.toType()
}

class UTestAllocateMemoryCall(
    val clazz: JcClassOrInterface
) : UTestCall() {
    override val instance: UTestExpression? = null
    override val method: JcMethod? = null
    override val args: List<UTestExpression> = listOf()
    override val type: JcType = clazz.toType()
}

sealed class UTestStatement : UTestExpression()

class UTestSetFieldStatement(
    val instance: UTestExpression,
    val field: JcField,
    val value: UTestExpression
) : UTestStatement() {
    override val type: JcType = field.enclosingClass.classpath.void
}

class UTestSetStaticFieldStatement(
    val field: JcField,
    val value: UTestExpression
) : UTestStatement() {
    override val type: JcType = field.enclosingClass.classpath.void
}


class UTestConditionExpression(
    val conditionType: ConditionType,
    val lhv: UTestExpression,
    val rhv: UTestExpression,
    val trueBranch: List<UTestStatement>,
    val elseBranch: List<UTestStatement>
) : UTestStatement() {
    override val type: JcType? =
        trueBranch.lastOrNull()?.type?.takeIf { elseBranch.isNotEmpty() } ?: lhv.type?.classpath?.void
}

class UTestArithmeticExpression(
    val operationType: ArithmeticOperationType,
    val lhv: UTestExpression,
    val rhv: UTestExpression,
    override val type: JcType
) : UTestExpression()

class UTestGetStaticFieldExpression(
    val field: JcField
) : UTestExpression() {
    override val type: JcType? = field.enclosingClass.classpath.findTypeOrNull(field.type)
}

sealed class UTestConstExpression<T> : UTestExpression() {
    abstract val value: T
}

class UTestBooleanExpression(
    override val value: Boolean,
    override val type: JcType
): UTestConstExpression<Boolean>()

class UTestByteExpression(
    override val value: Byte,
    override val type: JcType
): UTestConstExpression<Byte>()

class UTestShortExpression(
    override val value: Short,
    override val type: JcType
): UTestConstExpression<Short>()

class UTestIntExpression(
    override val value: Int,
    override val type: JcType
): UTestConstExpression<Int>()

class UTestLongExpression(
    override val value: Long,
    override val type: JcType
): UTestConstExpression<Long>()

class UTestFloatExpression(
    override val value: Float,
    override val type: JcType
): UTestConstExpression<Float>()

class UTestDoubleExpression(
    override val value: Double,
    override val type: JcType
): UTestConstExpression<Double>()

class UTestCharExpression(
    override val value: Char,
    override val type: JcType
): UTestConstExpression<Char>()

class UTestStringExpression(
    override val value: String,
    override val type: JcType
) : UTestConstExpression<String>()

class UTestNullExpression(
    override val type: JcType
) : UTestConstExpression<Any?>() {
    override val value = null
}

class UTestGetFieldExpression(
    val instance: UTestExpression,
    val field: JcField
) : UTestExpression() {
    override val type: JcType? = field.enclosingClass.classpath.findTypeOrNull(field.type)
}

class UTestArrayLengthExpression(
    val arrayInstance: UTestExpression
) : UTestExpression() {
    override val type: JcType? = arrayInstance.type?.classpath?.int
}

class UTestArrayGetExpression(
    val arrayInstance: UTestExpression,
    val index: UTestExpression
) : UTestExpression() {
    override val type: JcType? = (arrayInstance.type as? JcArrayType)?.elementType
}

class UTestArraySetStatement(
    val arrayInstance: UTestExpression,
    val index: UTestExpression,
    val setValueExpression: UTestExpression
) : UTestStatement() {
    override val type: JcType? = (arrayInstance.type as? JcArrayType)?.elementType
}

class UTestCreateArrayExpression(
    val elementType: JcType,
    val size: UTestExpression
) : UTestExpression() {
    override val type: JcType = JcArrayTypeImpl(elementType)
}

class UTestCastExpression(
    val expr: UTestExpression,
    override val type: JcType
) : UTestExpression()


enum class ConditionType {
    EQ, NEQ, GEQ, GT
}

enum class ArithmeticOperationType {

    //Arithmetic
    PLUS, SUB, MUL, DIV, REM,
    //UNARY
    //Relational
    EQ, NEQ, GT, GEQ, LT, LTQ,
    //Bitwise
    OR, AND, XOR
}
