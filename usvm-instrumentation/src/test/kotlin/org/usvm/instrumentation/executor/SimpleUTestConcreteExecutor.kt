package org.usvm.instrumentation.executor

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
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
    fun simpleTest() = executeTest {
        val uTest = UTestCreator.C.lol(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
    }

    @Test
    fun `execute static method`() = executeTest {
        val uTest = UTestCreator.A.javaStdLibCall(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `execute method with uTestCondition`() = executeTest {
        val uTest = UTestCreator.A.indexOfWithIf(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `execute method with inner class usage`() = executeTest {
        val uTest = UTestCreator.Arrays.checkAllSamePoints(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

}