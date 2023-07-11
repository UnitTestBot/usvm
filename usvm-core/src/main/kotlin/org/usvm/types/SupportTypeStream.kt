package org.usvm.types

import org.usvm.util.CachingSequence
import org.usvm.util.DfsIterator

/**
 * A persistent type stream based on the [supportType]. Takes inheritors of the [supportType] and
 * provides those that satisfy the [filtering] function.
 *
 * Maintains invariant that [cachingSequence] already filtered with the [filtering] function.
 */
class USupportTypeStream<Type> private constructor(
    private val typeSystem: UTypeSystem<Type>,
    private val cachingSequence: CachingSequence<Type>,
    private val supportType: Type,
    private val filtering: (Type) -> Boolean,
) : UTypeStream<Type> {
    override fun filterBySupertype(type: Type): UTypeStream<Type> {
        return when {
            typeSystem.isSupertype(supportType, type) -> { // we update the [supportType]
                USupportTypeStream(
                    typeSystem,
                    rootSequence(typeSystem, type).filter(filtering),
                    type,
                    filtering
                )
            }

            else -> { // just add one more filter
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
                if (type == supportType && filtering(type) && typeSystem.isInstantiable(type)) { // exact type
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

    override fun take(n: Int): Collection<Type> =
        cachingSequence.take(n).toList()

    override val isEmpty: Boolean
        get() = take(1).isEmpty()

    companion object {
        fun <Type> from(typeSystem: UTypeSystem<Type>, type: Type): USupportTypeStream<Type> {
            val root = rootSequence(typeSystem, type).filter(typeSystem::isInstantiable)
            return USupportTypeStream(typeSystem, root, type, typeSystem::isInstantiable)
        }

        private fun <Type> rootSequence(
            typeSystem: UTypeSystem<Type>,
            type: Type,
        ): CachingSequence<Type> {
            // TODO: we might use another strategy of iterating here
            val dfsIterator = DfsIterator(type) { typeNode -> typeSystem.findSubtypes(typeNode).iterator() }
            return CachingSequence(dfsIterator)
        }
    }
}