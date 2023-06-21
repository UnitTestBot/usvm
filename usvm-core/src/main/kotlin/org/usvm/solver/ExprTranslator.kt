package org.usvm.solver

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.cast
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UAllocatedArrayReading
import org.usvm.UAllocatedSymbolicMapReading
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UCollectionReading
import org.usvm.UHeapRef
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UInputArrayLengthReading
import org.usvm.UInputArrayReading
import org.usvm.UInputFieldReading
import org.usvm.UInputSymbolicMapLengthReading
import org.usvm.UInputSymbolicMapReading
import org.usvm.UIsExpr
import org.usvm.UMockSymbol
import org.usvm.UNullRef
import org.usvm.URegisterReading
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.USymbol
import org.usvm.memory.UAllocatedArrayId
import org.usvm.memory.UAllocatedSymbolicMapId
import org.usvm.memory.UInputArrayId
import org.usvm.memory.UInputArrayLengthId
import org.usvm.memory.UInputFieldId
import org.usvm.memory.UInputSymbolicMapId
import org.usvm.memory.UInputSymbolicMapLengthId
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UCollectionIdVisitor
import org.usvm.memory.USymbolicArrayIndex
import org.usvm.memory.USymbolicCollection
import org.usvm.uctx
import org.usvm.util.Region

/**
 * Translates custom [UExpr] to a [KExpr]. Region readings are translated via [URegionTranslator]s.
 * Base version cache everything, but doesn't track translated expressions like register readings, mock symbols, etc.
 * Tracking done in the [UTrackingExprTranslator].
 *
 * To show semantics of the translator, we use [KExpr] as return values, though [UExpr] is a typealias for it.
 */
