package org.usvm.types

import org.usvm.util.CachingSequence
import org.usvm.util.DfsIterator

class USupportTypeStream<Type> private constructor(
    private val typeSystem: UTypeSystem<Type>,
    private val cachingSequence: CachingSequence<Type>,
    private val supportType: Type,
    private val filtering: (Type) -> Boolean,
) : UTypeStream<Type> {
    override fun filterBySupertype(type: Type): UTypeStream<Type> {
        return when {
            typeSystem.isSupertype(supportType, type) -> {
                USupportTypeStream(
                    typeSystem,
                    rootSequence(typeSystem, type).filter(filtering),
                    type,
                    filtering
                )
            }

            else -> {
                USupportTypeStream(
                    typeSystem,
                    cachingSequence.filter { typeSystem.isSupertype(type, it) },
                    supportType,
                    filtering = { filtering(it) && typeSystem.isSupertype(type, it) }
                )
            }
        }
    }

    override fun filterBySubtype(type: Type): UTypeStream<Type> {
        return when {
            typeSystem.isSupertype(type, supportType) -> {
                if (type == supportType && filtering(type)) { // exact type
                    USingleTypeStream(typeSystem, type)
                } else {
                    UEmptyTypeStream()
                }
            }

            else -> {
                USupportTypeStream(
                    typeSystem,
                    cachingSequence.filter { typeSystem.isSupertype(it, type) },
                    supportType,
                    filtering = { filtering(it) && typeSystem.isSupertype(it, type) }
                )
            }
        }
    }

    override fun filterByNotSupertype(type: Type): UTypeStream<Type> {
        return when {
            typeSystem.isSupertype(type, supportType) -> {
                UEmptyTypeStream()
            }

            else -> {
                USupportTypeStream(
                    typeSystem,
                    cachingSequence.filter { !typeSystem.isSupertype(type, it) },
                    supportType,
                    filtering = { filtering(it) && !typeSystem.isSupertype(type, it) },
                )
            }
        }
    }

    override fun filterByNotSubtype(type: Type): UTypeStream<Type> {
        return when {
            typeSystem.isSupertype(type, supportType) && type != supportType -> {
                this
            }

            else -> {
                USupportTypeStream(
                    typeSystem,
                    cachingSequence.filter { !typeSystem.isSupertype(it, type) },
                    supportType,
                    filtering = { filtering(it) && !typeSystem.isSupertype(it, type) }
                )
            }
        }
    }

    override fun take(n: Int, result: MutableCollection<Type>): Boolean {
        cachingSequence.take(n).toCollection(result)

        return true
    }

    override val isEmpty: Boolean
        get() = cachingSequence.take(1).toList().isEmpty()

    companion object {
        fun <Type> from(typeSystem: UTypeSystem<Type>, type: Type): USupportTypeStream<Type> {
            val root = rootSequence(typeSystem, type).filter(typeSystem::isInstantiable)
            return USupportTypeStream(typeSystem, root, type, typeSystem::isInstantiable)
        }

        private fun <Type> rootSequence(
            typeSystem: UTypeSystem<Type>,
            type: Type,
        ): CachingSequence<Type> {
            val dfsIterator = DfsIterator(type) { typeNode -> typeSystem.findSubtypes(typeNode).iterator() }
            return CachingSequence(dfsIterator)
        }
    }
}