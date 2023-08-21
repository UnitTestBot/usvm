package org.usvm

import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.collection.id.USymbolicCollectionId
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.util.Region

@Suppress("MemberVisibilityCanBePrivate")
open class UComposer<Type>(
    ctx: UContext,
    internal val memory: UReadOnlyMemory<Type>
) : UExprTransformer<Type>(ctx) {
    open fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> = apply(expr)

    override fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <T : USort> transform(expr: UIteExpr<T>): UExpr<T> =
        transformExprAfterTransformed(expr, expr.condition) { condition ->
            when {
                condition.isTrue -> apply(expr.trueBranch)
                condition.isFalse -> apply(expr.falseBranch)
                else -> super.transform(expr)
            }
        }

    override fun <Sort : USort> transform(
        expr: URegisterReading<Sort>,
    ): UExpr<Sort> = with(expr) { memory.stack.readRegister(idx, sort) }

    override fun <Sort : USort> transform(expr: UCollectionReading<*, *, *>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Method, Sort : USort> transform(
        expr: UIndexedMethodReturnValue<Method, Sort>,
    ): UExpr<Sort> = memory.mocker.eval(expr)

    override fun transform(expr: UIsSubtypeExpr<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.ref) { ref ->
            memory.types.evalIsSubtype(ref, expr.supertype)
        }

    override fun transform(expr: UIsSupertypeExpr<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.ref) { ref ->
            memory.types.evalIsSupertype(ref, expr.subtype)
        }

    fun <CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>, Key, Sort : USort> transformCollectionReading(
        expr: UCollectionReading<CollectionId, Key, Sort>,
        key: Key,
    ): UExpr<Sort> = with(expr) {
        val mappedCollectionId = collection.collectionId.map(this@UComposer)
        val mappedKey = mappedCollectionId.keyMapper(this@UComposer)(key)
        val decomposedKey = mappedCollectionId.rebindKey(mappedKey)
        if (decomposedKey != null) {
            @Suppress("UNCHECKED_CAST")
            // I'm terribly sorry to do this cast, but it's impossible to do it type safe way :(
            val mappedCollection = collection.mapTo(this@UComposer, decomposedKey.collectionId) as USymbolicCollection<*, Any?, Sort>
            return mappedCollection.read(decomposedKey.key)
        }
        val mappedCollection = collection.mapTo(this@UComposer, mappedCollectionId)
        return mappedCollection.read(mappedKey)
    }

    override fun transform(expr: UInputArrayLengthReading<Type>): USizeExpr =
        transformCollectionReading(expr, expr.address)

    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort>): UExpr<Sort> =
        transformCollectionReading(expr, expr.address to expr.index)

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): UExpr<Sort> =
        transformCollectionReading(expr, expr.index)

    override fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field,Sort>): UExpr<Sort> =
        transformCollectionReading(expr, expr.address)

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedSymbolicMapReading<Type, KeySort, Sort, Reg>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.key)

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UInputSymbolicMapReading<Type, KeySort, Sort, Reg>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.address to expr.key)

    override fun transform(expr: UInputSymbolicMapLengthReading<Type>): USizeExpr =
        transformCollectionReading(expr, expr.address)

    override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> = expr

    override fun transform(expr: UNullRef): UExpr<UAddressSort> = memory.nullRef()
}
