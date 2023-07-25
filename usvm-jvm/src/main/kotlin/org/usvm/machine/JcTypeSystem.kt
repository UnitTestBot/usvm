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

    override fun isSupertype(supertype: JcType, type: JcType): Boolean =
        type.isAssignable(supertype)

    override fun isMultipleInheritanceAllowedFor(type: JcType): Boolean =
        (type as? JcClassType)?.jcClass?.isInterface ?: false

    override fun isFinal(type: JcType): Boolean =
        (type as? JcClassType)?.isFinal ?: false

    override fun isInstantiable(type: JcType): Boolean =
        type !is JcRefType || (!type.jcClass.isInterface && !type.jcClass.isAbstract)

    // TODO: deal with generics
    // TODO: handle object type, serializable and cloneable
    override fun findSubtypes(type: JcType): Sequence<JcType> = when (type) {
        is JcPrimitiveType -> emptySequence() // TODO: should not be called here
        is JcArrayType -> findSubtypes(type.elementType).map { cp.arrayTypeOf(it) }
        is JcRefType -> hierarchy
            .findSubClasses(
                type.jcClass,
                allHierarchy = false
            ) // TODO: prioritize classes somehow and filter bad classes
            .map { it.toType() }
            .run {
                if (type == cp.objectType) {
                    // since we use DFS iterator, the array of objects should come last
                    // here we return only the direct successors, so (2,3,...)-dimensional arrays isn't returned here
                    // such arrays are subtypes of `Object[]`
                    flatMap { listOf(it, cp.arrayTypeOf(it)) } + sequenceOf(cp.arrayTypeOf(type))
                } else {
                    this
                }
            }

        else -> error("Unknown type $type")
    }

    private val topTypeStream by lazy { USupportTypeStream.from(this, cp.objectType) }

    override fun topTypeStream(): UTypeStream<JcType> =
        topTypeStream
}
