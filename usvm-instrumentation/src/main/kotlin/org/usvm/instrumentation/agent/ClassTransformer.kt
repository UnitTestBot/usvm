package org.usvm.instrumentation.agent

import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.jacodb.transform.JcInstrumenter
import org.usvm.instrumentation.jacodb.transform.JcInstrumenterFactory
import org.usvm.instrumentation.jacodb.transform.JcRuntimeTraceInstrumenter
import org.usvm.instrumentation.trace.collector.TraceCollector
import org.usvm.instrumentation.util.*
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

class ClassTransformer(instrumenterClassName: String) : ClassFileTransformer {

    private val instrumenterFactoryInstance =
        Class.forName(instrumenterClassName).constructors.first().newInstance() as JcInstrumenterFactory<*>

    override fun transform(
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray
    ): ByteArray {
        if (loader !is WorkerClassLoader) {
            return super.transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer)
        }
        val instrumenter = instrumenterFactoryInstance.create(loader.jcClasspath)
        return instrumenter.instrumentClass(classfileBuffer.toClassNode()).toByteArray(loader, checkClass = true)
    }


}