package org.usvm.instrumentation.agent

import org.objectweb.asm.Opcodes
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.instrumentation.JcInstrumenterFactory
import org.usvm.jvm.util.toByteArray
import org.usvm.jvm.util.toClassNode
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

class ClassTransformer(
    instrumenterFactoryClassName: String,
    val instrumentation: Instrumentation
) : ClassFileTransformer {

    private val instrumenterFactoryInstance =
        Class.forName(instrumenterFactoryClassName).constructors.first().newInstance() as JcInstrumenterFactory<*>
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
        if (!loader.shouldInstrumentCurrentClass) return classfileBuffer
        return instrumenterCache.getOrPut(className) {
            val instrumenter = instrumenterFactoryInstance.create(loader.jcClasspath)

            // JacoDB may produce incorrect IR/bytecode for earlier Java versions
            // see https://github.com/UnitTestBot/usvm/issues/179
            val classNode = classfileBuffer.toClassNode()
            if (classNode.version < Opcodes.V1_8)
                return classfileBuffer

            val instrumentedClassNode = instrumenter.instrumentClass(classfileBuffer.toClassNode())
            instrumentedClassNode.toByteArray(loader.jcClasspath, checkClass = true)
        }
    }

}