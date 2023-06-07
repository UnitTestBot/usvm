package org.usvm.instrumentation.agent

import org.usvm.instrumentation.classloader.BaseWorkerClassLoader
import org.usvm.instrumentation.jacodb.transform.JcInstrumenterFactory
import org.usvm.instrumentation.util.*
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

class ClassTransformer(instrumenterClassName: String) : ClassFileTransformer {

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
        if (loader !is BaseWorkerClassLoader || className == null) {
            return classfileBuffer
        }
        return instrumenterCache.getOrPut(className) {
            val instrumenter = instrumenterFactoryInstance.create(loader.jcClasspath)
            val instrumentedClassNode = instrumenter.instrumentClass(classfileBuffer.toClassNode())
            instrumentedClassNode.toByteArray(loader, checkClass = true)
        }
    }


}