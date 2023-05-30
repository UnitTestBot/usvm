package org.usvm.instrumentation.executor

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.jacodb.transform.JcRuntimeTraceInstrumenterFactory
import org.usvm.instrumentation.testcase.statement.UTestExecutionSuccessResult
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.util.UTestCreator
import java.io.File
import java.nio.file.Paths

class UTestConcreteExecutorTest {

    companion object {

        const val testJarPath = "build/libs/usvm-instrumentation-test.jar"
        lateinit var jcClasspath: JcClasspath

        @BeforeAll
        @JvmStatic
        fun init() = runBlocking {
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

    @Test
    fun testStaticsDescriptorBuilding() {
        val uTestConcreteExecutor = createUTestConcreteExecutor()
        runBlocking {
            val uTest = UTestCreator.A.isA(jcClasspath)
            repeat(1) {
                val res = uTestConcreteExecutor.execute(uTest)
                assert(res is UTestExecutionSuccessResult)
                res as UTestExecutionSuccessResult
                println("Statics before = ${res.initialState.statics.entries.joinToString { "${it.key.name} to ${it.value}" }}")
                println("Statics after = ${res.resultState.statics.entries.joinToString { "${it.key.name} to ${it.value}" }}")
            }
        }
        uTestConcreteExecutor.close()
    }

    @Test
    fun executeUTest100times() {
        val uTestConcreteExecutor = createUTestConcreteExecutor()
        val uTest = UTestCreator.A.indexOf(jcClasspath)
        repeat(1000) {
            runBlocking {
                val res = uTestConcreteExecutor.execute(uTest)
                println("Res of $it-th execution: $res")
            }
        }
        uTestConcreteExecutor.close()
    }
}
