package org.usvm.jvm.util

import org.jacodb.api.jvm.JcClassOrInterface

interface JcClassLoaderExt {
    fun loadClass(jcClass: JcClassOrInterface, initialize: Boolean = true): Class<*>
}
