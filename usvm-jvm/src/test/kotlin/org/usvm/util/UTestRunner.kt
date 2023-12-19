package org.usvm.util

import org.jacodb.api.JcClasspath
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.instrumentation.JcRuntimeTraceInstrumenterFactory
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.executor.InstrumentationProcessPaths

object UTestRunner {

    lateinit var runner: UTestConcreteExecutor

    fun isInitialized() = this::runner.isInitialized

    fun initRunner(pathToJars: List<String>, classpath: JcClasspath) {
        runner = UTestConcreteExecutor(
            instrumentationClassFactory = JcRuntimeTraceInstrumenterFactory::class,
            testingProjectClasspath = pathToJars,
            jcClasspath = classpath,
            instrumentationProcessPaths = InstrumentationProcessPaths(),
            jcPersistenceLocation = null,
            timeout = InstrumentationModuleConstants.testExecutionTimeout
        )
    }

}