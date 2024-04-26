package org.usvm.util

import org.jacodb.api.jvm.JcClasspath
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.instrumentation.JcRuntimeTraceInstrumenterFactory
import org.usvm.instrumentation.util.InstrumentationModuleConstants

object UTestRunner {

    lateinit var runner: UTestConcreteExecutor

    fun isInitialized() = this::runner.isInitialized

    fun initRunner(pathToJars: List<String>, classpath: JcClasspath) {
        runner =
            UTestConcreteExecutor(
                JcRuntimeTraceInstrumenterFactory::class,
                pathToJars,
                classpath,
                InstrumentationModuleConstants.testExecutionTimeout
            )
    }

}