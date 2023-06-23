package org.usvm.instrumentation.testcase.executor

import getFieldValue
import invokeWithAccessibility
import org.jacodb.api.JcField
import org.jacodb.api.ext.*
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.instrumentation.JcInstructionTracer.StaticFieldAccessType
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.util.toJavaClass
import org.usvm.instrumentation.util.toJavaConstructor
import org.usvm.instrumentation.util.toJavaField
import org.usvm.instrumentation.util.toJavaMethod
import setFieldValue
import java.lang.ClassCastException

class UTestExpressionExecutor(
    private val workerClassLoader: WorkerClassLoader,
    private val accessedStatics: MutableSet<Pair<JcField, StaticFieldAccessType>>
) {

    private val jcClasspath = workerClassLoader.jcClasspath


    private val executedModels: MutableMap<UTestExpression, Any?> = hashMapOf()

    fun executeUTestExpression(uTestExpression: UTestExpression): Result<Any?> =
        try {
            Result.success(exec(uTestExpression))
        } catch (e: Throwable) {
            Result.failure(e)
        }

    fun executeUTestExpressions(uTestExpressions: List<UTestExpression>): Result<Any?>? {
        var lastResult: Result<Any?>? = null
        for (uTestExpression in uTestExpressions) {
            lastResult = executeUTestExpression(uTestExpression)
            if (lastResult.isFailure) return lastResult
        }
        return lastResult
    }


    private fun exec(uTestExpression: UTestExpression) = executedModels.getOrPut(uTestExpression) {
        when (uTestExpression) {
            is UTestConstExpression<*> -> executeUTestConstant(uTestExpression)
            is UTestArrayLengthExpression -> executeUTestArrayLengthExpression(uTestExpression)
            is UTestArrayGetExpression -> executeUTestArrayGetExpression(uTestExpression)
            is UTestArraySetStatement -> executeUTestArraySetStatement(uTestExpression)
            is UTestCreateArrayExpression -> executeUTestArrayCreateExpression(uTestExpression)
            is UTestAllocateMemoryCall -> executeUTestAllocateMemoryCall(uTestExpression)
            is UTestConstructorCall -> executeConstructorCall(uTestExpression)
            is UTestMethodCall -> executeMethodCall(uTestExpression)
            is UTestStaticMethodCall -> executeUTestStaticMethodCall(uTestExpression)
            is UTestCastExpression -> executeUTestCastExpression(uTestExpression)
            is UTestGetFieldExpression -> executeUTestGetFieldExpression(uTestExpression)
            is UTestGetStaticFieldExpression -> executeUTestGetStaticFieldExpression(uTestExpression)
            is UTestMockObject -> executeUTestMockObject(uTestExpression)
            is UTestConditionExpression -> executeUTestConditionExpression(uTestExpression)
            is UTestSetFieldStatement -> executeUTestSetFieldStatement(uTestExpression)
            is UTestSetStaticFieldStatement -> executeUTestSetStaticFieldStatement(uTestExpression)
        }
    }

    private fun executeUTestConstant(uTestConstExpression: UTestConstExpression<*>): Any? = uTestConstExpression.value

    private fun executeUTestArrayLengthExpression(uTestArrayLengthExpression: UTestArrayLengthExpression): Any? {
        val arrayInstance = exec(uTestArrayLengthExpression.arrayInstance) ?: return null
        return when (arrayInstance) {
            is BooleanArray -> arrayInstance.size
            is ByteArray -> arrayInstance.size
            is ShortArray -> arrayInstance.size
            is IntArray -> arrayInstance.size
            is LongArray -> arrayInstance.size
            is FloatArray -> arrayInstance.size
            is DoubleArray -> arrayInstance.size
            is CharArray -> arrayInstance.size
            else -> (arrayInstance as Array<*>).size
        }
    }

    private fun executeUTestArrayGetExpression(uTestArrayGetExpression: UTestArrayGetExpression): Any? {
        val arrayInstance = exec(uTestArrayGetExpression.arrayInstance)
        val index = exec(uTestArrayGetExpression.index) as Int
        return when (uTestArrayGetExpression.type) {
            jcClasspath.boolean -> (arrayInstance as BooleanArray)[index]
            jcClasspath.byte -> (arrayInstance as ByteArray)[index]
            jcClasspath.short -> (arrayInstance as ShortArray)[index]
            jcClasspath.int -> (arrayInstance as IntArray)[index]
            jcClasspath.long -> (arrayInstance as LongArray)[index]
            jcClasspath.double -> (arrayInstance as DoubleArray)[index]
            jcClasspath.float -> (arrayInstance as FloatArray)[index]
            jcClasspath.char -> (arrayInstance as CharArray)[index]
            else -> (arrayInstance as Array<*>)[index]
        }
    }

    private fun executeUTestArraySetStatement(uTestArraySetStatement: UTestArraySetStatement) {
        val arrayInstance = exec(uTestArraySetStatement.arrayInstance)
        val index = exec(uTestArraySetStatement.index) as Int
        val setValue = exec(uTestArraySetStatement.setValueExpression)

        when (uTestArraySetStatement.type) {
            jcClasspath.boolean -> (arrayInstance as BooleanArray).set(index, setValue as Boolean)
            jcClasspath.byte -> (arrayInstance as ByteArray).set(index, setValue as Byte)
            jcClasspath.short -> (arrayInstance as ShortArray).set(index, setValue as Short)
            jcClasspath.int -> (arrayInstance as IntArray).set(index, setValue as Int)
            jcClasspath.long -> (arrayInstance as LongArray).set(index, setValue as Long)
            jcClasspath.double -> (arrayInstance as DoubleArray).set(index, setValue as Double)
            jcClasspath.float -> (arrayInstance as FloatArray).set(index, setValue as Float)
            jcClasspath.char -> (arrayInstance as CharArray).set(index, setValue as Char)
            else -> (arrayInstance as Array<Any?>).set(index, setValue)
        }
    }


    private fun executeUTestArrayCreateExpression(uTestCreateArrayExpression: UTestCreateArrayExpression): Any {
        val size = exec(uTestCreateArrayExpression.size) as Int
        return when (uTestCreateArrayExpression.elementType) {
                jcClasspath.boolean -> BooleanArray(size)
                jcClasspath.byte -> ByteArray(size)
                jcClasspath.short -> ShortArray(size)
                jcClasspath.int -> IntArray(size)
                jcClasspath.long -> LongArray(size)
                jcClasspath.double -> DoubleArray(size)
                jcClasspath.float -> FloatArray(size)
                jcClasspath.char -> CharArray(size)
                else -> java.lang.reflect.Array.newInstance(uTestCreateArrayExpression.elementType.toJavaClass(workerClassLoader), size)
            }
    }

    private fun executeUTestAllocateMemoryCall(uTestAllocateMemoryCall: UTestAllocateMemoryCall): Any {
        TODO()
    }

    private fun executeUTestMockObject(uTestMockObject: UTestMockObject): Any? {
        //use mockito?
        TODO()
    }

    private fun executeUTestSetFieldStatement(uTestSetFieldStatement: UTestSetFieldStatement) {
        val instance = exec(uTestSetFieldStatement.instance)
        val field = uTestSetFieldStatement.field.toJavaField(workerClassLoader)
        val fieldValue = exec(uTestSetFieldStatement.value)
        field?.setFieldValue(instance, fieldValue)
    }

    private fun executeUTestSetStaticFieldStatement(uTestSetFieldStatement: UTestSetStaticFieldStatement) {
        val field = uTestSetFieldStatement.field.toJavaField(workerClassLoader)
        val fieldValue = exec(uTestSetFieldStatement.value)
        accessedStatics.add(uTestSetFieldStatement.field to StaticFieldAccessType.SET)
        field?.setFieldValue(null, fieldValue)
    }

    private fun executeUTestConditionExpression(uTestConditionExpression: UTestConditionExpression): Any? {
        val lCond = exec(uTestConditionExpression.lhv)
        val rCond = exec(uTestConditionExpression.rhv)
        val res =
            when (uTestConditionExpression.conditionType) {
                ConditionType.EQ -> lCond == rCond
                ConditionType.NEQ -> lCond != rCond
                ConditionType.GEQ -> (lCond as Comparable<Any?>) >= rCond
                ConditionType.GT -> (lCond as Comparable<Any?>) > rCond
            }
        return if (res) {
            executeUTestExpressions(uTestConditionExpression.trueBranch)
        } else {
            executeUTestExpressions(uTestConditionExpression.elseBranch)
        }
    }

    private fun executeUTestGetFieldExpression(uTestGetFieldExpression: UTestGetFieldExpression): Any? {
        val instance = exec(uTestGetFieldExpression.instance)
        val jField = uTestGetFieldExpression.field.toJavaField(workerClassLoader)
        return jField?.getFieldValue(instance)
    }

    private fun executeUTestGetStaticFieldExpression(uTestGetStaticFieldExpression: UTestGetStaticFieldExpression): Any? {
        val jField = uTestGetStaticFieldExpression.field.toJavaField(workerClassLoader)
        accessedStatics.add(uTestGetStaticFieldExpression.field to StaticFieldAccessType.GET)
        return jField?.getFieldValue(null)
    }

    private fun executeUTestStaticMethodCall(uTestStaticMethodCall: UTestStaticMethodCall): Any? {
        val jMethod = uTestStaticMethodCall.method.toJavaMethod(workerClassLoader)
        val args = uTestStaticMethodCall.args.map { exec(it) }
        return jMethod.invokeWithAccessibility(null, args)
    }

    private fun executeUTestCastExpression(uTestCastExpression: UTestCastExpression): Any? {
        val castExpr = exec(uTestCastExpression.expr)
        val toTypeJClass = uTestCastExpression.type.toJavaClass(workerClassLoader)
        return try {
            toTypeJClass.cast(castExpr)
        } catch (e: ClassCastException) {
            throw TestExecutorException("Cant cast object of type ${uTestCastExpression.expr.type} to ${uTestCastExpression.type}")
        }
    }

    private fun executeConstructorCall(uConstructorCall: UTestConstructorCall): Any {
        val jConstructor = uConstructorCall.method.toJavaConstructor(workerClassLoader)
        val args = uConstructorCall.args.map { exec(it) }
        return jConstructor.newInstance(*args.toTypedArray())
    }

    private fun executeMethodCall(uMethodCall: UTestMethodCall): Any? {
        val instance = exec(uMethodCall.instance)
        val args = uMethodCall.args.map { exec(it) }
        val jMethod = uMethodCall.method.toJavaMethod(workerClassLoader)
        return jMethod.invokeWithAccessibility(instance, args)
    }

}

class TestExecutorException(msg: String) : Exception(msg)