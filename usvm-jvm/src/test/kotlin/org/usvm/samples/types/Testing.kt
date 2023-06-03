package org.usvm.samples.types

import org.jacodb.api.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.jacodb

suspend fun main() {
    val db = jacodb {
        useProcessJavaRuntime()
        installFeatures(InMemoryHierarchy)
    }
    val cp = db.classpath(emptyList())

    val hierarchy = cp.hierarchyExt()

    val subClassesComparable = hierarchy.findSubClasses(cp.findClass<Comparable<*>>(), allHierarchy = false).count()
    val subClassesAny = hierarchy.findSubClasses(cp.findClass<Any>(), allHierarchy = false).count()

    check(subClassesComparable > 0)
    check(subClassesAny > 0)
}