package org.usvm

import org.ksmt.sort.KArray2Sort
import org.ksmt.sort.KArraySort
import org.usvm.util.Region
import org.usvm.util.RegionTree
import java.util.IdentityHashMap

/**
 * [URegionTranslator] defines a template method that translates a region reading to a specific [UExpr] with a sort [Sort].
 */
class URegionTranslator<in RegionId : URegionId<Key, Sort>, Key, Sort : USort, Result>(
    private val updateTranslator: UUpdateTranslator<Key, Sort, Result>,
    private val updatesTranslator: UUpdatesTranslator<Key, Sort, Result>,
) {
    fun translateReading(region: UMemoryRegion<RegionId, Key, Sort>, key: Key): UExpr<Sort> {
        val translated = translate(region)
        return updateTranslator.select(translated, key)
    }

    fun initialValue(): Result = updateTranslator.initialValue()

    private val cache = IdentityHashMap<UMemoryRegion<RegionId, Key, Sort>, Result>()

    private fun translate(region: UMemoryRegion<RegionId, Key, Sort>): Result =
        cache.getOrPut(region) { updatesTranslator.translateUpdates(region.updates) }
}

interface UUpdateTranslator<Key, Sort : USort, Result> {
    fun select(result: Result, key: Key): UExpr<Sort>

    fun initialValue(): Result

    fun applyUpdate(previous: Result, update: UUpdateNode<Key, Sort>): Result
}

internal class U1DArrayUpdateTranslator<KeySort : USort, Sort : USort>(
    private val exprTranslator: UExprTranslator<*, *>,
    private val initialValue: UExpr<KArraySort<KeySort, Sort>>

) : UUpdateTranslator<UExpr<KeySort>, Sort, UExpr<KArraySort<KeySort, Sort>>> {
    override fun select(result: UExpr<KArraySort<KeySort, Sort>>, key: UExpr<KeySort>): UExpr<Sort> =
        result.ctx.mkArraySelect(result, key)

    override fun initialValue(): UExpr<KArraySort<KeySort, Sort>> = initialValue

    override fun applyUpdate(
        previous: UExpr<KArraySort<KeySort, Sort>>,
        update: UUpdateNode<UExpr<KeySort>, Sort>,
    ): UExpr<KArraySort<KeySort, Sort>> = with(previous.uctx) {
        when (update) {
            is UPinpointUpdateNode -> {
                val key = update.key.translated
                val value = update.value.translated
                val guard = update.guard.translated
                mkIte(guard, previous.store(key, value), previous)
                // or
                // previous.store(update.key, mkIte(update.guard, update.value, previous.select(update.key)))
            }

            is URangedUpdateNode<*, *, *, *, *> -> {
                when (update.guard) {
                    falseExpr -> previous
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        (update as URangedUpdateNode<UArrayId<*, Any?, Sort>, *, Any?, UExpr<KeySort>, Sort>)
                        val key = mkFreshConst("k", previous.sort.domain)

                        val from = update.region
                        val convertedKey = from.regionId.keyMapper(exprTranslator)(update.keyConverter.convert(key))
                        val isInside = update.includesSymbolically(key).translated // already includes guard
                        val result = exprTranslator.translateRegionReading(from, convertedKey)
                        val ite = mkIte(isInside, result, previous.select(key))
                        mkArrayLambda(key.decl, ite)
                    }
                }
            }
        }
    }

    private val <ExprSort : USort> UExpr<ExprSort>.translated get() = exprTranslator.translate(this)
}

internal class U2DArrayUpdateTranslator<
    Key1Sort : USort,
    Key2Sort : USort,
    Sort : USort>(
    private val exprTranslator: UExprTranslator<*, *>,
    private val initialValue: UExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
) : UUpdateTranslator<Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort, UExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>> {
    /**
     * [key] is already translated, so we don't have to call it explicitly.
     */
    override fun select(
        result: UExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
        key: Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>,
    ): UExpr<Sort> =
        result.ctx.mkArraySelect(result, key.first, key.second)

    override fun initialValue(): UExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>> = initialValue

    override fun applyUpdate(
        previous: UExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
        update: UUpdateNode<Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort>,
    ): UExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>> = with(previous.uctx) {
        when (update) {
            is UPinpointUpdateNode -> {
                val key1 = update.key.first.translated
                val key2 = update.key.second.translated
                val value = update.value.translated
                val guard = update.guard.translated
                mkIte(guard, previous.store(key1, key2, value), previous)
            }

            is URangedUpdateNode<*, *, *, *, *> -> {
                when (update.guard) {
                    falseExpr -> previous
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        (update as URangedUpdateNode<UArrayId<*, Any?, Sort>, *, Any?, Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort>)
                        val key1 = mkFreshConst("k1", previous.sort.domain0)
                        val key2 = mkFreshConst("k2", previous.sort.domain1)

                        val region = update.region
                        val convertedKey =
                            region.regionId.keyMapper(exprTranslator)(update.keyConverter.convert(key1 to key2))
                        val isInside = update.includesSymbolically(key1 to key2).translated // already includes guard
                        val result = exprTranslator.translateRegionReading(region, convertedKey)
                        val ite = mkIte(isInside, result, previous.select(key1, key2))
                        mkArrayLambda(key1.decl, key2.decl, ite)
                    }
                }
            }
        }
    }

    private val <ExprSort : USort> UExpr<ExprSort>.translated get() = exprTranslator.translate(this)
}

