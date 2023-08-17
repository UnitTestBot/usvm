package org.usvm.types

import org.usvm.util.CachingSequence
import org.usvm.util.DfsIterator

/**
 * A persistent type stream based on the [supportType]. Takes inheritors of the [supportType] and
 * provides those that satisfy the [filtering] function.
 *
 * Maintains invariant that [cachingSequence] already filtered with the [filtering] function.
 *
 * @param cacheFromQueries is a list for the types from queries, which satisfy [filtering].
 * It's based on an observation, that in practice many of them satisfy [filtering], so they can be used for
 * fast checking on emptiness. The list is used, because if the size is small,
 * it's faster than a [kotlinx.collections.immutable.PersistentSet].
 */
class USupportTypeStream<Type> private constructor(
    private val typeSystem: UTypeSystem<Type>,
    private val cachingSequence: CachingSequence<Type>,
    private val cacheFromQueries: List<Type>,
    private val supportType: Type,
    private val filtering: (Type) -> Boolean,
) : UTypeStream<Type> {
    override fun filterBySupertype(type: Type): UTypeStream<Type> =
        when {
            // we update the [supportType]
            typeSystem.isSupertype(supportType, type) -> USupportTypeStream(
                typeSystem,
                rootSequence(typeSystem, type).filter(filtering),
                cacheFromQueries.addIfDoesntExceedSizeAndFilter(
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
            cacheFromQueries.addIfDoesntExceedSizeAndFilter(
                type,
                maxSize = MAX_SIZE,
                filtering
            ) { newFiltering(it) && typeSystem.isSupertype(supportType, it) },
            supportType,
            filtering = { filtering(it) && newFiltering(it) }
        )

    override fun take(n: Int): Set<Type> {
        val set = cacheFromQueries.take(n).toMutableSet()
        val iterator = cachingSequence.iterator()
        while (set.size < n && iterator.hasNext()) {
            set += iterator.next()
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

        /**
         * In practice, usually the type doesn't have more than 8 concrete inheritors, and [MAX_SIZE] is less than
         * the default capacity of [java.util.ArrayList].
         */
        private const val MAX_SIZE = 8

        /**
         * @param type the type to be added
         * @param maxSize the maximum size of the result list
         * @param filtering the filtering function for checking the [type]
         * @param newFiltering the filtering function for checking the types in [this] list and the [type]
         */
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