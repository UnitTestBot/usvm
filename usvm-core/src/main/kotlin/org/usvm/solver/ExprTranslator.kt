package org.usvm.solver

import io.ksmt.decl.KDecl
import io.ksmt.expr.KExpr
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UAllocatedArrayReading
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UHeapReading
import org.usvm.UHeapRef
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UInputArrayLengthReading
import org.usvm.UInputArrayReading
import org.usvm.UInputFieldReading
import org.usvm.UIsSubtypeExpr
import org.usvm.UIsSupertypeExpr
import org.usvm.UMockSymbol
import org.usvm.UNullRef
import org.usvm.URegisterReading
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.USymbol
import org.usvm.USymbolicHeapRef
import org.usvm.memory.UAllocatedArrayId
import org.usvm.memory.UInputArrayId
import org.usvm.memory.UInputArrayLengthId
import org.usvm.memory.UInputFieldId
import org.usvm.memory.URegionId
import org.usvm.memory.USymbolicArrayIndex
import java.util.concurrent.ConcurrentHashMap

/**
 * Translates custom [UExpr] to a [KExpr]. Region readings are translated via [URegionTranslator]s.
 * Base version cache everything, but doesn't track translated expressions like register readings, mock symbols, etc.
 *
 * To show semantics of the translator, we use [KExpr] as return values, though [UExpr] is a typealias for it.
 */
