@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "DEPRECATION")
package org.usvm.instrumentation.util
import jdk.internal.loader.URLClassPath
import jdk.internal.loader.Resource as InternalResource

import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.AccessController
import java.security.CodeSigner

class URLClassPathLoader(classPath: List<File>) {

    interface Resource {
        fun getName(): String
        fun getURL(): URL
        fun getCodeSourceURL(): URL
        fun getInputStream(): InputStream
        fun getContentLength(): Int
        fun getBytes(): ByteArray
        fun getCodeSigners(): Array<CodeSigner>?
    }

    private class InternalResourceWrapper(val resource: InternalResource): Resource {
        override fun getName(): String = resource.name
        override fun getURL(): URL = resource.url
        override fun getCodeSourceURL(): URL = resource.codeSourceURL
        override fun getInputStream(): InputStream = resource.inputStream
        override fun getContentLength(): Int = resource.contentLength
        override fun getBytes(): ByteArray = resource.bytes
        override fun getCodeSigners(): Array<CodeSigner>? = resource.codeSigners
    }

    private val urlClassPath = URLClassPath(classPath.map { it.toURI().toURL() }.toTypedArray(), AccessController.getContext())

    fun getResource(name: String): Resource? =
        urlClassPath.getResource(name, false)?.let { InternalResourceWrapper(it) }

    fun getResources(name: String): Sequence<Resource> =
        urlClassPath.getResources(name, false).asSequence().map { InternalResourceWrapper(it) }
}
