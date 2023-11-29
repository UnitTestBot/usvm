package org.usvm.util

import org.jacodb.api.JcClasspath
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.instrumentation.JcRuntimeTraceInstrumenterFactory
import org.usvm.instrumentation.util.InstrumentationModuleConstants

object UTestRunner {

    lateinit var runner: UTestConcreteExecutor

    fun isInitialized() = this::runner.isInitialized

    fun initRunner(pathToJars: List<String>, classpath: JcClasspath) {
        runner = UTestConcreteExecutor(
            instrumentationClassFactory = JcRuntimeTraceInstrumenterFactory::class,
            testingProjectClasspath = pathToJars,
            jcClasspath = classpath,
            jcPersistenceLocation = null,
            timeout = InstrumentationModuleConstants.testExecutionTimeout
        )
    }

}