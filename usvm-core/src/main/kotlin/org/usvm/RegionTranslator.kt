package org.usvm

import org.ksmt.expr.KExpr
import org.ksmt.sort.KArray2Sort
import org.ksmt.sort.KArraySort
import org.ksmt.utils.mkConst
import org.usvm.util.Region
import org.usvm.util.RegionTree
import java.util.IdentityHashMap

/**
 * [URegionTranslator] defines a template method that translates a region reading to a specific [KExpr] with a sort [Sort].
 */
internal class URegionTranslator<in RegionId : URegionId<Key, Sort>, Key, Sort : USort, Result>(
    private val updateTranslator: UUpdateTranslator<Key, Sort, Result>,
    private val updatesTranslator: UUpdatesTranslator<Key, Sort, Result>,
) {
    fun translateReading(region: UMemoryRegion<RegionId, Key, Sort>, key: Key): KExpr<Sort> {
        val translated = translate(region)
        return updateTranslator.select(translated, key)
    }

    private val cache = IdentityHashMap<UMemoryRegion<RegionId, Key, Sort>, Result>()

    private fun translate(region: UMemoryRegion<RegionId, Key, Sort>): Result =
        cache.getOrPut(region) { updatesTranslator.translateUpdates(region.updates) }
}

internal interface UUpdateTranslator<Key, Sort : USort, Result> {
    fun select(result: Result, key: Key): KExpr<Sort>

    fun initialValue(): Result

    fun applyUpdate(previous: Result, update: UUpdateNode<Key, Sort>): Result
}

internal class U1DArrayUpdateTranslator<RegionId : URegionId<UExpr<KeySort>, Sort>, KeySort : USort, Sort : USort>(
    private val translator: UExprTranslator<*, *>,
    private val keySort: KeySort,
    private val regionId: RegionId,
) : UUpdateTranslator<KExpr<KeySort>, Sort, KExpr<KArraySort<KeySort, Sort>>> {
    override fun select(result: KExpr<KArraySort<KeySort, Sort>>, key: KExpr<KeySort>): KExpr<Sort> =
        result.ctx.mkArraySelect(result, key)

    override fun initialValue(): KExpr<KArraySort<KeySort, Sort>> {
        val ctx = regionId.sort.uctx
        val arraySort = ctx.mkArraySort(keySort, regionId.sort)
        val defaultValue = regionId.defaultValue
        val initialArray = if (defaultValue == null) {
            arraySort.mkConst(regionId.toString())
        } else {
            ctx.mkArrayConst(arraySort, defaultValue)
        }
        return initialArray
    }

    override fun applyUpdate(
        previous: KExpr<KArraySort<KeySort, Sort>>,
        update: UUpdateNode<KExpr<KeySort>, Sort>,
    ): KExpr<KArraySort<KeySort, Sort>> = with(previous.uctx) {
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
                        val convertedKey = from.regionId.keyMapper(translator)(update.keyConverter.convert(key))
                        val isInside = update.includesSymbolically(key).translated // already includes guard
                        val result = translator.translateRegionReading(from, convertedKey)
                        val ite = mkIte(isInside, result, previous.select(key))
                        mkArrayLambda(key.decl, ite)
                    }
                }
            }
        }
    }

    private val <ExprSort : USort> KExpr<ExprSort>.translated get() = translator.translate(this)
}

internal class U2DArrayUpdateTranslator<RegionId : URegionId<Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>, Sort>, Key1Sort : USort, Key2Sort : USort, Sort : USort>(
    private val translator: UExprTranslator<*, *>,
    private val key1Sort: Key1Sort,
    private val key2Sort: Key2Sort,
    private val regionId: RegionId,
) : UUpdateTranslator<Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>, Sort, KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>> {
    override fun select(
        result: KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
        key: Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>,
    ): KExpr<Sort> =
        result.ctx.mkArraySelect(result, key.first.translated, key.second.translated)

    override fun initialValue(): KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>> {
        val ctx = regionId.sort.uctx
        val arraySort = ctx.mkArraySort(key1Sort, key2Sort, regionId.sort)

        val defaultValue = regionId.defaultValue
        val initialArray = if (defaultValue == null) {
            arraySort.mkConst(regionId.toString())
        } else {
            ctx.mkArrayConst(arraySort, defaultValue.translated)
        }

        return initialArray
    }

    override fun applyUpdate(
        previous: KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
        update: UUpdateNode<Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>, Sort>,
    ): KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>> = with(previous.uctx) {
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
                            region.regionId.keyMapper(translator)(update.keyConverter.convert(key1 to key2))
                        val isInside = update.includesSymbolically(key1 to key2).translated // already includes guard
                        val result = translator.translateRegionReading(region, convertedKey)
                        val ite = mkIte(isInside, result, previous.select(key1, key2))
                        mkArrayLambda(key1.decl, key2.decl, ite)
                    }
                }
            }
        }
    }

    private val <ExprSort : USort> UExpr<ExprSort>.translated get() = translator.translate(this)
}

internal interface UUpdatesTranslator<Key, Sort : USort, Result> {
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
