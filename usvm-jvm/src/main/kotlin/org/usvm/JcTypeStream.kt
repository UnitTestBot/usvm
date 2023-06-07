package org.usvm

import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.usvm.constraints.UEmptyTypeStream
import org.usvm.constraints.USingleTypeStream
import org.usvm.constraints.UTypeStream
import org.usvm.util.CachingSequence
import org.usvm.util.DfsIterator

class JcTypeStream private constructor(
    private val typeSystem: JcTypeSystem,
    private val cachingSequence: CachingSequence<JcType>,
    private val supportType: JcType,
    private val filtering: (JcType) -> Boolean,
) : UTypeStream<JcType> {
    override fun filterBySupertype(type: JcType): UTypeStream<JcType> {
        require(type is JcRefType)

        return when {
            typeSystem.isSupertype(supportType, type) -> {
                JcTypeStream(
                    typeSystem,
                    rootSequence(typeSystem, type).filter(filtering),
                    type,
                    filtering
                )
            }

            else -> {
                JcTypeStream(
                    typeSystem,
                    cachingSequence.filter { typeSystem.isSupertype(type, it) },
                    supportType,
                    filtering = { filtering(it) && typeSystem.isSupertype(type, it) }
                )
            }
        }
    }

    override fun filterBySubtype(type: JcType): UTypeStream<JcType> {
        require(type is JcRefType)

        return when {
            typeSystem.isSupertype(type, supportType) -> {
                if (type == supportType && filtering(type)) { // exact type
                    USingleTypeStream(typeSystem, type)
                } else {
                    UEmptyTypeStream()
                }
            }

            else -> {
                JcTypeStream(
                    typeSystem,
                    cachingSequence.filter { typeSystem.isSupertype(it, type) },
                    supportType,
                    filtering = { filtering(it) && typeSystem.isSupertype(it, type) }
                )
            }
        }
    }

    override fun filterByNotSupertype(type: JcType): UTypeStream<JcType> {
        require(type is JcRefType)

        return when {
            typeSystem.isSupertype(type, supportType) -> {
                UEmptyTypeStream()
            }

            else -> {
                JcTypeStream(
                    typeSystem,
                    cachingSequence.filter { !typeSystem.isSupertype(type, it) },
                    supportType,
                    filtering = { filtering(it) && !typeSystem.isSupertype(type, it) },
                )
            }
        }
    }

    override fun filterByNotSubtype(type: JcType): UTypeStream<JcType> {
        require(type is JcRefType)

        return when {
            typeSystem.isSupertype(type, supportType) && type != supportType -> {
                this
            }

            else -> {
                JcTypeStream(
                    typeSystem,
                    cachingSequence.filter { !typeSystem.isSupertype(it, type) },
                    supportType,
                    filtering = { filtering(it) && !typeSystem.isSupertype(it, type) }
                )
            }
        }
    }

    override fun take(n: Int, result: MutableCollection<JcType>): Boolean {
        cachingSequence.take(n).toCollection(result)

        return true
    }

    override val isEmpty: Boolean
        get() = cachingSequence.take(1).toList().isEmpty()

    companion object {
        fun from(typeSystem: JcTypeSystem, type: JcType): JcTypeStream {
            val root = rootSequence(typeSystem, type).filter(typeSystem::isInstantiable)
            return JcTypeStream(typeSystem, root, type, typeSystem::isInstantiable)
        }

        private fun rootSequence(
            typeSystem: JcTypeSystem,
            type: JcType,
        ): CachingSequence<JcType> {
            val dfsIterator = DfsIterator(type) { typeNode -> typeSystem.findSubTypes(typeNode).iterator() }

            return CachingSequence(dfsIterator)
        }
    }
}