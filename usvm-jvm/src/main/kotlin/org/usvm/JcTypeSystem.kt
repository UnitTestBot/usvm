package org.usvm

import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcType
import org.jacodb.api.ext.isAssignable
import org.jacodb.impl.features.HierarchyExtensionImpl

class JcTypeSystem(
    val cp: JcClasspath
) : UTypeSystem<JcType> {
    override fun isSupertype(u: JcType, t: JcType): Boolean {
        return t.isAssignable(u)
    }

    override fun isMultipleInheritanceAllowedFor(t: JcType): Boolean {
        return (t as? JcClassType)?.jcClass?.isInterface ?: false
    }

    override fun isFinal(t: JcType): Boolean {
        return (t as? JcClassType)?.isFinal ?: false
    }
}