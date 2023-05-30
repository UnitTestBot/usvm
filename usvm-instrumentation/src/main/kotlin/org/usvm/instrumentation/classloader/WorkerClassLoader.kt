package org.usvm.instrumentation.classloader

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.usvm.instrumentation.util.URLClassPathLoader
import org.usvm.instrumentation.util.URLClassPathLoader.Resource
import java.security.CodeSource
import java.security.SecureClassLoader

class WorkerClassLoader(
    private val urlClassPath: URLClassPathLoader,
    private val traceCollectorClassLoader: ClassLoader,
    private val traceCollectorClassName: String,
    val jcClasspath: JcClasspath,
) : SecureClassLoader(null) {

    val loadedClasses = HashSet<JcClassOrInterface>()
    private val foundClasses = HashMap<String, Class<*>>()

    override fun loadClass(name: String?): Class<*> {
        if (name == traceCollectorClassName) return traceCollectorClassLoader.loadClass(name)
        val loadedClass = super.loadClass(name)
        if (loadedClass.classLoader === this && name != null) jcClasspath.findClassOrNull(name)
            ?.let { loadedClasses.add(it) }
        return loadedClass
    }

    override fun findClass(name: String): Class<*> =
        foundClasses.getOrPut(name) {
            val res = getWorkerResource(name)
            val bb = res.getBytes()
            val cs = CodeSource(res.getCodeSourceURL(), res.getCodeSigners())
            defineClass(name, bb, 0, bb.size, cs)
        }

    private fun getWorkerResource(name: String): Resource =
        cachedClasses.getOrPut(name) {
            val path = name.replace('.', '/') + ".class"
            val resource = urlClassPath.getResource(path)
            WorkerResource(resource)
        }


    companion object {
        val cachedClasses = hashMapOf<String, Resource>()
    }

    private class WorkerResource(val resource: Resource) : Resource by resource {

        private val cachedBytes by lazy { resource.getBytes() }
        override fun getBytes(): ByteArray = cachedBytes
    }

}