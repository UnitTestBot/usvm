package org.usvm

import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.isAssignable
import org.jacodb.api.ext.toType
import org.jacodb.impl.features.HierarchyExtensionImpl

class JcTypeSystem(
    val cp: JcClasspath
) : UTypeSystem<JcType> {
    private val hierarchy = HierarchyExtensionImpl(cp)

    override fun isSupertype(u: JcType, t: JcType): Boolean {
        return t.isAssignable(u)
    }

    override fun isMultipleInheritanceAllowedFor(t: JcType): Boolean {
        return (t as? JcClassType)?.jcClass?.isInterface ?: false
    }

    override fun isFinal(t: JcType): Boolean {
        return (t as? JcClassType)?.isFinal ?: false
    }

    private val topTypeStream by lazy {
        val jcObject = cp.findClass<Any>().toType()
        JcTypeStream.from(this, jcObject)
    }

    override fun topTypeStream(): JcTypeStream {
        return topTypeStream
    }

    fun findSubTypes(t: JcType): Sequence<JcType> {
        require(t is JcRefType)
        val jcClass = t.jcClass
        // TODO: deal with generics here
        return hierarchy.findSubClasses(jcClass, allHierarchy = false).map { it.toType() }
    }

    fun isInstantiable(t: JcType): Boolean {
        if (t !is JcRefType) {
            return true
        }
        return !t.jcClass.isInterface && !t.jcClass.isAbstract
    }
}