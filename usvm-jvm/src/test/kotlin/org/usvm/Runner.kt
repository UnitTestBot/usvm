package org.usvm

import org.jacodb.analysis.impl.JcApplicationGraphImpl
import org.jacodb.api.ext.findClass
import org.jacodb.impl.features.HierarchyExtensionImpl
import org.jacodb.impl.features.SyncUsagesExtension
import org.jacodb.impl.jacodb
import org.usvm.samples.loops.WhileLoops


suspend fun main() {
    val classpath = allClasspath.filter { it.name.contains("samples") }

    val db = jacodb {
        useProcessJavaRuntime()
        loadByteCode(classpath)
    }
    val cp = db.classpath(classpath)
    val classec = cp.findClass<WhileLoops>()

    printAllMethods(classec)


    @Suppress("UNUSED_VARIABLE")
    val appGraph = JcApplicationGraphImpl(cp, SyncUsagesExtension(HierarchyExtensionImpl(cp), cp))


    return
}