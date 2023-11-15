package org.usvm.api.util

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcRefType
import org.jacodb.api.ext.allSuperHierarchySequence
import org.jacodb.api.ext.toType
import java.nio.ByteBuffer
import java.security.CodeSource
import java.security.SecureClassLoader

/**
 * Loads known classes using [ClassLoader.getSystemClassLoader], or defines them using bytecode from jacodb if they are unknown.
 */
object JcClassLoader : SecureClassLoader(ClassLoader.getSystemClassLoader()) {
    fun loadClass(jcClass: JcClassOrInterface): Class<*> = defineClassRecursively(jcClass)

    private fun defineClass(name: String, code: ByteArray): Class<*> {
        return defineClass(name, ByteBuffer.wrap(code), null as CodeSource?)
    }

    private fun defineClassRecursively(jcClass: JcClassOrInterface): Class<*> =
        defineClassRecursively(jcClass, hashSetOf())

    private fun defineClassRecursively(
        jcClass: JcClassOrInterface,
        visited: MutableSet<JcClassOrInterface>
    ): Class<*> {
        visited += jcClass

        return try {
            // We cannot redefine a class that was already defined
            loadClass(jcClass.name)
        } catch (e: ClassNotFoundException) {
            with(jcClass) {
                // For unknown class we need to load all its supers, all classes mentioned in its ALL (not only declared)
                // fields (as they are used in resolving), and then define the class itself using its bytecode from jacodb

                val notVisitedSupers = allSuperHierarchySequence.filterNot { it in visited }
                notVisitedSupers.forEach { defineClassRecursively(it, visited) }

                val classType = toType()
                val notVisitedRefFieldTypes = classType
                    .fields
                    .asSequence()
                    .map { it.fieldType }
                    .filterIsInstance<JcRefType>()
                    .map { it.jcClass }
                    .filterNot { it in visited }

                notVisitedRefFieldTypes.forEach { defineClassRecursively(it, visited) }

                return defineClass(name, bytecode())
            }
        }
    }
}

