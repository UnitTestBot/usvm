package org.usvm.instrumentation.agent

import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.jacodb.impl.fs.ClassSourceImpl
import org.objectweb.asm.tree.ClassNode
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.jacodb.transform.RuntimeTraceInstrumenter
import org.usvm.instrumentation.trace.collector.TraceCollector
import org.usvm.instrumentation.util.*
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

class ClassTransformer : ClassFileTransformer {

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
        val instrumenter = RuntimeTraceInstrumenter(loader.jcClasspath, TraceCollector())
        return instrumenter.instrumentClass(classfileBuffer.toClassNode()).toByteArray(loader, checkClass = true)
    }


}