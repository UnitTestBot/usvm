package org.usvm.instrumentation.executor

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.instrumentation.jacodb.transform.JcRuntimeTraceInstrumenterFactory
import org.usvm.instrumentation.testcase.statement.UTestExecutionSuccessResult
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.util.TracePrinter
import org.usvm.instrumentation.util.UTestCreator
import java.io.File
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class UTestConcreteExecutorTest {

    companion object {

        const val testJarPath = "build/libs/usvm-instrumentation-test.jar"
        lateinit var jcClasspath: JcClasspath
        lateinit var uTestConcreteExecutor: UTestConcreteExecutor

        @BeforeAll
        @JvmStatic
        fun init() = runBlocking {
            System.setProperty("java.home", "/usr/lib/jvm/java-8-openjdk/")
            val cp = listOf(File(testJarPath).absoluteFile)
            val db = jacodb {
                loadByteCode(cp)
                installFeatures(InMemoryHierarchy)
            }
            jcClasspath = db.classpath(cp)
        }


        fun createUTestConcreteExecutor(): UTestConcreteExecutor {
            return UTestConcreteExecutor(
                JcRuntimeTraceInstrumenterFactory::class,
                testJarPath,
                jcClasspath,
                InstrumentationModuleConstants.timeout
            )
        }
    }

    private fun initExecutor() {
        uTestConcreteExecutor = createUTestConcreteExecutor()
    }

    fun executeTest(body: suspend () -> Unit) {
        try {
            initExecutor()
            runBlocking {
                body.invoke()
            }
        } finally {
            uTestConcreteExecutor.close()
        }
    }

    @Test
    fun simpleTest() = executeTest {
        val uTest = UTestCreator.A.isA(jcClasspath)
        val res = uTestConcreteExecutor.execute(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        TracePrinter.printTraceToConsole(res.trace!!)
    }

    @Test
    fun testStaticsDescriptorBuilding() = executeTest {
        val uTest = UTestCreator.A.isA(jcClasspath)
        repeat(1) {
            val res = uTestConcreteExecutor.execute(uTest)
            assert(res is UTestExecutionSuccessResult)
            res as UTestExecutionSuccessResult
            println("Statics before = ${res.initialState.statics.entries.joinToString { "${it.key.name} to ${it.value}" }}")
            println("Statics after = ${res.resultState.statics.entries.joinToString { "${it.key.name} to ${it.value}" }}")
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun executeUTest1000times() = executeTest {
        val uTest = UTestCreator.A.indexOf(jcClasspath)
        repeat(1000) {
            val res = measureTimedValue { uTestConcreteExecutor.execute(uTest) }.also { println("T = ${it.duration}") }.value
            assert(res is UTestExecutionSuccessResult)
            println("Res of $it-th execution: $res")
        }
    }

    @Test
    fun staticCall() = executeTest {
        val uTest = UTestCreator.A.javaStdLibCall(jcClasspath)
        val res = uTestConcreteExecutor.execute(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }

    @Test
    fun `execute method with inner class usage`() = executeTest {
        val uTest = UTestCreator.Arrays.checkAllSamePoints(jcClasspath)
        val res = uTestConcreteExecutor.execute(uTest)
        assert(res is UTestExecutionSuccessResult)
        res as UTestExecutionSuccessResult
        assert(res.result != null)
    }
}
