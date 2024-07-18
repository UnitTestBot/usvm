package org.usvm.solver

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.memory.UMemoryUpdatesVisitor
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UUpdateNode
import org.usvm.model.UModelEvaluator
import org.usvm.uctx

/**
 * [URegionTranslator] defines a template method that translates a region reading to a specific [KExpr] with a sort
 * [Sort].
 */
interface URegionTranslator<CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>, Key, Sort : USort> {
    fun translateReading(region: USymbolicCollection<CollectionId, Key, Sort>, key: Key): KExpr<Sort>
}

interface URegionDecoder<Key, Sort : USort> {
    fun decodeLazyRegion(
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>,
    ): UReadOnlyMemoryRegion<Key, Sort>?
}

interface UCollectionDecoder<Key, Sort : USort> {
    fun decodeCollection(model: UModelEvaluator<*>): UReadOnlyMemoryRegion<Key, Sort>
}

/**
 * A region translator for 1-dimensional symbolic regions.
 *
 * @param exprTranslator defines how to perform translation on inner values.
 * @param initialValue defines an initial value for a translated array.
 */
abstract class U1DUpdatesTranslator<KeySort : USort, Sort : USort>(
    val exprTranslator: UExprTranslator<*, *>,
    val initialValue: KExpr<KArraySort<KeySort, Sort>>,
) : UMemoryUpdatesVisitor<UExpr<KeySort>, Sort, KExpr<KArraySort<KeySort, Sort>>> {

    /**
     * Note: [key] is already translated, so we don't have to translate it explicitly.
     */
    override fun visitSelect(result: KExpr<KArraySort<KeySort, Sort>>, key: KExpr<KeySort>): KExpr<Sort> =
        result.ctx.mkArraySelect(result, key)

    override fun visitInitialValue(): KExpr<KArraySort<KeySort, Sort>> = initialValue

    override fun visitUpdate(
        previous: KExpr<KArraySort<KeySort, Sort>>,
        update: UUpdateNode<UExpr<KeySort>, Sort>
    ): KExpr<KArraySort<KeySort, Sort>> = with(previous.uctx) {
        when (update) {
            is UPinpointUpdateNode -> {
                val key = update.key.translated
                val value = update.value.translated
                val guard = update.guard.translated
                mkIte(guard, previous.store(key, value), previous)
//                 or
//                val keyDecl = mkFreshConst("k", previous.sort.domain)
//                mkArrayLambda(keyDecl.decl, mkIte(keyDecl eq key and guard, value, previous.select(keyDecl)))
//                 or
//                previous.store(key, mkIte(guard, value, previous.select(key)))
            }

            is URangedUpdateNode<*, *, UExpr<KeySort>, Sort> -> {
                when (update.guard) {
                    falseExpr -> previous
                    else -> translateRangedUpdate(previous, update)
                }
            }
        }
    }

    abstract fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<KeySort, Sort>>,
        update: URangedUpdateNode<*, *, UExpr<KeySort>, Sort>
    ): KExpr<KArraySort<KeySort, Sort>>

    val <ExprSort : USort> UExpr<ExprSort>.translated get() = exprTranslator.translate(this)
}

/**
 * A region translator for 2-dimensional symbolic regions.
 *
 * @param exprTranslator defines how to perform translation on inner values.
 * @param initialValue defines an initial value for a translated array.
 */
abstract class U2DUpdatesTranslator<Key1Sort : USort, Key2Sort : USort, Sort : USort>(
    val exprTranslator: UExprTranslator<*, *>,
    val initialValue: KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
) : UMemoryUpdatesVisitor<Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort, KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>> {

    /**
     * Note: [key] is already translated, so we don't have to call it explicitly.
     */
    override fun visitSelect(
        result: KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
        key: Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>,
    ): KExpr<Sort> =
        result.ctx.mkArraySelect(result, key.first, key.second)

    override fun visitInitialValue(): KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>> = initialValue

    override fun visitUpdate(
        previous: KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
        update: UUpdateNode<Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort>,
    ): KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>> = with(previous.uctx) {
        when (update) {
            is UPinpointUpdateNode -> {
                val key1 = update.key.first.translated
                val key2 = update.key.second.translated
                val value = update.value.translated
                val guard = update.guard.translated
                mkIte(guard, previous.store(key1, key2, value), previous)
            }

            is URangedUpdateNode<*, *, Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort> -> {
                when (update.guard) {
                    falseExpr -> previous
                    else -> translateRangedUpdate(previous, update)
                }
            }
        }
    }

    abstract fun KContext.translateRangedUpdate(
        previous: KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
        update: URangedUpdateNode<*, *, Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, Sort>
    ): KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>

    val <ExprSort : USort> UExpr<ExprSort>.translated get() = exprTranslator.translate(this)
}
