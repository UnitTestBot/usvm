package org.usvm.instrumentation.agent

import org.usvm.instrumentation.instrumentation.JcRuntimeTraceInstrumenter
import java.lang.instrument.Instrumentation

@Suppress("UNUSED")
class Agent {
    companion object {
        @JvmStatic
        fun premain(arguments: String?, instrumentation: Instrumentation) {
            val instrumenterClassname = arguments ?: JcRuntimeTraceInstrumenter::class.java.name
            val transformer = ClassTransformer(instrumenterClassname, instrumentation)
            instrumentation.addTransformer(transformer)
        }
    }
}