package org.usvm.solver

import io.ksmt.decl.KDecl
import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.mkConst
import io.ksmt.utils.uncheckedCast
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UCollectionReading
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UIsExpr
import org.usvm.UIsSubtypeExpr
import org.usvm.UIsSupertypeExpr
import org.usvm.UMockSymbol
import org.usvm.UNullRef
import org.usvm.URegisterReading
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.USymbol
import org.usvm.USymbolicHeapRef
import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UArrayRegionDecoder
import org.usvm.collection.array.UArrayRegionId
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.USymbolicArrayId
import org.usvm.collection.array.length.UArrayLengthRegionDecoder
import org.usvm.collection.array.length.UArrayLengthsRegionId
import org.usvm.collection.array.length.UInputArrayLengthReading
import org.usvm.collection.field.UFieldRegionDecoder
import org.usvm.collection.field.UFieldsRegionId
import org.usvm.collection.field.UInputFieldReading
import org.usvm.collection.field.USymbolicFieldId
import org.usvm.collection.map.length.UInputMapLengthReading
import org.usvm.collection.map.length.UMapLengthRegionDecoder
import org.usvm.collection.map.length.UMapLengthRegionId
import org.usvm.collection.map.primitive.UAllocatedMapReading
import org.usvm.collection.map.primitive.UInputMapReading
import org.usvm.collection.map.primitive.UMapRegionDecoder
import org.usvm.collection.map.primitive.UMapRegionId
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithInputKeysReading
import org.usvm.collection.map.ref.URefMapRegionDecoder
import org.usvm.collection.map.ref.URefMapRegionId
import org.usvm.memory.UMemoryRegionId
import org.usvm.util.Region
import java.util.concurrent.ConcurrentHashMap

/**
 * Translates custom [UExpr] to a [KExpr]. Region readings are translated via [URegionTranslator]s.
 * Base version cache everything, but doesn't track translated expressions like register readings, mock symbols, etc.
 *
 * To show semantics of the translator, we use [KExpr] as return values, though [UExpr] is a typealias for it.
 */