interface UUpdatesTranslator<Key, Sort : USort, Result> {
    fun translateUpdates(updates: UMemoryUpdates<Key, Sort>): Result
}

internal class UFlatUpdatesTranslator<Key, Sort : USort, Result>(
    private val updateTranslator: UUpdateTranslator<Key, Sort, Result>,
) : UUpdatesTranslator<Key, Sort, Result> {
    private val cache: IdentityHashMap<UMemoryUpdates<Key, Sort>, Result> = IdentityHashMap()

    override fun translateUpdates(
        updates: UMemoryUpdates<Key, Sort>,
    ): Result =
        when (updates) {
            is UFlatUpdates<Key, Sort> -> translateFlatUpdates(updates)
            else -> error("This updates translator works only with UFlatUpdates")
        }

    private fun translateFlatUpdates(updates: UFlatUpdates<Key, Sort>): Result {
        val result = cache.getOrPut(updates) {
            val node = updates.node ?: return@getOrPut updateTranslator.initialValue()
            val accumulated = translateUpdates(node.next)
            updateTranslator.applyUpdate(accumulated, node.update)
        }
        return result
    }
}

internal class UTreeUpdatesTranslator<Key, Sort : USort, Result>(
    private val updateTranslator: UUpdateTranslator<Key, Sort, Result>,
) : UUpdatesTranslator<Key, Sort, Result> {
    private val cache: IdentityHashMap<RegionTree<UUpdateNode<Key, Sort>, *>, Result> = IdentityHashMap()

    override fun translateUpdates(updates: UMemoryUpdates<Key, Sort>): Result {
        require(updates is UTreeUpdates<Key, *, Sort>) { "This updates translator works only with UTreeUpdates" }

        return cache.getOrPut(updates.updates) {
            Builder(updates).leftMostTranslate(updates.updates)
        }
    }

    private inner class Builder(
        private val treeUpdates: UTreeUpdates<Key, *, Sort>,
    ) {
        private val emittedUpdates = hashSetOf<UUpdateNode<Key, Sort>>()

        fun leftMostTranslate(updates: RegionTree<UUpdateNode<Key, Sort>, *>): Result {
            var result = cache[updates]

            if (result != null) {
                return result
            }

            val entryIterator = updates.entries.iterator()
            if (!entryIterator.hasNext()) {
                return updateTranslator.initialValue()
            }
            val (update, nextUpdates) = entryIterator.next().value
            result = leftMostTranslate(nextUpdates)
            result = updateTranslator.applyUpdate(result, update)
            return notLeftMostTranslate(result, entryIterator)
        }

        private fun notLeftMostTranslate(
            accumulator: Result,
            iterator: Iterator<Map.Entry<Region<*>, Pair<UUpdateNode<Key, Sort>, RegionTree<UUpdateNode<Key, Sort>, *>>>>,
        ): Result {
            var accumulated = accumulator
            while (iterator.hasNext()) {
                val (reg, entry) = iterator.next()
                val (update, tree) = entry
                accumulated = notLeftMostTranslate(accumulated, tree.entries.iterator())

                accumulated = addIfNeeded(accumulated, update, reg)
            }
            return accumulated
        }

        private fun addIfNeeded(accumulated: Result, update: UUpdateNode<Key, Sort>, region: Region<*>): Result {
            if (treeUpdates.checkWasCloned(update, region)) {
                if (update in emittedUpdates) {
                    return accumulated
                }
                emittedUpdates += update
                updateTranslator.applyUpdate(accumulated, update)
            }

            return updateTranslator.applyUpdate(accumulated, update)
        }
    }
}
