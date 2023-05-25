package org.usvm.instrumentation.agent

import java.lang.instrument.Instrumentation

@Suppress("UNUSED")
class Agent {

    companion object {
        @JvmStatic
        fun premain(arguments: String?, instrumentation: Instrumentation) {
            instrumentation.addTransformer(ClassTransformer())
        }
    }
}