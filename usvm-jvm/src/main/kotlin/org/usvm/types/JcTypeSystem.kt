package org.usvm.types

import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassOrInterface
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

object TypeScorer

class JcTypeSystem(
    private val cp: JcClasspath,
    override val typeOperationsTimeout: Duration,
) : UTypeSystem<JcType> {
    private val hierarchy = HierarchyExtensionImpl(cp)
    private val scorer = ScorerExtension<Double>(cp, TypeScorer)

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

    override fun isFinal(type: JcType): Boolean = when (type) {
        is JcPrimitiveType -> true
        is JcClassType -> type.isFinal
        is JcArrayType -> isFinal(type.elementType)
        else -> false
    }

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
        cp.objectType -> objectInheritors
        is JcArrayType -> findSubtypes(type.elementType).map { cp.arrayTypeOf(it) }
        is JcClassType -> cache.findSubClasses(type.jcClass).map { it.toType() } // TODO: filter bad classes
        is JcTypeVariable -> findSubtypes(type.jcClass.toType())
        else -> error("Unknown type $type")
    }

    private val objectInheritors by lazy {
        scorer.allClassesSorted
            .flatMap { jcClass ->
                val type = jcClass.toType()
                sequenceOf(type, cp.arrayTypeOf(type))
            } + cp.arrayTypeOf(cp.objectType)
    }

    private fun Sequence<JcClassOrInterface>.sortByScore(): Pair<Sequence<JcClassOrInterface>, Int> =
        mapTo(mutableListOf()) { jcClass ->
            // TODO: fix NEGATIVE_INFINITY (workaround for unknown types)
            val score = scorer.getScore(jcClass) ?: Double.NEGATIVE_INFINITY
            jcClass to score
        }.run {
            sortByDescending { it.second }
            asSequence().map { it.first } to size
        }

    // TODO: refactor this constant
    private val cache = SubClassesCache(200)

    private inner class SubClassesCache(
        private val sizeThreshold: Int,
    ) {
        private val cache = ConcurrentHashMap<JcClassOrInterface, Sequence<JcClassOrInterface>>()
        fun findSubClasses(jcClass: JcClassOrInterface): Sequence<JcClassOrInterface> =
            cache.getOrElse(jcClass) {
                val (sequence, size) = hierarchy
                    .findSubClasses(jcClass, allHierarchy = false, includeOwn = false)
                    .sortByScore()
                if (size >= sizeThreshold) {
                    cache[jcClass] = sequence
                }
                sequence
            }
    }

    private val topTypeStream by lazy { USupportTypeStream.from(this, cp.objectType) }

    override fun topTypeStream(): UTypeStream<JcType> =
        topTypeStream
}
