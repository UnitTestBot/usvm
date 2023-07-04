package org.usvm.machine

import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.ext.isAssignable
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.toType
import org.jacodb.impl.features.HierarchyExtensionImpl
import org.jacodb.impl.features.asyncHierarchy
import org.jacodb.impl.features.hierarchyExt
import org.usvm.types.USingleTypeStream
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

class JcTypeSystem(
    private val cp: JcClasspath
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

    override fun isInstantiable(t: JcType): Boolean {
        return t !is JcRefType || (!t.jcClass.isInterface && !t.jcClass.isAbstract)
    }

    // TODO: deal with generics
    override fun findSubtypes(t: JcType): Sequence<JcType> =
        hierarchy
            .findSubClasses((t as JcRefType).jcClass, allHierarchy = false)
            .map { it.toType()}

    override fun topTypeStream(): UTypeStream<JcType> =
        USupportTypeStream.from(this, cp.objectType)
}
