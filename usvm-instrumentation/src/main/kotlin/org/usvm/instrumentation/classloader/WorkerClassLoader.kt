package org.usvm.instrumentation.classloader

import isFinal
import isStatic
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.ext.findClass
import org.objectweb.asm.tree.ClassNode
import org.usvm.instrumentation.testcase.descriptor.StaticDescriptorsBuilder
import org.usvm.instrumentation.util.URLClassPathLoader
import org.usvm.instrumentation.util.toByteArray
import setFieldValue
import java.io.File
import java.lang.instrument.ClassDefinition
import java.lang.instrument.Instrumentation
import java.security.CodeSource
import java.security.SecureClassLoader

/**
 * Worker classloader using as classloader in testing project
 */
class WorkerClassLoader(
    private val urlClassPath: URLClassPathLoader,
    private val traceCollectorClassLoader: ClassLoader,
    private val traceCollectorClassName: String,
    private val mockCollectorClassName: String,
    val jcClasspath: JcClasspath
) : SecureClassLoader(null) {

    private lateinit var instrumentation: Instrumentation

    fun regInstrumentation(instrumentation: Instrumentation) {
        this.instrumentation = instrumentation
    }

    //Loaded classes cache
    private val foundClasses = LinkedHashMap<String, Pair<Class<*>, JcClassOrInterface>>()

    //Using for static descriptor building after class initialization
    private var staticDescriptorsBuilder: StaticDescriptorsBuilder? = null

    fun setStaticDescriptorsBuilder(builder: StaticDescriptorsBuilder) {
        this.staticDescriptorsBuilder = builder
    }

    //Invoking clinit method for loaded classes for statics reset between executions
    fun reset(accessedStatic: List<JcField>) {
        val jcClassesToReinit = accessedStatic.map { it.enclosingClass }.toSet()
        val classesToReinit = foundClasses.values
            .filter { it.second in jcClassesToReinit }
            .map { it.first }

        //TODO do it in bytecode
        //To avoid cyclic references first reset all static fields to default values, then we need to call <clinit>
        classesToReinit.forEach { cl ->
            cl.declaredFields
                .filter {
                    it.isStatic() && ((it.isFinal && !it.type.isPrimitive) || !it.isFinal)
                }
                .forEach {
                    it.setFieldValue(null, null)
                }
        }
        classesToReinit.forEach { cl ->
            cl.declaredMethods.find { it.name == "generatedClinit0" }?.invoke(null)
        }
    }

    fun redefineClass(jClass: Class<*>, asmBody: ClassNode) {
        val classDefinition = ClassDefinition(jClass, asmBody.toByteArray(this))
        instrumentation.redefineClasses(classDefinition)
    }

    override fun loadClass(name: String): Class<*> {
        if (name == traceCollectorClassName) return traceCollectorClassLoader.loadClass(name)
        if (name == mockCollectorClassName) return traceCollectorClassLoader.loadClass(name)
        return super.loadClass(name)
    }

    fun defineClass(name: String, classNode: ClassNode): Class<*>? {
        val classByteArray = classNode.toByteArray(this)
        File("/tmp/mock.class").writeBytes(classByteArray)
        return defineClass(name, classByteArray, 0, classByteArray.size)
    }

    override fun findClass(name: String): Class<*> {
        return foundClasses.getOrPut(name) {
            val res = getWorkerResource(name)
            val bb = res.getBytes()
            val cs = CodeSource(res.getCodeSourceURL(), res.getCodeSigners())
            val foundClass = defineClass(name, bb, 0, bb.size, cs)
            val jcClass = jcClasspath.findClass(name)
            staticDescriptorsBuilder?.buildInitialDescriptorForClass(jcClass)
            foundClass to jcClass
        }.first
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