package org.usvm.machine

import org.jacodb.api.JcClassType
import org.jacodb.api.JcType
import org.jacodb.api.ext.isAssignable
import org.usvm.UTypeSystem

class JcTypeSystem : UTypeSystem<JcType> {
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