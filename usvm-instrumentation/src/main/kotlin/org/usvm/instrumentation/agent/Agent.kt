package org.usvm.instrumentation.agent

import org.usvm.instrumentation.instrumentation.JcRuntimeTraceInstrumenter
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import java.io.File
import java.lang.instrument.Instrumentation
import java.util.jar.JarFile

@Suppress("UNUSED")
class Agent {
    companion object {
        @JvmStatic
        fun premain(arguments: String?, instrumentation: Instrumentation) {
            val collectorsJarPath = InstrumentationModuleConstants.pathToUsvmCollectorsJar
            val collectorsJar = JarFile(File(collectorsJarPath))
            instrumentation.appendToBootstrapClassLoaderSearch(collectorsJar)
            val instrumenterClassname = arguments ?: JcRuntimeTraceInstrumenter::class.java.name
            val transformer = ClassTransformer(instrumenterClassname, instrumentation)
            instrumentation.addTransformer(transformer)
        }
    }
}