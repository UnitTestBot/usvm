package org.usvm.instrumentation.executor

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.impl.JcRamErsSettings
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.instrumentation.instrumentation.JcRuntimeTraceInstrumenterFactory
import org.usvm.instrumentation.util.InstrumentationModuleConstants
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
                persistenceImpl(JcRamErsSettings)
                loadByteCode(cp)
                installFeatures(InMemoryHierarchy)
                jre = File(InstrumentationModuleConstants.pathToJava)
            }
            jcClasspath = db.classpath(cp)
            uTestConcreteExecutor = createUTestConcreteExecutor()
        }

        private fun createUTestConcreteExecutor(): UTestConcreteExecutor {
            return UTestConcreteExecutor(
                JcRuntimeTraceInstrumenterFactory::class,
                testJarPath,
                jcClasspath,
                InstrumentationModuleConstants.testExecutionTimeout
            )
        }
    }


    fun executeTest(body: suspend () -> Unit) {
        try {
            runBlocking {
                body.invoke()
            }
        } finally {
        }
    }

}
