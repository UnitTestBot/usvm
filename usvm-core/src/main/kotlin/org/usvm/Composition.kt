package org.usvm

import org.usvm.api.readString
import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UArrayMemoryRegion
import org.usvm.collection.array.UArrayRegionId
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
import org.usvm.collection.string.UCharAtExpr
import org.usvm.collection.string.UCharExpr
import org.usvm.collection.string.UCharToLowerExpr
import org.usvm.collection.string.UCharToUpperExpr
import org.usvm.collection.string.UConcreteStringBuilder
import org.usvm.collection.string.UConcreteStringHashCodeBv32Expr
import org.usvm.collection.string.UConcreteStringHashCodeIntExpr
import org.usvm.collection.string.UFloatFromStringExpr
import org.usvm.collection.string.UIntFromStringExpr
import org.usvm.collection.string.URegexMatchesExpr
import org.usvm.collection.string.URegexReplaceAllExpr
import org.usvm.collection.string.URegexReplaceFirstExpr
import org.usvm.collection.string.UStringConcatExpr
import org.usvm.collection.string.UStringExpr
import org.usvm.collection.string.UStringFromArrayExpr
import org.usvm.collection.string.UStringFromFloatExpr
import org.usvm.collection.string.UStringFromIntExpr
import org.usvm.collection.string.UStringFromLanguageExpr
import org.usvm.collection.string.UStringHashCodeExpr
import org.usvm.collection.string.UStringIndexOfExpr
import org.usvm.collection.string.UStringLeExpr
import org.usvm.collection.string.UStringLengthExpr
import org.usvm.collection.string.UStringLiteralExpr
import org.usvm.collection.string.UStringLtExpr
import org.usvm.collection.string.UStringRepeatExpr
import org.usvm.collection.string.UStringReplaceAllExpr
import org.usvm.collection.string.UStringReplaceFirstExpr
import org.usvm.collection.string.UStringReverseExpr
import org.usvm.collection.string.UStringSliceExpr
import org.usvm.collection.string.UStringToLowerExpr
import org.usvm.collection.string.UStringToUpperExpr
import org.usvm.collection.string.concatStrings
import org.usvm.collection.string.getHashCode
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.USymbolicCollectionId
import org.usvm.regions.Region

@Suppress("MemberVisibilityCanBePrivate")
open class UComposer<Type, USizeSort : USort>(
    override val ctx: UContext<USizeSort>,
    internal val memory: UReadOnlyMemory<Type>
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

    override fun transform(expr: UStringLiteralExpr): UStringExpr = expr

    override fun transform(expr: UStringFromLanguageExpr): UStringExpr =
        transformExprAfterTransformed(expr, expr.ref) { ref ->
            memory.readString(ref)
        }

    override fun transform(expr: UStringFromArrayExpr<Type, USizeSort>): UStringExpr =
        transformExprAfterTransformed(expr, expr.length) { length ->
            val concreteLength = ctx.getIntValue(expr.length)
            val arrayRegionId = UArrayRegionId<Type, UCharSort, USizeSort>(expr.charArrayType, ctx.charSort)
            val memory =
                if (concreteLength != null) {
                    val concreteStringBuilder = UConcreteStringBuilder(
                        ctx,
                        arrayRegionId,
                        concreteLength,
                        expr.contentAddress,
                        this
                    )
                    expr.content.applyTo(concreteStringBuilder, null, this)
                    val resultingArray = concreteStringBuilder.charArray
                    if (resultingArray != null) {
                        return ctx.mkStringLiteral(String(resultingArray))
                    }
                    concreteStringBuilder
                } else {
                    val memory = this.memory.toWritableMemory()
                    expr.content.applyTo(memory, null, this)
                    memory
                }
            val arrayRegion = memory.getRegion(arrayRegionId) as UArrayMemoryRegion<Type, UCharSort, USizeSort>
            val content = arrayRegion.getAllocatedArray(expr.charArrayType, ctx.charSort, expr.contentAddress)
            return ctx.mkStringFromArray(content, expr.charArrayType, length)
        }


    override fun transform(expr: UStringConcatExpr): UStringExpr =
        transformExprAfterTransformed(expr, expr.left, expr.right) { left, right ->
            memory.concatStrings<Type, USizeSort>(left, right)
        }

    override fun transform(expr: UConcreteStringHashCodeBv32Expr): UExpr<USizeSort> {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UConcreteStringHashCodeIntExpr): UExpr<USizeSort> {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringHashCodeExpr<USizeSort>): UExpr<USizeSort> =
        transformExprAfterTransformed(expr, expr.string) { string ->
            getHashCode(ctx, string)
        }

    override fun transform(expr: UStringLtExpr): UBoolExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringLeExpr): UBoolExpr {
        TODO("Not yet implemented")
    }

    override fun <UFloatSort : USort> transform(expr: UStringFromFloatExpr<UFloatSort>): UStringExpr {
        TODO("Not yet implemented")
    }

    override fun <UFloatSort : USort> transform(expr: UFloatFromStringExpr<UFloatSort>): UExpr<UFloatSort> {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringToUpperExpr): UStringExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringToLowerExpr): UStringExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UCharToUpperExpr): UCharExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UCharToLowerExpr): UCharExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringReverseExpr): UStringExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: URegexMatchesExpr): UBoolExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringReplaceFirstExpr): UStringExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringReplaceAllExpr): UStringExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: URegexReplaceFirstExpr): UStringExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: URegexReplaceAllExpr): UStringExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringIndexOfExpr<USizeSort>): UExpr<USizeSort> {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringRepeatExpr<USizeSort>): UStringExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UIntFromStringExpr<USizeSort>): UExpr<USizeSort> {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringFromIntExpr<USizeSort>): UStringExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringSliceExpr<USizeSort>): UStringExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UCharAtExpr<USizeSort>): UCharExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UStringLengthExpr<USizeSort>): UExpr<USizeSort> {
        TODO("Not yet implemented")
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T : USort> UComposer<*, *>?.compose(expr: UExpr<T>) = this?.apply(expr) ?: expr
