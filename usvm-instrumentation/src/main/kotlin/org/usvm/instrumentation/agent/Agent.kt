package org.usvm.instrumentation.agent

import org.usvm.instrumentation.jacodb.transform.JcRuntimeTraceInstrumenter
import org.usvm.instrumentation.jacodb.transform.JcRuntimeTraceInstrumenterFactory
import java.lang.instrument.Instrumentation

@Suppress("UNUSED")
class Agent {

    companion object {
        @JvmStatic
        fun premain(arguments: String?, instrumentation: Instrumentation) {
            val instrumenterClassname = arguments ?: JcRuntimeTraceInstrumenter::class.java.name
            instrumentation.addTransformer(ClassTransformer(instrumenterClassname))
        }
    }
}