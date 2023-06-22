package org.usvm.instrumentation.executor

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.instrumentation.testcase.api.UTestExecutionSuccessResult
import org.usvm.instrumentation.util.TracePrinter
import org.usvm.instrumentation.util.UTestCreator
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.isRegularFile
import kotlin.streams.toList

class GuavaUTestConcreteExecutor: UTestConcreteExecutorTest() {

    companion object {

        @BeforeAll
        @JvmStatic
        fun initClasspath() {
            val guavaJarsPath = "src/test/resources/guava-26.0/"
            val guavaJars = Files.walk(Paths.get(guavaJarsPath))
                .filter { it.isRegularFile() }
                .map { it.absolutePathString() }
                .toList()
            testJarPath = guavaJars
            init()
        }
    }

//    @Test
//    fun `Throwables getRootCause`() = executeTest {
//        val uTest = UTestCreator.Throwables.getRootCause(jcClasspath)
//        val res = uTestConcreteExecutor.executeAsync(uTest)
//        println(res)
//        assert(res is UTestExecutionSuccessResult)
//        res as UTestExecutionSuccessResult
//    }

    @Test
    fun `Doubles join`() = executeTest {
        val uTest = UTestCreator.Doubles.join(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        TracePrinter.printTraceToConsole(res.trace!!)
    }


}