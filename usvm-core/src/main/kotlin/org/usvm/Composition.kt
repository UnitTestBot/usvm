package org.usvm

import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.length.UInputArrayLengthReading
import org.usvm.collection.field.UInputFieldReading
import org.usvm.collection.map.length.UInputMapLengthReading
import org.usvm.collection.map.primitive.UAllocatedMapReading
import org.usvm.collection.map.primitive.UInputMapReading
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithInputKeysReading
import org.usvm.collection.set.primitive.UAllocatedSetReading
import org.usvm.collection.set.primitive.UInputSetReading
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithAllocatedElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsReading
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.USymbolicCollectionId
import org.usvm.regions.Region

@Suppress("MemberVisibilityCanBePrivate")
open class UComposer<Type, USizeSort : USort>(
    ctx: UContext<USizeSort>,
    val memory: UReadOnlyMemory<Type>
) : UExprTransformer<Type, USizeSort>(ctx) {
    open fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> = apply(expr)

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

    override fun <Method, Sort : USort> transform(
        expr: UIndexedMethodReturnValue<Method, Sort>,
    ): UExpr<Sort> = memory.mocker.eval(expr)

    override fun <Sort : USort> transform(
        expr: UTrackedSymbol<Sort>
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
        val mappedKey = collection.collectionId.keyInfo().mapKey(key, this@UComposer)
        return collection.read(mappedKey, this@UComposer)
    }

    override fun transform(expr: UInputArrayLengthReading<Type, USizeSort>): UExpr<USizeSort> =
        transformCollectionReading(expr, expr.address)

    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort, USizeSort>): UExpr<Sort> =
        transformCollectionReading(expr, expr.address to expr.index)

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort, USizeSort>): UExpr<Sort> =
        transformCollectionReading(expr, expr.index)

    override fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort> =
        transformCollectionReading(expr, expr.address)

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedMapReading<Type, KeySort, Sort, Reg>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.key)

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UInputMapReading<Type, KeySort, Sort, Reg>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.address to expr.key)

    override fun <Sort : USort> transform(
        expr: UAllocatedRefMapWithInputKeysReading<Type, Sort>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.keyRef)

    override fun <Sort : USort> transform(
        expr: UInputRefMapWithAllocatedKeysReading<Type, Sort>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.mapRef)

    override fun <Sort : USort> transform(
        expr: UInputRefMapWithInputKeysReading<Type, Sort>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.mapRef to expr.keyRef)

    override fun transform(expr: UInputMapLengthReading<Type, USizeSort>): UExpr<USizeSort> =
        transformCollectionReading(expr, expr.address)

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedSetReading<Type, ElemSort, Reg>
    ): UBoolExpr = transformCollectionReading(expr, expr.element)

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UInputSetReading<Type, ElemSort, Reg>
    ): UBoolExpr = transformCollectionReading(expr, expr.address to expr.element)

    override fun transform(expr: UAllocatedRefSetWithInputElementsReading<Type>): UBoolExpr =
        transformCollectionReading(expr, expr.elementRef)

    override fun transform(expr: UInputRefSetWithAllocatedElementsReading<Type>): UBoolExpr =
        transformCollectionReading(expr, expr.setRef)

    override fun transform(expr: UInputRefSetWithInputElementsReading<Type>): UBoolExpr =
        transformCollectionReading(expr, expr.setRef to expr.elementRef)

    override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> = expr

    override fun transform(expr: UNullRef): UExpr<UAddressSort> = memory.nullRef()

    override fun transform(expr: UPointer): UExpr<UAddressSort> = expr
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T : USort> UComposer<*, *>?.compose(expr: UExpr<T>) = this?.apply(expr) ?: expr