open class UExprTranslator<Field, Type>(
    override val ctx: UContext,
) : UExprTransformer<Field, Type>(ctx) {
    open fun <Sort : USort> translate(expr: UExpr<Sort>): KExpr<Sort> = apply(expr)

    override fun <Sort : USort> transform(expr: USymbol<Sort>): KExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): KExpr<Sort> {
        val registerConst = expr.sort.mkConst("r${expr.idx}_${expr.sort}")
        return registerConst
    }

    override fun <Sort : USort> transform(expr: UHeapReading<*, *, *>): KExpr<Sort> =
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

    private val _declToIsSubtypeExpr = mutableMapOf<KDecl<UBoolSort>, UIsSubtypeExpr<Type>>()
    val declToIsSubtypeExpr: Map<KDecl<UBoolSort>, UIsSubtypeExpr<Type>> get() = _declToIsSubtypeExpr

    override fun transform(expr: UIsSubtypeExpr<Type>): KExpr<KBoolSort> {
        require(expr.ref is USymbolicHeapRef) { "Unexpected ref: ${expr.ref}" }

        val const = expr.sort.mkConst("isSubtype#${_declToIsSubtypeExpr.size}")
        // we need to track declarations to pass them to the type solver in the DPLL(T) procedure
        _declToIsSubtypeExpr[const.decl] = expr
        return const
    }

    private val _declToIsSupertypeExpr = mutableMapOf<KDecl<UBoolSort>, UIsSupertypeExpr<Type>>()
    val declToIsSupertypeExpr: Map<KDecl<UBoolSort>, UIsSupertypeExpr<Type>> get() = _declToIsSupertypeExpr
    override fun transform(expr: UIsSupertypeExpr<Type>): KExpr<KBoolSort> {
        require(expr.ref is USymbolicHeapRef) { "Unexpected ref: ${expr.ref}" }

        val const = expr.sort.mkConst("isSupertype#${_declToIsSupertypeExpr.size}")
        // we need to track declarations to pass them to the type solver in the DPLL(T) procedure
        _declToIsSupertypeExpr[const.decl] = expr
        return const
    }

    override fun transform(expr: UInputArrayLengthReading<Type>): KExpr<USizeSort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            val translator = inputArrayLengthIdTranslator(expr.region.regionId)
            translator.translateReading(expr.region, address)
        }

    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address, expr.index) { address, index ->
            val translator = inputArrayIdTranslator(expr.region.regionId)
            translator.translateReading(expr.region, address to index)
        }

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.index) { index ->
            val translator = allocatedArrayIdTranslator(expr.region.regionId)
            translator.translateReading(expr.region, index)
        }

    override fun <Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            val translator = inputFieldIdTranslator(expr.region.regionId)
            translator.translateReading(expr.region, address)
        }

    fun <Sort : USort> translateAllocatedArrayId(
        regionId: UAllocatedArrayId<Type, Sort>,
    ): KExpr<KArraySort<USizeSort, Sort>> =
        with(ctx) {
            val sort = mkArraySort(sizeSort, regionId.sort)
            val translatedDefaultValue = translate(regionId.defaultValue)
            mkArrayConst(sort, translatedDefaultValue)
        }

    fun translateInputArrayLengthId(
        regionId: UInputArrayLengthId<Type>,
    ): KExpr<KArraySort<UAddressSort, USizeSort>> =
        with(ctx) {
            mkArraySort(addressSort, sizeSort).mkConst(regionId.toString()) // TODO: replace toString
        }

    fun <Sort : USort> translateInputArrayId(
        regionId: UInputArrayId<Type, Sort>,
    ): KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>> =
        with(ctx) {
            mkArraySort(addressSort, sizeSort, regionId.sort).mkConst(regionId.toString()) // TODO: replace toString
        }

    fun <Sort : USort> translateInputFieldId(
        regionId: UInputFieldId<Field, Sort>,
    ): KExpr<KArraySort<UAddressSort, Sort>> =
        with(ctx) {
            mkArraySort(addressSort, regionId.sort).mkConst(regionId.toString())
        }

    private val regionIdToTranslator = ConcurrentHashMap<URegionId<*, *, *>, URegionTranslator<*, *, *, *>>()

    private inline fun <reified V : URegionTranslator<*, *, *, *>> getOrPutRegionTranslator(
        regionId: URegionId<*, *, *>,
        defaultValue: () -> V,
    ): V = regionIdToTranslator.getOrPut(regionId, defaultValue) as V

    private fun <Sort : USort> inputFieldIdTranslator(
        regionId: UInputFieldId<Field, Sort>,
    ): URegionTranslator<UInputFieldId<Field, Sort>, UHeapRef, Sort, *> =
        getOrPutRegionTranslator(regionId) {
            require(regionId.defaultValue == null)
            val initialValue = translateInputFieldId(regionId)
            val updateTranslator = U1DUpdatesTranslator(this, initialValue)
            URegionTranslator(updateTranslator)
        }

    private fun <Sort : USort> allocatedArrayIdTranslator(
        regionId: UAllocatedArrayId<Type, Sort>,
    ): URegionTranslator<UAllocatedArrayId<Type, Sort>, USizeExpr, Sort, *> =
        getOrPutRegionTranslator(regionId) {
            requireNotNull(regionId.defaultValue)
            val initialValue = translateAllocatedArrayId(regionId)
            val updateTranslator = U1DUpdatesTranslator(this, initialValue)
            URegionTranslator(updateTranslator)
        }

    private fun <Sort : USort> inputArrayIdTranslator(
        regionId: UInputArrayId<Type, Sort>,
    ): URegionTranslator<UInputArrayId<Type, Sort>, USymbolicArrayIndex, Sort, *> =
        getOrPutRegionTranslator(regionId) {
            require(regionId.defaultValue == null)
            val initialValue = translateInputArrayId(regionId)
            val updateTranslator = U2DUpdatesTranslator(this, initialValue)
            URegionTranslator(updateTranslator)
        }

    private fun inputArrayLengthIdTranslator(
        regionId: UInputArrayLengthId<Type>,
    ): URegionTranslator<UInputArrayLengthId<Type>, UHeapRef, USizeSort, *> =
        getOrPutRegionTranslator(regionId) {
            require(regionId.defaultValue == null)
            val initialValue = translateInputArrayLengthId(regionId)
            val updateTranslator = U1DUpdatesTranslator(this, initialValue)
            URegionTranslator(updateTranslator)
        }
}
