package org.usvm.types

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
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
    private val fromQueries: List<Type>,
    private val supportType: Type,
    private val filtering: (Type) -> Boolean,
) : UTypeStream<Type> {
    override fun filterBySupertype(type: Type): UTypeStream<Type> =
        when {
            // we update the [supportType
            typeSystem.isSupertype(supportType, type) -> USupportTypeStream(
                typeSystem,
                rootSequence(typeSystem, type).filter(filtering),
                fromQueries.addIfDoesntExceedSizeAndFilter(
                    type,
                    maxSize = MAX_SIZE,
                    filtering
                ) { typeSystem.isSupertype(type, it) },
                type,
                filtering
            )

            // just add one more filter
            else -> withNewFiltering(type) { typeSystem.isSupertype(type, it) }
        }

    override fun filterBySubtype(type: Type): UTypeStream<Type> =
        when {
            // exact type
            typeSystem.isSupertype(type, supportType) ->
                if (type == supportType && filtering(type) && typeSystem.isInstantiable(type)) {
                    USingleTypeStream(typeSystem, type)
                } else {
                    emptyTypeStream()
                }

            else -> withNewFiltering(type) { typeSystem.isSupertype(it, type) }
        }

    override fun filterByNotSupertype(type: Type): UTypeStream<Type> =
        when {
            typeSystem.isSupertype(type, supportType) -> emptyTypeStream()
            else -> withNewFiltering(type) { !typeSystem.isSupertype(type, it) }
        }

    override fun filterByNotSubtype(type: Type): UTypeStream<Type> =
        when {
            typeSystem.isSupertype(type, supportType) && type != supportType -> this
            else -> withNewFiltering(type) { !typeSystem.isSupertype(it, type) }
        }

    private fun withNewFiltering(type: Type, newFiltering: (Type) -> Boolean) =
        USupportTypeStream(
            typeSystem,
            cachingSequence.filter(newFiltering),
            fromQueries.addIfDoesntExceedSizeAndFilter(
                type,
                maxSize = MAX_SIZE,
                filtering
            ) { newFiltering(it) && typeSystem.isSupertype(supportType, it) },
            supportType,
            filtering = { filtering(it) && newFiltering(it) }
        )

    override fun take(n: Int): Set<Type> {
        val set = fromQueries.toMutableSet()
        for (it in cachingSequence) {
            if (set.size == n) {
                break
            }
            set += it
        }
        return set
    }

    override val isEmpty: Boolean
        get() = take(1).isEmpty()

    companion object {
        fun <Type> from(typeSystem: UTypeSystem<Type>, type: Type): USupportTypeStream<Type> {
            val root = rootSequence(typeSystem, type).filter(typeSystem::isInstantiable)
            val fromQueries = if (typeSystem.isInstantiable(type)) listOf(type) else listOf()
            return USupportTypeStream(typeSystem, root, fromQueries, type, typeSystem::isInstantiable)
        }

        private fun <Type> rootSequence(
            typeSystem: UTypeSystem<Type>,
            type: Type,
        ): CachingSequence<Type> {
            // TODO: we might use another strategy of iterating here
            val dfsIterator = DfsIterator(type) { typeNode -> typeSystem.findSubtypes(typeNode).iterator() }
            return CachingSequence(dfsIterator)
        }

        private const val MAX_SIZE = 8

        private inline fun <Type> List<Type>.addIfDoesntExceedSizeAndFilter(
            type: Type,
            maxSize: Int,
            filtering: (Type) -> Boolean,
            newFiltering: (Type) -> Boolean,
        ): List<Type> =
            filter(newFiltering)
                .run {
                    if (size < maxSize && filtering(type) && newFiltering(type) && type !in this) {
                       this + type
                    } else {
                        this
                    }
                }
    }
}