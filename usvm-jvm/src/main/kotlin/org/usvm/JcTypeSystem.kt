package org.usvm

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcType
import org.jacodb.api.ext.isAssignable

class JcTypeSystem(
    private val cp: JcClasspath
) : UTypeSystem<JcType> {
    override fun isSupertype(u: JcType, t: JcType): Boolean {
        return t.isAssignable(u)
    }

    override fun isMultipleInheritanceAllowedFor(t: JcType): Boolean {
        return (t as? JcClassOrInterface)?.isInterface ?: false
    }

    override fun isFinal(t: JcType): Boolean {
        return (t as? JcClassOrInterface)?.isFinal ?: false
    }
}