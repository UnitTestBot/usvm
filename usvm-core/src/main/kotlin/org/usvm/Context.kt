package org.usvm

import org.ksmt.KAst
import org.ksmt.KContext
import org.ksmt.solver.model.DefaultValueSampler.Companion.sampleValue
import org.ksmt.utils.asExpr
import org.ksmt.utils.cast

@Suppress("LeakingThis")
open class UContext(
    private val operationMode: OperationMode = OperationMode.CONCURRENT, // TODO replace it when we have KSMT 0.3.3 version
    private val astManagementMode: AstManagementMode = AstManagementMode.GC, // TODO replace it when we have KSMT 0.3.3 version
    private val simplificationMode: SimplificationMode = SimplificationMode.SIMPLIFY
) : KContext(operationMode, astManagementMode, simplificationMode) {

    val addressSort: UAddressSort = UAddressSort(this)
    val sizeSort: USizeSort = bv32Sort
    val zeroSize: USizeExpr = sizeSort.sampleValue()

    val nullRef = UConcreteHeapRef(this, nullAddress)

    private val uConcreteHeapRefCache = mkAstInterner<UConcreteHeapRef>()
    fun mkConcreteHeapRef(address: UConcreteHeapAddress): UConcreteHeapRef =
        uConcreteHeapRefCache.createIfContextActive {
            UConcreteHeapRef(this, address)
        }

    private val registerReadingCache = mkAstInterner<URegisterReading<out USort>>()
    fun <Sort : USort> mkRegisterReading(idx: Int, sort: Sort): URegisterReading<Sort> =
        registerReadingCache.createIfContextActive { URegisterReading(this, idx, sort) }.cast()

    private val inputFieldReadingCache = mkAstInterner<UFieldReading<Any, out USort>>()

    fun <Field, Sort : USort> mkFieldReading(
        region: UVectorMemoryRegion<Sort>,
        address: UHeapRef,
        field: Field
    ): UFieldReading<Field, Sort> = inputFieldReadingCache.createIfContextActive {
        UFieldReading(this, region, address, field.cast())
    }.cast()

    private val allocatedArrayReadingCache = mkAstInterner<UAllocatedArrayReading<Any, out USort>>()

    fun <ArrayType, Sort : USort> mkAllocatedArrayReading(
        region: UAllocatedArrayMemoryRegion<Sort>,
        address: UConcreteHeapAddress,
        index: USizeExpr,
        arrayType: ArrayType,
        elementSort: Sort
    ): UAllocatedArrayReading<ArrayType, Sort> = allocatedArrayReadingCache.createIfContextActive {
        UAllocatedArrayReading(this, region, address, index, arrayType.cast(), elementSort)
    }.cast()

    private val inputArrayReadingCache = mkAstInterner<UInputArrayReading<Any, out USort>>()

    fun <ArrayType, Sort : USort> mkInputArrayReading(
        region: UInputArrayMemoryRegion<Sort>,
        address: UHeapRef,
        index: USizeExpr,
        arrayType: ArrayType,
        elementSort: Sort
    ): UInputArrayReading<ArrayType, Sort> = inputArrayReadingCache.createIfContextActive {
        UInputArrayReading(this, region, address, index, arrayType.cast(), elementSort)
    }.cast()

    private val arrayLengthCache = mkAstInterner<UArrayLength<Any>>()

    fun <ArrayType> mkArrayLength(
        region: UArrayLengthMemoryRegion,
        address: UHeapRef,
        arrayType: ArrayType
    ): UArrayLength<ArrayType> = arrayLengthCache.createIfContextActive {
        UArrayLength(this, region, address, arrayType.cast())
    }.cast()

    private val indexedMethodReturnValueCache = mkAstInterner<UIndexedMethodReturnValue<Any, out USort>>()

    fun <Method, Sort : USort> mkIndexedMethodReturnValue(
        method: Method,
        callIndex: Int,
        sort: Sort
    ): UIndexedMethodReturnValue<Method, Sort> = indexedMethodReturnValueCache.createIfContextActive {
        UIndexedMethodReturnValue(this, method.cast(), callIndex, sort)
    }.cast()

    private val isExprCache = mkAstInterner<UIsExpr<Any>>()
    fun <Type> mkIsExpr(
        ref: UHeapRef, type: Type
    ): UIsExpr<Type> = isExprCache.createIfContextActive {
        UIsExpr(this, ref, type.cast())
    }.cast()

    fun <Sort : USort> mkDefault(sort: Sort): UExpr<Sort> =
        when (sort) {
            is UAddressSort -> nullRef.asExpr(sort)
            else -> sort.sampleValue()
        }
}

fun <Sort : USort> Sort.defaultValue() =
    when (ctx) {
        is UContext -> (ctx as UContext).mkDefault(this)
        else -> sampleValue()
    }

val KAst.uctx
    get() = ctx as UContext
