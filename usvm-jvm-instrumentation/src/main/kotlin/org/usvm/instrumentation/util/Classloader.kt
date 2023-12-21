@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.usvm.instrumentation.util

import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.security.CodeSigner

class URLClassPathLoader(private val classPath: List<File>) {

    interface Resource {
        fun getName(): String
        fun getURL(): URL
        fun getCodeSourceURL(): URL
        fun getBytes(): ByteArray
        fun getCodeSigners(): Array<CodeSigner>?
    }

    private val urlClassLoader = URLClassLoader(classPath.map { it.toURI().toURL() }.toTypedArray(), null)

    fun getResource(name: String): Resource? {
        val resourceUrl = urlClassLoader.getResource(name) ?: return null
        return object : Resource {
            override fun getName(): String = name
            override fun getURL(): URL = resourceUrl

            // TODO usvm-sbft-merge: may be incorrect, especially for non-ASCII URLs
            override fun getCodeSourceURL(): URL = resourceUrl
            override fun getBytes(): ByteArray = resourceUrl.readBytes()

            // TODO usvm-sbft-merge: figure out the way to get code signers
            override fun getCodeSigners(): Array<CodeSigner>? = null
        }
    }

}