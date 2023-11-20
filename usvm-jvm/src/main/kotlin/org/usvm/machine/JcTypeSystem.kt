package org.usvm.machine

import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypeVariable
import org.jacodb.api.ext.isAssignable
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.toType
import org.jacodb.impl.features.HierarchyExtensionImpl
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class JcTypeSystem(
    private val cp: JcClasspath,
    override val typeOperationsTimeout: Duration
) : UTypeSystem<JcType> {
    private val hierarchy = HierarchyExtensionImpl(cp)

    override fun isSupertype(supertype: JcType, type: JcType): Boolean =
        when {
            supertype == type -> true
            supertype is JcTypeVariable ->
                isSupertype(cp.objectType, type) && supertype.bounds.all { isSupertype(it, type) }

            type is JcTypeVariable -> supertype == cp.objectType || type.bounds.any { isSupertype(supertype, it) }
            else -> type.isAssignable(supertype)
        }


    private fun isInterface(type: JcType): Boolean =
        (type as? JcClassType)?.jcClass?.isInterface ?: false

    override fun hasCommonSubtype(type: JcType, types: Collection<JcType>): Boolean {
        when {
            type is JcPrimitiveType -> {
                return types.isEmpty()
            }

            isInterface(type) -> {
                return types.none { it is JcArrayType || it is JcPrimitiveType }
            }

            type is JcClassType -> {
                return types.all {
                    // It is guaranteed that it </: [type]
                    isInterface(it) || isSupertype(it, type)
                }
            }

            type is JcArrayType -> {
                val elementTypes = types.mapNotNull {
                    when {
                        it is JcArrayType -> it.elementType
                        it == cp.objectType -> null
                        else -> return false
                    }
                }
                return hasCommonSubtype(type.elementType, elementTypes)
            }

            type is JcTypeVariable -> {
                val bounds = type.bounds
                return if (bounds.isEmpty()) {
                    types.none { it is JcPrimitiveType }
                } else {
                    bounds.all { hasCommonSubtype(it, types) }
                }
            }

            else -> error("Unexpected type: $type")
        }
    }

    override fun isFinal(type: JcType): Boolean =
        (type as? JcClassType)?.isFinal ?: false

    override fun isInstantiable(type: JcType): Boolean =
        when (type) {
            is JcPrimitiveType -> true

            is JcRefType -> when (type) {
                is JcArrayType -> isInstantiable(type.elementType)
                is JcClassType -> !type.jcClass.isInterface && !type.jcClass.isAbstract
                else -> false
            }

            else -> error("Unknown type $type")
        }

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
                    this + map { cp.arrayTypeOf(it) } + cp.arrayTypeOf(type)
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
