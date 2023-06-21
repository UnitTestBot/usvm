package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.objectweb.asm.tree.ClassNode
import org.usvm.instrumentation.trace.collector.TraceCollector

interface JcInstrumenter {

    val jcClasspath: JcClasspath

    fun instrumentClass(classNode: ClassNode): ClassNode
}