open class UExprTranslator<Type>(
    override val ctx: UContext,
) : UExprTransformer<Type>(ctx) {
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
        val const = expr.sort.mkConst("null")
        return const
    }

    override fun transform(expr: UConcreteHeapRef): KExpr<UAddressSort> =
        error("Unexpected UConcreteHeapRef $expr in UExprTranslator, that has to be impossible by construction!")

    private val _declToIsExpr = mutableMapOf<KDecl<UBoolSort>, UIsExpr<Type>>()
    val declToIsExpr: Map<KDecl<UBoolSort>, UIsExpr<Type>> get() = _declToIsExpr

    override fun transform(expr: UIsSubtypeExpr<Type>): KExpr<KBoolSort> {
        require(expr.ref is USymbolicHeapRef) { "Unexpected ref: ${expr.ref}" }

        val const = expr.sort.mkConst("isSubtype#${_declToIsExpr.size}")
        // we need to track declarations to pass them to the type solver in the DPLL(T) procedure
        _declToIsExpr[const.decl] = expr
        return const
    }

    override fun transform(expr: UIsSupertypeExpr<Type>): KExpr<KBoolSort> {
        require(expr.ref is USymbolicHeapRef) { "Unexpected ref: ${expr.ref}" }

        val const = expr.sort.mkConst("isSupertype#${_declToIsExpr.size}")
        // we need to track declarations to pass them to the type solver in the DPLL(T) procedure
        _declToIsExpr[const.decl] = expr
        return const
    }

    override fun transform(expr: UInputArrayLengthReading<Type>): KExpr<USizeSort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            val arrayLengthRegionId = with(expr.collection.collectionId) {
                UArrayLengthsRegionId(sort, arrayType)
            }

            val translator = getOrPutRegionDecoder(arrayLengthRegionId) {
                UArrayLengthRegionDecoder(arrayLengthRegionId, this)
            }.inputArrayLengthRegionTranslator(expr.collection.collectionId)

            translator.translateReading(expr.collection, address)
        }

    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address, expr.index) { address, index ->
            val translator = arrayRegionDecoder(expr.collection.collectionId)
                .inputArrayRegionTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, address to index)
        }

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.index) { index ->
            val translator = arrayRegionDecoder(expr.collection.collectionId)
                .allocatedArrayRegionTranslator(expr.collection.collectionId)
            translator.translateReading(expr.collection, index)
        }

    override fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            val fieldRegionId = with(expr.collection.collectionId) { UFieldsRegionId(field, sort) }

            val translator = getOrPutRegionDecoder(fieldRegionId) {
                UFieldRegionDecoder(fieldRegionId, this)
            }.inputFieldRegionTranslator(expr.collection.collectionId)

            translator.translateReading(expr.collection, address)
        }

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedMapReading<Type, KeySort, Sort, Reg>
    ): KExpr<Sort> = transformExprAfterTransformed(expr, expr.key) { key ->
        val symbolicMapRegionId = with(expr.collection.collectionId) {
            UMapRegionId(keySort, valueSort, mapType, keyInfo)
        }

        val translator = getOrPutRegionDecoder(symbolicMapRegionId) {
            UMapRegionDecoder(symbolicMapRegionId, this)
        }.allocatedMapTranslator(expr.collection.collectionId)

        translator.translateReading(expr.collection, key)
    }

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UInputMapReading<Type, KeySort, Sort, Reg>
    ): KExpr<Sort> = transformExprAfterTransformed(expr, expr.address, expr.key) { address, key ->
        val symbolicMapRegionId = with(expr.collection.collectionId) {
            UMapRegionId(keySort, valueSort, mapType, keyInfo)
        }

        val translator = getOrPutRegionDecoder(symbolicMapRegionId) {
            UMapRegionDecoder(symbolicMapRegionId, this)
        }.inputMapTranslator(expr.collection.collectionId)

        translator.translateReading(expr.collection, address to key)
    }

    override fun <Sort : USort> transform(
        expr: UAllocatedRefMapWithInputKeysReading<Type, Sort>
    ): UExpr<Sort> = transformExprAfterTransformed(expr, expr.keyRef) { keyRef ->
        val symbolicRefMapRegionId = with(expr.collection.collectionId) {
            URefMapRegionId(sort, mapType)
        }

        val translator = getOrPutRegionDecoder(symbolicRefMapRegionId) {
            URefMapRegionDecoder(symbolicRefMapRegionId, this)
        }.allocatedRefMapWithInputKeysTranslator(expr.collection.collectionId)

        translator.translateReading(expr.collection, keyRef)
    }

    override fun <Sort : USort> transform(
        expr: UInputRefMapWithAllocatedKeysReading<Type, Sort>
    ): UExpr<Sort> = transformExprAfterTransformed(expr, expr.mapRef) { mapRef ->
        val symbolicRefMapRegionId = with(expr.collection.collectionId) {
            URefMapRegionId(sort, mapType)
        }

        val translator = getOrPutRegionDecoder(symbolicRefMapRegionId) {
            URefMapRegionDecoder(symbolicRefMapRegionId, this)
        }.inputRefMapWithAllocatedKeysTranslator(expr.collection.collectionId)

        translator.translateReading(expr.collection, mapRef)
    }

    override fun <Sort : USort> transform(
        expr: UInputRefMapWithInputKeysReading<Type, Sort>
    ): UExpr<Sort> = transformExprAfterTransformed(expr, expr.mapRef, expr.keyRef) { mapRef, keyRef ->
        val symbolicRefMapRegionId = with(expr.collection.collectionId) {
            URefMapRegionId(sort, mapType)
        }

        val translator = getOrPutRegionDecoder(symbolicRefMapRegionId) {
            URefMapRegionDecoder(symbolicRefMapRegionId, this)
        }.inputRefMapTranslator(expr.collection.collectionId)

        translator.translateReading(expr.collection, mapRef to keyRef)
    }

    override fun transform(expr: UInputMapLengthReading<Type>): KExpr<USizeSort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            val symbolicMapLengthRegionId = with(expr.collection.collectionId) {
                UMapLengthRegionId(sort, mapType)
            }

            val translator = getOrPutRegionDecoder(symbolicMapLengthRegionId) {
                UMapLengthRegionDecoder(symbolicMapLengthRegionId, this)
            }.inputMapLengthRegionTranslator(expr.collection.collectionId)

            translator.translateReading(expr.collection, address)
        }

    fun <Field, Sort : USort, FieldId : USymbolicFieldId<Field, *, Sort, FieldId>> fieldsRegionDecoder(
        fieldId: FieldId
    ): UFieldRegionDecoder<Field, Sort> {
        val fieldRegionId = UFieldsRegionId(fieldId.field, fieldId.sort)
        return getOrPutRegionDecoder(fieldRegionId) {
            UFieldRegionDecoder(fieldRegionId, this)
        }
    }

    fun <ArrayType, Sort : USort, ArrayId : USymbolicArrayId<ArrayType, *, Sort, ArrayId>> arrayRegionDecoder(
        arrayId: ArrayId
    ): UArrayRegionDecoder<ArrayType, Sort> {
        val arrayRegionId = UArrayRegionId(arrayId.arrayType, arrayId.sort)
        return getOrPutRegionDecoder(arrayRegionId) {
            UArrayRegionDecoder(arrayRegionId, this)
        }
    }

    val regionIdToDecoder: MutableMap<UMemoryRegionId<*, *>, URegionDecoder<*, *>> = ConcurrentHashMap()

    inline fun <reified D : URegionDecoder<*, *>> getOrPutRegionDecoder(
        regionId: UMemoryRegionId<*, *>,
        buildDecoder: () -> D
    ): D = regionIdToDecoder.getOrPut(regionId) {
        buildDecoder()
    }.uncheckedCast()
}
