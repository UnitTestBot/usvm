package org.usvm

import org.ksmt.sort.KArray2Sort
import org.ksmt.sort.KArraySort
import org.ksmt.utils.cast
import java.util.IdentityHashMap

/**
 * [URegionTranslator] defines a template method that translates a region reading to a specific [UExpr] with a sort [Sort].
 */
class URegionTranslator<RegionId : URegionId<Key, Sort, RegionId>, Key, Sort : USort, Result>(
    private val updateTranslator: UMemoryUpdatesVisitor<Key, Sort, Result>,
) {
    fun translateReading(region: USymbolicMemoryRegion<RegionId, Key, Sort>, key: Key): UExpr<Sort> {
        val translated = translate(region)
        return updateTranslator.visitSelect(translated, key)
    }

    private val visitorCache = IdentityHashMap<Any?, Result>()

    private fun translate(region: USymbolicMemoryRegion<RegionId, Key, Sort>): Result =
        region.updates.accept(updateTranslator, visitorCache)
}

interface UMemoryUpdatesVisitor<Key, Sort : USort, Result> {
    fun visitSelect(result: Result, key: Key): UExpr<Sort>

    fun visitInitialValue(): Result

    fun visitUpdate(previous: Result, update: UUpdateNode<Key, Sort>): Result
}

internal class U1DArrayUpdateTranslate<KeySort : USort, Sort : USort>(
    private val exprTranslator: UExprTranslator<*, *>,
    private val initialValue: UExpr<KArraySort<KeySort, Sort>>,
) : UMemoryUpdatesVisitor<UExpr<KeySort>, Sort, UExpr<KArraySort<KeySort, Sort>>> {
    override fun visitSelect(result: UExpr<KArraySort<KeySort, Sort>>, key: UExpr<KeySort>): UExpr<Sort> =
        result.ctx.mkArraySelect(result, key)

    override fun visitInitialValue(): UExpr<KArraySort<KeySort, Sort>> = initialValue

    override fun visitUpdate(
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

            is URangedUpdateNode<*, *, *, *> -> {
                when (update.guard) {
                    falseExpr -> previous
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        (update as URangedUpdateNode<UArrayId<*, Any?, Sort, *>, Any?, UExpr<KeySort>, Sort>)
                        val key = mkFreshConst("k", previous.sort.domain)

                        val from = update.region

                        val keyMapper = from.regionId.keyMapper(exprTranslator)
                        val convertedKey = keyMapper(update.keyConverter.convert(key))
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

internal class U2DArrayUpdateVisitor<
    Key1Sort : USort,
    Key2Sort : USort,
    Sort : USort,
    >(
    private val exprTranslator: UExprTranslator<*, *>,
    private val initialValue: UExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
) : UMemoryUpdatesVisitor<Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort, UExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>> {
    /**
     * [key] is already translated, so we don't have to call it explicitly.
     */
    override fun visitSelect(
        result: UExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
        key: Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>,
    ): UExpr<Sort> =
        result.ctx.mkArraySelect(result, key.first, key.second)

    override fun visitInitialValue(): UExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>> = initialValue

    override fun visitUpdate(
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

            is URangedUpdateNode<*, *, *, *> -> {
                when (update.guard) {
                    falseExpr -> previous
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        (update as URangedUpdateNode<UArrayId<*, Any?, Sort, *>, Any?, Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort>)
                        val key1 = mkFreshConst("k1", previous.sort.domain0)
                        val key2 = mkFreshConst("k2", previous.sort.domain1)

                        val region = update.region
                        val keyMapper = region.regionId.keyMapper(exprTranslator)
                        val convertedKey = keyMapper(update.keyConverter.convert(key1 to key2))
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
