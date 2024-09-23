package org.usvm.model

import io.ksmt.decl.KDecl
import io.ksmt.expr.KArray2Store
import io.ksmt.expr.KArrayConst
import io.ksmt.expr.KArrayStore
import io.ksmt.expr.KExpr
import io.ksmt.expr.KFunctionAsArray
import io.ksmt.expr.KInterpretedValue
import io.ksmt.solver.KModel
import io.ksmt.solver.model.KFuncInterp
import io.ksmt.solver.model.KFuncInterpEntryOneAry
import io.ksmt.solver.model.KFuncInterpEntryTwoAry
import io.ksmt.solver.model.KFuncInterpVarsFree
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArray3Sort
import io.ksmt.sort.KArrayNSort
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KArraySortBase
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KFpRoundingModeSort
import io.ksmt.sort.KFpSort
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import io.ksmt.sort.KSort
import io.ksmt.sort.KSortVisitor
import io.ksmt.sort.KUninterpretedSort
import io.ksmt.utils.uncheckedCast
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.NULL_ADDRESS
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.mkSizeExpr
import org.usvm.sizeSort

/**
 * Retrieves an expression interpretation from the provided [model].
 * In the case when the expression is free in the [model] completes model wrt expression sort.
 * */
open class UModelEvaluator<SizeSort : USort>(
    val ctx: UContext<SizeSort>,
    val model: KModel,
    val addressesMapping: AddressesMapping,
) : KSortVisitor<UExpr<*>> {
    val completedValues = hashMapOf<KExpr<*>, UExpr<*>>()
    val completed1DArrays = hashMapOf<KDecl<*>, UMemory1DArray<*, *>>()
    val completed2DArrays = hashMapOf<KDecl<*>, UMemory2DArray<*, *, *>>()

    open fun visitSize(): UExpr<SizeSort> = ctx.mkSizeExpr(0)
    open fun visitAddress(): UHeapRef = ctx.mkConcreteHeapRef(NULL_ADDRESS)

    override fun visit(sort: KBoolSort): UExpr<*> = ctx.defaultValueSampler.visit(sort)

    override fun <S : KBvSort> visit(sort: S): UExpr<*> = if (sort == ctx.sizeSort) {
        visitSize()
    } else {
        ctx.defaultValueSampler.visit(sort)
    }

    override fun visit(sort: KIntSort): UExpr<*> = if (sort == ctx.sizeSort) {
        visitSize()
    } else {
        ctx.defaultValueSampler.visit(sort)
    }

    override fun visit(sort: KUninterpretedSort): UExpr<*> = if (sort == ctx.addressSort) {
        visitAddress()
    } else {
        ctx.defaultValueSampler.visit(sort)
    }

    override fun visit(sort: KRealSort): UExpr<*> = ctx.defaultValueSampler.visit(sort)
    override fun visit(sort: KFpRoundingModeSort): UExpr<*> = ctx.defaultValueSampler.visit(sort)
    override fun <S : KFpSort> visit(sort: S): UExpr<*> = ctx.defaultValueSampler.visit(sort)

    override fun <D : KSort, R : KSort> visit(sort: KArraySort<D, R>): UExpr<*> =
        ctx.mkArrayConst(sort, sort.range.accept(this).uncheckedCast())

    override fun <D0 : KSort, D1 : KSort, R : KSort> visit(sort: KArray2Sort<D0, D1, R>): UExpr<*> =
        ctx.mkArrayConst(sort, sort.range.accept(this).uncheckedCast())

    override fun <D0 : KSort, D1 : KSort, D2 : KSort, R : KSort> visit(sort: KArray3Sort<D0, D1, D2, R>): UExpr<*> =
        ctx.mkArrayConst(sort, sort.range.accept(this).uncheckedCast())

    override fun <R : KSort> visit(sort: KArrayNSort<R>): UExpr<*> =
        ctx.mkArrayConst(sort, sort.range.accept(this).uncheckedCast())

    /**
     * Evaluate simple (not an array) expression in the model.
     * Complete model according to the expression sort if expr is free in the model.
     * */
    open fun <Sort : USort> evalAndComplete(expr: UExpr<Sort>): UExpr<Sort> {
        val modelValue = model.eval(expr, isComplete = false)
        if (modelValue is KInterpretedValue<*>) {
            return modelValue.mapAddress(addressesMapping).uncheckedCast()
        }

        return completedValues.getOrPut(expr) {
            check(expr.sort !is KArraySortBase<*>) { "Unexpected array expression $expr" }

            expr.sort.accept(this)
        }.uncheckedCast()
    }

    /**
     * Evaluate 1D array expression in the model.
     * Complete model according to the array range (value) sort if array is free in the model.
     * */
    open fun <Idx : USort, Value : USort> evalAndCompleteArray1DMemoryRegion(
        translated: KDecl<KArraySort<Idx, Value>>,
    ): UMemory1DArray<Idx, Value> {
        val interpretation = model.interpretation(translated)

        var stores = persistentHashMapOf<UExpr<Idx>, UExpr<Value>>()
        val defaultValue = interpretation?.let {
            traverse1DArrayEntries(interpretation) { idx, value ->
                stores = stores.put(idx.mapAddress(addressesMapping), value.mapAddress(addressesMapping), ctx.defaultOwnership)
            }
        }

        if (defaultValue != null) {
            return UMemory1DArray(stores, defaultValue.mapAddress(addressesMapping))
        }

        return completed1DArrays.getOrPut(translated) {
            val completedDefault = translated.sort.range.accept(this)
            UMemory1DArray(stores, completedDefault.uncheckedCast())
        }.uncheckedCast()
    }

    /**
     * Evaluate 2D array expression in the model.
     * Complete model according to the array range (value) sort if array is free in the model.
     * */
    open fun <Idx1 : USort, Idx2 : USort, Value : USort> evalAndCompleteArray2DMemoryRegion(
        translated: KDecl<KArray2Sort<Idx1, Idx2, Value>>
    ): UMemory2DArray<Idx1, Idx2, Value> {
        val interpretation = model.interpretation(translated)

        var stores = persistentHashMapOf<Pair<UExpr<Idx1>, UExpr<Idx2>>, UExpr<Value>>()
        val defaultValue = interpretation?.let {
            traverse2DArrayEntries(interpretation) { idx1, idx2, value ->
                val mappedIdx1 = idx1.mapAddress(addressesMapping)
                val mappedIdx2 = idx2.mapAddress(addressesMapping)
                stores = stores.put(mappedIdx1 to mappedIdx2, value.mapAddress(addressesMapping), ctx.defaultOwnership)
            }
        }

        if (defaultValue != null) {
            return UMemory2DArray(stores, defaultValue.mapAddress(addressesMapping))
        }

        return completed2DArrays.getOrPut(translated) {
            val completedDefault = translated.sort.range.accept(this)
            UMemory2DArray(stores, completedDefault.uncheckedCast())
        }.uncheckedCast()
    }

    /**
     * In the KSMT there are two representations for the array model:
     * 1. A bunch of an array store expressions with the constant array base.
     * Usually returned by the Z3 solver.
     * 2. (function-as-array) and the function interpretation.
     * Usually returned by the Yices solver.
     *
     * In the latter case we can avoid intermediate conversion to the array-store style representation.
     * */
    inline fun <Idx : USort, Value : USort> traverse1DArrayEntries(
        interpretation: KFuncInterp<KArraySort<Idx, Value>>,
        onEntry: (KExpr<Idx>, KExpr<Value>) -> Unit
    ): KExpr<Value>? {
        var arrayInterpretation = interpretation.default ?: return null
        if (arrayInterpretation is KFunctionAsArray<*, *>) {
            val functionInterpretation = model.interpretation(arrayInterpretation.function) ?: return null
            check(functionInterpretation is KFuncInterpVarsFree<*>) { "Unexpected vars in array interpretation" }
            for (entry in functionInterpretation.entries) {
                entry as KFuncInterpEntryOneAry<*>
                onEntry(entry.arg.uncheckedCast(), entry.value.uncheckedCast())
            }
            return functionInterpretation.default?.uncheckedCast()
        }

        while (arrayInterpretation is KArrayStore<Idx, Value>) {
            onEntry(arrayInterpretation.index, arrayInterpretation.value)
            arrayInterpretation = arrayInterpretation.array
        }

        check(arrayInterpretation is KArrayConst<*, *>) { "Unexpected array: $arrayInterpretation" }
        return arrayInterpretation.value.uncheckedCast()
    }

    /**
     * See [traverse1DArrayEntries] for the details.
     * */
    inline fun <Idx1 : USort, Idx2 : USort, Value : USort> traverse2DArrayEntries(
        interpretation: KFuncInterp<KArray2Sort<Idx1, Idx2, Value>>,
        onEntry: (KExpr<Idx1>, KExpr<Idx2>, KExpr<Value>) -> Unit
    ): KExpr<Value>? {
        var arrayInterpretation = interpretation.default ?: return null
        if (arrayInterpretation is KFunctionAsArray<*, *>) {
            val functionInterpretation = model.interpretation(arrayInterpretation.function) ?: return null
            check(functionInterpretation is KFuncInterpVarsFree<*>) { "Unexpected vars in array interpretation" }
            for (entry in functionInterpretation.entries) {
                entry as KFuncInterpEntryTwoAry<*>
                onEntry(entry.arg0.uncheckedCast(), entry.arg1.uncheckedCast(), entry.value.uncheckedCast())
            }
            return functionInterpretation.default?.uncheckedCast()
        }

        while (arrayInterpretation is KArray2Store<Idx1, Idx2, Value>) {
            onEntry(arrayInterpretation.index0, arrayInterpretation.index1, arrayInterpretation.value)
            arrayInterpretation = arrayInterpretation.array
        }

        check(arrayInterpretation is KArrayConst<*, *>) { "Unexpected array: $arrayInterpretation" }
        return arrayInterpretation.value.uncheckedCast()
    }
}
