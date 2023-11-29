package org.usvm.instrumentation.executor

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.instrumentation.instrumentation.JcRuntimeTraceInstrumenterFactory
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile

abstract class UTestConcreteExecutorTest {


    companion object {
        lateinit var testJarPath: List<String>
        lateinit var jcClasspath: JcClasspath
        lateinit var jcPersistenceLocation: String
        lateinit var uTestConcreteExecutor: UTestConcreteExecutor


        @JvmStatic
        fun init() = runBlocking {
            val cp = testJarPath.map { File(it) }
            jcPersistenceLocation = createTempFile().absolutePathString()
            val db = jacodb {
                loadByteCode(cp)
                installFeatures(InMemoryHierarchy)
                jre = File(InstrumentationModuleConstants.pathToJava)
                persistent(location = jcPersistenceLocation)
            }
            db.awaitBackgroundJobs()
            jcClasspath = db.classpath(cp)
            uTestConcreteExecutor = createUTestConcreteExecutor()
        }

        private fun createUTestConcreteExecutor(): UTestConcreteExecutor {
            return UTestConcreteExecutor(
                instrumentationClassFactory = JcRuntimeTraceInstrumenterFactory::class,
                testingProjectClasspath = testJarPath,
                jcClasspath = jcClasspath,
                jcPersistenceLocation = jcPersistenceLocation,
                timeout = InstrumentationModuleConstants.testExecutionTimeout
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
