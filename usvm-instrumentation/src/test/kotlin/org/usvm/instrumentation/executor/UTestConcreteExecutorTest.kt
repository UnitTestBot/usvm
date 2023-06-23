package org.usvm.instrumentation.executor

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.instrumentation.instrumentation.JcRuntimeTraceInstrumenterFactory
import org.usvm.instrumentation.testcase.api.UTestExecutionSuccessResult
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.util.TracePrinter
import org.usvm.instrumentation.util.UTestCreator
import java.io.File

abstract class UTestConcreteExecutorTest {


    companion object {
        lateinit var testJarPath: List<String>
        lateinit var jcClasspath: JcClasspath
        lateinit var uTestConcreteExecutor: UTestConcreteExecutor


        @JvmStatic
        fun init() = runBlocking {
            val cp = testJarPath.map { File(it) }
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

}
