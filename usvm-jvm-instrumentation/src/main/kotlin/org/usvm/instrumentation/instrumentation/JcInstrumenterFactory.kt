package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath

interface JcInstrumenterFactory<T: JcInstrumenter> {
    fun create(jcClasspath: JcClasspath): T
}

class JcRuntimeTraceInstrumenterFactory : JcInstrumenterFactory<JcRuntimeTraceInstrumenter> {
    override fun create(jcClasspath: JcClasspath): JcRuntimeTraceInstrumenter = JcRuntimeTraceInstrumenter(jcClasspath)
}

class JcExtendedRuntimeTraceInstrumenterFactory : JcInstrumenterFactory<JcRuntimeTraceInstrumenter> {
    override fun create(jcClasspath: JcClasspath): JcRuntimeTraceInstrumenter = JcRuntimeTraceInstrumenter(jcClasspath, true)
}

class NoInstrumentationFactory : JcInstrumenterFactory<NoInstrumentation> {
    override fun create(jcClasspath: JcClasspath): NoInstrumentation = NoInstrumentation(jcClasspath)
}