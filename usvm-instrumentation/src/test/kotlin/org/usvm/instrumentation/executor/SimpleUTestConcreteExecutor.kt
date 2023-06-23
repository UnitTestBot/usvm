package org.usvm.instrumentation.executor

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.instrumentation.testcase.api.UTestExecutionExceptionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionSuccessResult
import org.usvm.instrumentation.util.UTestCreator

class SimpleUTestConcreteExecutor: UTestConcreteExecutorTest() {


    companion object {

        @BeforeAll
        @JvmStatic
        fun initClasspath() {
            testJarPath = listOf("build/libs/usvm-instrumentation-test.jar")
            init()
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
    fun `method with bug`() = executeTest {
        val uTest = UTestCreator.A.methodWithBug(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionExceptionResult)
    }

}