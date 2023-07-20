package org.usvm.instrumentation.executor

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.instrumentation.testcase.api.UTestExecutionExceptionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionSuccessResult
import org.usvm.instrumentation.testcase.descriptor.UTestConstantDescriptor
import org.usvm.instrumentation.util.UTestCreator

class SimpleUTestConcreteExecutor: UTestConcreteExecutorTest() {


    companion object {

        @BeforeAll
        @JvmStatic
        fun initClasspath() {
            testJarPath = listOf("build/libs/usvm-jvm-instrumentation-test.jar")
            init()
        }

        @AfterAll
        @JvmStatic
        fun close() {
            uTestConcreteExecutor.close()
        }
    }

    @Test
    fun `simple test`() = executeTest {
        val uTest = UTestCreator.C.lol(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
    }

    @Test
    fun `arithmetic operation`() = executeTest {
        val uTest = UTestCreator.A.arithmeticOperation(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        val result = res.result
        assert(result != null)
        assert(result is UTestConstantDescriptor.Int && result.value == 239)
    }

    @Test
    fun `static fields test`() = executeTest {
        val uTest = UTestCreator.A.isA(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
    }

    @Test
    fun `static method test`() = executeTest {
        val uTest = UTestCreator.A.javaStdLibCall(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `method with uTestCondition test`() = executeTest {
        val uTest = UTestCreator.A.indexOfWithIf(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `method with inner class usage test`() = executeTest {
        val uTest = UTestCreator.Arrays.checkAllSamePoints(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `singleton test`() = executeTest {
        val uTest = UTestCreator.Singleton.addToArray(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `nested class test`() = executeTest {
        val uTest = UTestCreator.NestedClass.getB(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `simple class mock test`() = executeTest {
        val uTest = UTestCreator.A.mock(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        val result = res.result
        assert(result != null)
        assert(result is UTestConstantDescriptor.Int && result.value == 239)
    }

    @Test
    fun `mock static method test`() = executeTest {
        val uTest = UTestCreator.A.mockStaticMethod(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        val result = res.result
        assert(result != null)
        assert(result is UTestConstantDescriptor.Int && result.value == 239)
    }

    @Test
    fun `simple abstract class mock test`() = executeTest {
        val uTest = UTestCreator.A.mockAbstractClass(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        val result = res.result
        assert(result != null)
        assert(result is UTestConstantDescriptor.Int && result.value == 240)
    }

    @Test
    fun `mock java random`() = executeTest {
        val uTest = UTestCreator.A.mockRandom(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        val result = res.result
        assert(result != null)
        assert(result is UTestConstantDescriptor.Int && result.value == 239)
    }

    @Test
    fun `simple abstract class partially mocked test`() = executeTest {
        val uTest = UTestCreator.A.mockAbstractClass1(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        val result = res.result
        assert(result != null)
        //Expected behavior!!
        assert(result is UTestConstantDescriptor.Int && result.value == 1)
    }

    @Test
    fun `multiple mock`() = executeTest {
        val uTest = UTestCreator.A.mockMultiple(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        val result = res.result
        assert(result != null)
        assert(result is UTestConstantDescriptor.Int && result.value == 239)
    }

    @Test
    fun `simple interface mock test`() = executeTest {
        val uTest = UTestCreator.A.mockInterface(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        val result = res.result
        assert(result != null)
        assert(result is UTestConstantDescriptor.Int && result.value == 239)
    }

    @Test
    fun `simple interface with default method mock test`() = executeTest {
        val uTest = UTestCreator.A.mockInterfaceWithDefaultMock(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        val result = res.result
        assert(result != null)
        assert(result is UTestConstantDescriptor.Int && result.value == 239)
    }

    @Test
    fun `method with bug`() = executeTest {
        val uTest = UTestCreator.A.methodWithBug(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionExceptionResult)
    }

}