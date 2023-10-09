package org.usvm.fuzzer.generator

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcType
import org.jacodb.api.TypeName
import org.jacodb.api.ext.*
import org.jacodb.impl.cfg.util.isPrimitive
import org.usvm.fuzzer.util.FuzzingContext
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.util.getTypename
import org.usvm.instrumentation.util.isPrimitiveArray
import org.usvm.instrumentation.util.stringType
import org.usvm.instrumentation.util.toJcType
import java.util.*

class DataGenerator(
    val jcClasspath: JcClasspath,
) {

    fun generateRandomParameterValue(
        parameterType: TypeName,
        depth: Int = 0
    ): Pair<UTestExpression?, List<UTestInst>> {
        val parameterJcType = parameterType.toJcType(jcClasspath)!!
        if (parameterType.isPrimitive || parameterType == jcClasspath.stringType().getTypename()) {
            return generatePrimitive(parameterJcType) to listOf()
        }
        if (parameterType.isPrimitiveArray()) {
            return generatePrimitiveArray(parameterJcType.ifArrayGetElementType!!)
        }
        return generateObject(parameterJcType, depth + 1)
    }

    //Null if random
    private fun generatePrimitive(type: JcType, value: Any? = null) =
        when (type) {
            jcClasspath.boolean -> UTestBooleanExpression(
                (value as? Boolean) ?: FuzzingContext.nextBoolean(),
                jcClasspath.int
            )

            jcClasspath.byte -> UTestByteExpression((value as? Byte) ?: FuzzingContext.nextByte(), jcClasspath.byte)
            jcClasspath.short -> UTestShortExpression(
                (value as? Short) ?: FuzzingContext.nextShort(),
                jcClasspath.short
            )

            jcClasspath.int -> UTestIntExpression((value as? Int) ?: FuzzingContext.nextInt(), jcClasspath.int)
            jcClasspath.long -> UTestLongExpression((value as? Long) ?: FuzzingContext.nextLong(), jcClasspath.long)
            jcClasspath.float -> UTestFloatExpression(
                (value as? Float) ?: FuzzingContext.nextFloat(),
                jcClasspath.float
            )

            jcClasspath.double -> UTestDoubleExpression(
                (value as? Double) ?: FuzzingContext.nextDouble(),
                jcClasspath.double
            )

            jcClasspath.char -> UTestCharExpression((value as? Char) ?: FuzzingContext.nextChar(), jcClasspath.char)
            jcClasspath.stringType() -> UTestStringExpression(
                (value as? String) ?: FuzzingContext.nextString(),
                jcClasspath.stringType()
            )

            else -> error("Trying to generate not primitive value")
        }

    private fun generatePrimitiveArray(elementType: JcType): Pair<UTestExpression, List<UTestInst>> {
        val arraySize = FuzzingContext.nextInt(5)
        val array = when (elementType) {
            jcClasspath.boolean -> FuzzingContext.nextBooleanArray(arraySize).toList()
            jcClasspath.byte -> FuzzingContext.nextByteArray(arraySize).toList()
            jcClasspath.short -> FuzzingContext.nextShortArray(arraySize).toList()
            jcClasspath.int -> FuzzingContext.nextIntArray(arraySize).toList()
            jcClasspath.long -> FuzzingContext.nextLongArray(arraySize).toList()
            jcClasspath.float -> FuzzingContext.nextFloatArray(arraySize).toList()
            jcClasspath.double -> FuzzingContext.nextDoubleArray(arraySize).toList()
            jcClasspath.char -> FuzzingContext.nextCharArray(arraySize).toList()
            jcClasspath.stringType() -> FuzzingContext.nextStringArray(arraySize).toList()
            else -> error("Trying to generate not primitive value")
        }
        return createUTestPrimitiveArray(elementType, array)
    }


    private fun <T> createUTestPrimitiveArray(
        elementType: JcType,
        values: List<T>
    ): Pair<UTestExpression, List<UTestInst>> {
        val initStatements = mutableListOf<UTestInst>()
        val size = values.size
        val instance = UTestCreateArrayExpression(elementType, UTestIntExpression(size, jcClasspath.int))
        for ((ind, value) in values.withIndex()) {
            val setValueExpr = generatePrimitive(elementType, value)
            initStatements.add(UTestArraySetStatement(instance, UTestIntExpression(ind, jcClasspath.int), setValueExpr))
        }
        return instance to initStatements
    }

    fun generateObject(type: JcType, depth: Int): Pair<UTestExpression, List<UTestInst>> {
        val jcClass = type.toJcClassOrInterface(jcClasspath)!!
        val constructor = jcClass.constructors.minBy { it.parameters.size }
        val initStatements = mutableListOf<UTestInst>()
        val argInstances = mutableListOf<UTestExpression>()
        constructor.parameters.map {
            val (instance, initStmts) = generateRandomParameterValue(it.type, depth)
            argInstances.add(instance ?: UTestNullExpression(it.type.toJcType(jcClasspath)!!))
            initStatements.addAll(initStmts)
        }
        return UTestConstructorCall(constructor, argInstances) to initStatements
    }

    private fun JcType.toJcClassOrInterface(jcClasspath: JcClasspath) = jcClasspath.findClassOrNull(typeName)
}
