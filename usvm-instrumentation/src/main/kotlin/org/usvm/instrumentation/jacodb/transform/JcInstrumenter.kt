package org.usvm.instrumentation.jacodb.transform

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.objectweb.asm.tree.ClassNode
import org.usvm.instrumentation.trace.collector.TraceCollector

interface JcInstrumenter {

    val jcClasspath: JcClasspath
    val traceCollector: TraceCollector

    fun instrumentClass(classNode: ClassNode): ClassNode
}