package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath
import org.objectweb.asm.tree.ClassNode

class NoInstrumentation(
    override val jcClasspath: JcClasspath
) : JcInstrumenter {
    override fun instrumentClass(classNode: ClassNode): ClassNode {
        return classNode
    }
}