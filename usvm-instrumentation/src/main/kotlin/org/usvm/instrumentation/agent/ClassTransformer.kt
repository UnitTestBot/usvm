package org.usvm.instrumentation.agent

import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.instrumentation.JcInstrumenterFactory
import org.usvm.instrumentation.util.toByteArray
import org.usvm.instrumentation.util.toClassNode
import java.io.File
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

class ClassTransformer(
    instrumenterClassName: String,
    val instrumentation: Instrumentation
) : ClassFileTransformer {

    private val instrumenterFactoryInstance =
        Class.forName(instrumenterClassName).constructors.first().newInstance() as JcInstrumenterFactory<*>
    private val instrumenterCache = HashMap<String, ByteArray>()


    override fun transform(
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray
    ): ByteArray {
        if (loader !is WorkerClassLoader || className == null) {
            return classfileBuffer
        }
        loader.regInstrumentation(instrumentation)
        return classfileBuffer
        return instrumenterCache.getOrPut(className) {
            val instrumenter = instrumenterFactoryInstance.create(loader.jcClasspath)
            val instrumentedClassNode = instrumenter.instrumentClass(classfileBuffer.toClassNode())
            instrumentedClassNode.toByteArray(loader, checkClass = true)
        }
    }

}