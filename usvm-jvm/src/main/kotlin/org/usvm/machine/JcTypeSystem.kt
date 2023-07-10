package org.usvm.machine

import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.ext.isAssignable
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.toType
import org.jacodb.impl.features.HierarchyExtensionImpl
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

class JcTypeSystem(
    private val cp: JcClasspath,
) : UTypeSystem<JcType> {
    private val hierarchy = HierarchyExtensionImpl(cp)

    override fun isSupertype(u: JcType, t: JcType): Boolean =
        t.isAssignable(u)

    override fun isMultipleInheritanceAllowedFor(t: JcType): Boolean =
        (t as? JcClassType)?.jcClass?.isInterface ?: false

    override fun isFinal(t: JcType): Boolean =
        (t as? JcClassType)?.isFinal ?: false

    override fun isInstantiable(t: JcType): Boolean =
        t !is JcRefType || (!t.jcClass.isInterface && !t.jcClass.isAbstract)

    // TODO: deal with generics
    // TODO: handle object type, serializable and cloneable
    override fun findSubtypes(t: JcType): Sequence<JcType> = when (t) {
        is JcPrimitiveType -> emptySequence() // TODO: should not be called here
        is JcArrayType -> findSubtypes(t.elementType).map { cp.arrayTypeOf(it) }
        is JcRefType -> hierarchy
            .findSubClasses(t.jcClass, allHierarchy = false) // TODO: prioritize classes somehow and filter bad classes
            .map { it.toType() }

        else -> error("Unknown type $t")
    }

    override fun topTypeStream(): UTypeStream<JcType> =
        USupportTypeStream.from(this, cp.objectType)
}