open class UExprTranslator<Field, Type>(
    override val ctx: UContext,
) : UExprTransformer<Field, Type>(ctx), UCollectionIdVisitor<URegionTranslator<*, *, *, *>> {

    open fun <Sort : USort> translate(expr: UExpr<Sort>): KExpr<Sort> = apply(expr)

    override fun <Sort : USort> transform(expr: USymbol<Sort>): KExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): KExpr<Sort> {
        val registerConst = expr.sort.mkConst("r${expr.idx}_${expr.sort}")
        return registerConst
    }

    override fun <Sort : USort> transform(expr: UCollectionReading<*, *, *>): KExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Sort : USort> transform(expr: UMockSymbol<Sort>): KExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): KExpr<Sort> {
        val const = expr.sort.mkConst("m${expr.method}_${expr.callIndex}_${expr.sort}")
        return const
    }

    override fun transform(expr: UNullRef): KExpr<UAddressSort> {
        val const = ctx.mkUninterpretedSortValue(ctx.addressSort, valueIdx = 0)
        return const
    }

    override fun transform(expr: UConcreteHeapRef): KExpr<UAddressSort> =
        error("Unexpected UConcreteHeapRef $expr in UExprTranslator, that has to be impossible by construction!")

    override fun transform(expr: UIsExpr<Type>): KExpr<KBoolSort> =
        error("Unexpected UIsExpr $expr in UExprTranslator, that has to be impossible by construction!")

    override fun transform(expr: UInputArrayLengthReading<Type>): KExpr<USizeSort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            translateRegionReading(expr.collection, address)
        }

    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address, expr.index) { address, index ->
            translateRegionReading(expr.collection, address to index)
        }

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.index) { index ->
            translateRegionReading(expr.collection, index)
        }

    override fun <Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            translateRegionReading(expr.collection, address)
        }

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> transform(
        expr: UAllocatedSymbolicMapReading<KeySort, Reg, Sort>
    ): KExpr<Sort> = transformExprAfterTransformed(expr, expr.key) { key ->
        translateRegionReading(expr.collection, key)
    }

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> transform(
        expr: UInputSymbolicMapReading<KeySort, Reg, Sort>
    ): KExpr<Sort> = transformExprAfterTransformed(expr, expr.address, expr.key) { address, key ->
        translateRegionReading(expr.collection, address to key)
    }

    override fun transform(expr: UInputSymbolicMapLengthReading): KExpr<USizeSort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            translateRegionReading(expr.collection, address)
        }

    open fun <Key, Sort : USort> translateRegionReading(
        region: USymbolicCollection<USymbolicCollectionId<Key, Sort, *>, Key, Sort>,
        key: Key,
    ): KExpr<Sort> {
        val regionTranslator = buildTranslator(region.collectionId)
        return regionTranslator.translateReading(region, key)
    }

    private val regionIdToInitialValue_ = mutableMapOf<USymbolicCollectionId<*, *, *>, KExpr<*>>()
    val regionIdToInitialValue: Map<USymbolicCollectionId<*, *, *>, KExpr<*>> get() = regionIdToInitialValue_

    private inline fun <reified V : KExpr<*>> getOrPutInitialValue(
        regionId: USymbolicCollectionId<*, *, *>,
        defaultValue: () -> V
    ): V = regionIdToInitialValue_.getOrPut(regionId, defaultValue) as V

    private val regionIdConstantNames = mutableMapOf<USymbolicCollectionId<*, *, *>, String>()
    private fun regionConstantName(regionId: USymbolicCollectionId<*, *, *>): String =
        regionIdConstantNames.getOrPut(regionId) {
            "${regionIdConstantNames.size}_${regionId}"
        }

    override fun <Field, Sort : USort> visit(
        collectionId: UInputFieldId<Field, Sort>,
    ): URegionTranslator<UInputFieldId<Field, Sort>, UHeapRef, Sort, *> {
        require(collectionId.defaultValue == null)
        val initialValue = getOrPutInitialValue(collectionId) {
            with(collectionId.sort.uctx) {
                mkArraySort(addressSort, collectionId.sort).mkConst(collectionId.toString()) // TODO: replace toString
            }
        }
        val updateTranslator = U1DArrayUpdateTranslate(this, initialValue)
        return URegionTranslator(updateTranslator)
    }

    override fun <ArrayType, Sort : USort> visit(
        collectionId: UAllocatedArrayId<ArrayType, Sort>,
    ): URegionTranslator<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort, *> {
        requireNotNull(collectionId.defaultValue)
        val initialValue = getOrPutInitialValue(collectionId) {
            with(collectionId.sort.uctx) {
                val sort = mkArraySort(sizeSort, collectionId.sort)
                val translatedDefaultValue = translate(collectionId.defaultValue)
                mkArrayConst(sort, translatedDefaultValue)
            }
        }
        val updateTranslator = U1DArrayUpdateTranslate(this, initialValue)
        return URegionTranslator(updateTranslator)
    }

    override fun <ArrayType, Sort : USort> visit(
        collectionId: UInputArrayId<ArrayType, Sort>,
    ): URegionTranslator<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort, *> {
        require(collectionId.defaultValue == null)
        val initialValue = getOrPutInitialValue(collectionId) {
            with(collectionId.sort.uctx) {
                mkArraySort(addressSort, sizeSort, collectionId.sort).mkConst(collectionId.toString()) // TODO: replace toString
            }
        }
        val updateTranslator = U2DArrayUpdateVisitor(this, initialValue)
        return URegionTranslator(updateTranslator)
    }

    override fun <ArrayType> visit(
        collectionId: UInputArrayLengthId<ArrayType>,
    ): URegionTranslator<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort, *> {
        require(collectionId.defaultValue == null)
        val initialValue = getOrPutInitialValue(collectionId) {
            with(collectionId.sort.uctx) {
                mkArraySort(addressSort, sizeSort).mkConst(collectionId.toString()) // TODO: replace toString
            }
        }
        val updateTranslator = U1DArrayUpdateTranslate(this, initialValue)
        return URegionTranslator(updateTranslator)
    }

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> visit(
        collectionId: UAllocatedSymbolicMapId<KeySort, Reg, Sort>
    ): URegionTranslator<UAllocatedSymbolicMapId<KeySort, Reg, Sort>, *, *, *> {
        requireNotNull(collectionId.defaultValue)
        val initialValue = getOrPutInitialValue(collectionId) {
            with(collectionId.sort.uctx) {
                val sort = mkArraySort(collectionId.descriptor.keySort, collectionId.sort)
                val translatedDefaultValue = translate(collectionId.defaultValue)
                mkArrayConst(sort, translatedDefaultValue)
            }
        }
        val updateTranslator = U1DArrayUpdateTranslate(this, initialValue)
        return URegionTranslator(updateTranslator)
    }

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> visit(
        collectionId: UInputSymbolicMapId<KeySort, Reg, Sort>
    ): URegionTranslator<UInputSymbolicMapId<KeySort, Reg, Sort>, *, *, *> {
        require(collectionId.defaultValue == null)
        val initialValue = getOrPutInitialValue(collectionId) {
            with(collectionId.sort.uctx) {
                val constantName = regionConstantName(collectionId)
                mkArraySort(addressSort, collectionId.descriptor.keySort, collectionId.sort).mkConst(constantName)
            }
        }
        val updateTranslator = U2DArrayUpdateVisitor(this, initialValue)
        return URegionTranslator(updateTranslator)
    }

    override fun visit(collectionId: UInputSymbolicMapLengthId): URegionTranslator<UInputSymbolicMapLengthId, *, *, *> {
        require(collectionId.defaultValue == null)
        val initialValue = getOrPutInitialValue(collectionId) {
            with(collectionId.sort.uctx) {
                val constantName = regionConstantName(collectionId)
                mkArraySort(addressSort, sizeSort).mkConst(constantName)
            }
        }
        val updateTranslator = U1DArrayUpdateTranslate(this, initialValue)
        return URegionTranslator(updateTranslator)
    }

    open fun <Key, Sort : USort> buildTranslator(
        regionId: USymbolicCollectionId<Key, Sort, *>,
    ): URegionTranslator<USymbolicCollectionId<Key, Sort, *>, Key, Sort, *> {
        @Suppress("UNCHECKED_CAST")
        return regionId.accept(this) as URegionTranslator<USymbolicCollectionId<Key, Sort, *>, Key, Sort, *>
    }
}

/**
 * Tracks translated symbols. This information used in [ULazyModelDecoder].
 */
open class UTrackingExprTranslator<Field, Type>(
    ctx: UContext,
) : UExprTranslator<Field, Type>(ctx) {

    val registerIdxToTranslated = mutableMapOf<Int, UExpr<*>>()

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort> =
        registerIdxToTranslated.getOrPut(expr.idx) {
            super.transform(expr)
        }.cast()

    val indexedMethodReturnValueToTranslated = mutableMapOf<Pair<*, Int>, UExpr<*>>()

    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort> =
        indexedMethodReturnValueToTranslated.getOrPut(expr.method to expr.callIndex) {
            super.transform(expr)
        }.cast()

    val translatedNullRef: UHeapRef = super.apply(ctx.nullRef)

    val regionIdToTranslator =
        mutableMapOf<USymbolicCollectionId<*, *, *>, URegionTranslator<USymbolicCollectionId<*, *, *>, *, *, *>>()

    override fun <Key, Sort : USort> buildTranslator(
        regionId: USymbolicCollectionId<Key, Sort, *>,
    ): URegionTranslator<USymbolicCollectionId<Key, Sort, *>, Key, Sort, *> =
        regionIdToTranslator.getOrPut(regionId) {
            super.buildTranslator(regionId).cast()
        }.cast()
}