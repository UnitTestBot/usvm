package org.usvm

import org.ksmt.KAst
import org.ksmt.KContext
import org.ksmt.cache.AstInterner
import org.ksmt.cache.KInternedObject
import org.ksmt.solver.model.DefaultValueSampler.Companion.sampleValue
import org.ksmt.utils.asExpr
import org.ksmt.utils.cast

@Suppress("LeakingThis")
open class UContext(
    private val operationMode: OperationMode = OperationMode.CONCURRENT, // TODO replace it when we have KSMT 0.3.3 version
    private val astManagementMode: AstManagementMode = AstManagementMode.GC // TODO replace it when we have KSMT 0.3.3 version
) : KContext(operationMode, astManagementMode) {
    val addressSort: UAddressSort = UAddressSort(this)
    val sizeSort: USizeSort = mkBv32Sort()

    val zeroSize: USizeExpr = sizeSort.sampleValue()

    val nullRef = UConcreteHeapRef(this, nullAddress)

    private val uConcreteHeapRefCache = mkAstInterner<UConcreteHeapRef>()
    fun mkConcreteHeapRef(address: UHeapAddress): UConcreteHeapRef =
        uConcreteHeapRefCache.createIfContextActive {
            UConcreteHeapRef(this, address)
        }

    private val registerReadingCache = mkAstInterner<URegisterReading>()
    fun mkRegisterReading(idx: Int, sort: USort): URegisterReading =
        registerReadingCache.createIfContextActive {
            URegisterReading(this, idx, sort)
        }.cast()

    private val inputFieldReadingCache = mkAstInterner<UFieldReading<Any>>()

    fun <Field> mkFieldReading(
        region: UVectorMemoryRegion<USort>,
        address: UHeapRef,
        field: Field
    ): UFieldReading<Field> = inputFieldReadingCache.createIfContextActive {
        UFieldReading(this, region, address, field.cast())
    }.cast()

    private val allocatedArrayReadingCache = mkAstInterner<UAllocatedArrayReading<Any>>()

    fun <ArrayType> mkAllocatedArrayReading(
        region: UAllocatedArrayMemoryRegion<USort>,
        address: UHeapAddress,
        index: USizeExpr,
        arrayType: ArrayType,
        elementSort: USort
    ): UAllocatedArrayReading<ArrayType> = allocatedArrayReadingCache.createIfContextActive {
        UAllocatedArrayReading(this, region, address, index, arrayType.cast(), elementSort)
    }.cast()

    private val inputArrayReadingCache = mkAstInterner<UInputArrayReading<Any>>()

    fun <ArrayType> mkInputArrayReading(
        region: UInputArrayMemoryRegion<USort>,
        address: UHeapRef,
        index: USizeExpr,
        arrayType: ArrayType,
        elementSort: USort
    ): UInputArrayReading<ArrayType> = inputArrayReadingCache.createIfContextActive {
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

    private val indexedMethodReturnValueCache = mkAstInterner<UIndexedMethodReturnValue<Any>>()

    fun <Method> mkIndexedMethodReturnValue(
        method: Method,
        callIndex: Int,
        sort: USort
    ): UIndexedMethodReturnValue<Method> = indexedMethodReturnValueCache.createIfContextActive {
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

    // TODO: delegate it to KSMT
    fun mkNotSimplified(expr: UBoolExpr) =
        when (expr) {
            is UNotExpr -> expr.arg
            else -> expr.ctx.mkNot(expr)
        }

    // TODO remove it when we have KSMT 0.3.3 version
    private inline fun <T> ensureContextActive(block: () -> T): T {
        check(isActive) { "Context is not active" }
        return block()
    }

    // TODO remove it when we have KSMT 0.3.3 version
    private inline fun <T> AstInterner<T>.createIfContextActive(
        builder: () -> T
    ): T where T : KAst, T : KInternedObject = ensureContextActive {
        intern(builder())
    }

    // TODO remove it when we have KSMT 0.3.3 version
    private fun <T> mkAstInterner(): AstInterner<T> where T : KAst, T : KInternedObject =
        org.ksmt.cache.mkAstInterner(operationMode, astManagementMode)


}

fun USort.defaultValue() =
    when (ctx) {
        is UContext -> (ctx as UContext).mkDefault(this)
        else -> sampleValue()
    }

val KAst.uctx
    get() = ctx as UContext
