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
            testJarPath = listOf("build/libs/usvm-instrumentation-test.jar")
            init()
        }

        @AfterAll
        @JvmStatic
        fun close() {
            uTestConcreteExecutor.close()
        }
    }

    @Test
    fun `simple`() = executeTest {
        val uTest = UTestCreator.C.lol(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
    }

    @Test
    fun `static fields`() = executeTest {
        val uTest = UTestCreator.A.isA(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
    }

    @Test
    fun `static method`() = executeTest {
        val uTest = UTestCreator.A.javaStdLibCall(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `method with uTestCondition`() = executeTest {
        val uTest = UTestCreator.A.indexOfWithIf(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `method with inner class usage`() = executeTest {
        val uTest = UTestCreator.Arrays.checkAllSamePoints(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `singleton`() = executeTest {
        val uTest = UTestCreator.Singleton.addToArray(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `nested class`() = executeTest {
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