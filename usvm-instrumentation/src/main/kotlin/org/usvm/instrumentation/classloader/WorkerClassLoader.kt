package org.usvm.instrumentation.classloader

import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.findClass
import org.usvm.instrumentation.testcase.descriptor.StaticDescriptorsBuilder
import org.usvm.instrumentation.util.URLClassPathLoader
import java.security.CodeSource
import java.security.SecureClassLoader

/**
 * Worker classloader using as classloader in testing project
 */
class WorkerClassLoader(
    private val urlClassPath: URLClassPathLoader,
    private val traceCollectorClassLoader: ClassLoader,
    private val traceCollectorClassName: String,
    val jcClasspath: JcClasspath
) : SecureClassLoader(null) {

    //Loaded classes cache
    private val foundClasses = LinkedHashMap<String, Class<*>>()
    //Using for static descriptor building after class initialization
    private var staticDescriptorsBuilder: StaticDescriptorsBuilder? = null

    fun setStaticDescriptorsBuilder(builder: StaticDescriptorsBuilder) {
        this.staticDescriptorsBuilder = builder
    }

    //Invoking clinit method for loaded classes for statics reset between executions
    fun reset() {
        foundClasses.forEach { (_, cl) -> cl.declaredMethods.find { it.name == "generatedClinit0" }?.invoke(null) }
    }

    override fun loadClass(name: String): Class<*> {
        if (name == traceCollectorClassName) return traceCollectorClassLoader.loadClass(name)
        return super.loadClass(name)
    }

    override fun findClass(name: String): Class<*> =
        foundClasses.getOrPut(name) {
            val res = getWorkerResource(name)
            val bb = res.getBytes()
            val cs = CodeSource(res.getCodeSourceURL(), res.getCodeSigners())
            val foundClass = defineClass(name, bb, 0, bb.size, cs)
            val jcClass = jcClasspath.findClass(name)
            staticDescriptorsBuilder?.buildInitialDescriptorForClass(jcClass)
            foundClass
        }

    private fun getWorkerResource(name: String): URLClassPathLoader.Resource = cachedClasses.getOrPut(name) {
        val path = name.replace('.', '/') + ".class"
        val resource = urlClassPath.getResource(path)
        WorkerResource(resource)
    }

    companion object {
        private val cachedClasses = hashMapOf<String, URLClassPathLoader.Resource>()
    }

    private class WorkerResource(val resource: URLClassPathLoader.Resource) : URLClassPathLoader.Resource by resource {

        private val cachedBytes by lazy { resource.getBytes() }
        override fun getBytes(): ByteArray = cachedBytes
    }

}