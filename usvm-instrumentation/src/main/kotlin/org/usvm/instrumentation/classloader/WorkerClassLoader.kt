package org.usvm.instrumentation.classloader

import org.jacodb.api.JcClasspath
import java.net.URL
import java.net.URLClassLoader

class WorkerClassLoader(
    classpath: Array<URL>,
    private val traceCollectorClassLoader: ClassLoader,
    private val traceCollectorClassName: String,
    val jcClasspath: JcClasspath,
): URLClassLoader(classpath, null) {

    override fun loadClass(name: String?): Class<*> {
        if (name == traceCollectorClassName) return traceCollectorClassLoader.loadClass(name)
        return super.loadClass(name)
    }
}