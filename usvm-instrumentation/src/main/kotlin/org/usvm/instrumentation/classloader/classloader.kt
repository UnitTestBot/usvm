package org.usvm.instrumentation.classloader

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.findClass
import org.usvm.instrumentation.jacodb.util.isAllStaticsAreEasyToRollback
import org.usvm.instrumentation.util.URLClassPathLoader
import java.security.CodeSource
import java.security.SecureClassLoader
import java.util.HashSet

abstract class BaseWorkerClassLoader(
    private val urlClassPath: URLClassPathLoader,
    private val traceCollectorClassLoader: ClassLoader,
    private val traceCollectorClassName: String,
    val jcClasspath: JcClasspath,
    parent: ClassLoader?
) : SecureClassLoader(parent) {

    private val foundClasses = HashMap<String, Class<*>>()

    abstract fun isMatchClassLoader(name: String): Boolean

    override fun loadClass(name: String): Class<*> {
        if (name == traceCollectorClassName) return traceCollectorClassLoader.loadClass(name)
        return super.loadClass(name)
    }

    override fun findClass(name: String): Class<*>? = foundClasses.getOrPut(name) {
        if (!isMatchClassLoader(name)) return null
        val res = getWorkerResource(name)
        val bb = res.getBytes()
        val cs = CodeSource(res.getCodeSourceURL(), res.getCodeSigners())
        defineClass(name, bb, 0, bb.size, cs)
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

class UserClassLoaderWithoutStatics(
    urlClassPath: URLClassPathLoader,
    traceCollectorClassLoader: ClassLoader,
    traceCollectorClassName: String,
     jcClasspath: JcClasspath
) : BaseWorkerClassLoader(
    urlClassPath = urlClassPath,
    traceCollectorClassLoader = traceCollectorClassLoader,
    traceCollectorClassName = traceCollectorClassName,
    jcClasspath,
    parent = null
) {


    override fun isMatchClassLoader(name: String): Boolean = matchedClasses.getOrPut(name) {
        val jcClass = jcClasspath.findClass(name)
        jcClass.declaredFields.all { !it.isStatic }
    }


    companion object {
        private val matchedClasses = hashMapOf<String, Boolean>()
    }
}

class UserClassLoaderWithPrimitiveStatics(
    urlClassPath: URLClassPathLoader,
    traceCollectorClassLoader: ClassLoader,
    traceCollectorClassName: String,
    parent: UserClassLoaderWithoutStatics?,
    jcClasspath: JcClasspath,
    private val loadedStatics: HashSet<JcClassOrInterface>
) : BaseWorkerClassLoader(
    urlClassPath = urlClassPath,
    traceCollectorClassLoader = traceCollectorClassLoader,
    traceCollectorClassName = traceCollectorClassName,
    jcClasspath,
    parent = parent
) {


    override fun isMatchClassLoader(name: String): Boolean = matchedClasses.getOrPut(name) {
        val jcClass = jcClasspath.findClass(name)
        loadedStatics.add(jcClass)
        jcClass.isAllStaticsAreEasyToRollback()
    }


    companion object {
        private val matchedClasses = hashMapOf<String, Boolean>()
    }
}


class UserClassLoaderWithObjectStatics(
    urlClassPath: URLClassPathLoader,
    traceCollectorClassLoader: ClassLoader,
    traceCollectorClassName: String,
    parent: UserClassLoaderWithPrimitiveStatics?,
    jcClasspath: JcClasspath,
    private val loadedStatics: HashSet<JcClassOrInterface>
) : BaseWorkerClassLoader(
    urlClassPath = urlClassPath,
    traceCollectorClassLoader = traceCollectorClassLoader,
    traceCollectorClassName = traceCollectorClassName,
    jcClasspath,
    parent = parent
) {
    override fun isMatchClassLoader(name: String): Boolean = matchedClasses.getOrPut(name) {
        val jcClass = jcClasspath.findClass(name)
        loadedStatics.add(jcClass)
        true
    }

    companion object {
        private val matchedClasses = hashMapOf<String, Boolean>()
    }
}

class HierarchicalWorkerClassLoader(
    private val urlClassPath: URLClassPathLoader,
    private val traceCollectorClassLoader: ClassLoader,
    private val traceCollectorClassName: String,
    private val jcClasspath: JcClasspath
) {

    val loadedClassesWithStatics = hashSetOf<JcClassOrInterface>()

    private val userClassLoaderWithoutStatics = UserClassLoaderWithoutStatics(
        urlClassPath = urlClassPath,
        traceCollectorClassLoader = traceCollectorClassLoader,
        traceCollectorClassName = traceCollectorClassName,
        jcClasspath = jcClasspath
    )

    private val userClassLoaderWithPrimitiveStatics = UserClassLoaderWithPrimitiveStatics(
        urlClassPath = urlClassPath,
        traceCollectorClassLoader = traceCollectorClassLoader,
        traceCollectorClassName = traceCollectorClassName,
        parent = userClassLoaderWithoutStatics,
        jcClasspath = jcClasspath,
        loadedClassesWithStatics
    )

    private var userClassLoaderWithObjectStatics = UserClassLoaderWithObjectStatics(
        urlClassPath = urlClassPath,
        traceCollectorClassLoader = traceCollectorClassLoader,
        traceCollectorClassName = traceCollectorClassName,
        parent = userClassLoaderWithPrimitiveStatics,
        jcClasspath = jcClasspath,
        loadedClassesWithStatics
    )

    fun reset() {
        userClassLoaderWithObjectStatics = UserClassLoaderWithObjectStatics(
            urlClassPath = urlClassPath,
            traceCollectorClassLoader = traceCollectorClassLoader,
            traceCollectorClassName = traceCollectorClassName,
            parent = userClassLoaderWithPrimitiveStatics,
            jcClasspath = jcClasspath,
            loadedClassesWithStatics
        )
    }

    fun getClassLoader(): BaseWorkerClassLoader = userClassLoaderWithObjectStatics

}
