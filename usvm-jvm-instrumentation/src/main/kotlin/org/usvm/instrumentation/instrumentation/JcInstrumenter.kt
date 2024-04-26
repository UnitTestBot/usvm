package org.usvm.instrumentation.instrumentation

import org.jacodb.api.jvm.JcClasspath
import org.objectweb.asm.tree.ClassNode

interface JcInstrumenter {

    val jcClasspath: JcClasspath

    fun instrumentClass(classNode: ClassNode): ClassNode
}