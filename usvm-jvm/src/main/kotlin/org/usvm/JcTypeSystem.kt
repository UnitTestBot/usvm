package org.usvm

import org.jacodb.api.JcClassType
import org.jacodb.api.JcType
import org.jacodb.api.ext.isAssignable

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

    override fun topTypeStream(): JcTypeStream {
        return JcTypeStream(this)
    }
}