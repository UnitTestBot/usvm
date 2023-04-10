package org.usvm

import org.ksmt.KAst
import org.ksmt.KContext
import org.ksmt.expr.KExpr
import org.ksmt.solver.model.DefaultValueSampler.Companion.sampleValue
import org.ksmt.sort.KBoolSort
import org.ksmt.sort.KSort
import org.ksmt.sort.KUninterpretedSort
import org.ksmt.utils.asExpr
import org.ksmt.utils.cast

@Suppress("LeakingThis")
open class UContext(
    operationMode: OperationMode = OperationMode.CONCURRENT,
    astManagementMode: AstManagementMode = AstManagementMode.GC,
    simplificationMode: SimplificationMode = SimplificationMode.SIMPLIFY
) : KContext(operationMode, astManagementMode, simplificationMode) {

    val addressSort: UAddressSort = mkUninterpretedSort("Address")
    val sizeSort: USizeSort = bv32Sort
    val zeroSize: USizeExpr = sizeSort.sampleValue()

    val nullRef: USymbolicHeapRef = UNullRef(this)

    fun mkNullRef(): USymbolicHeapRef {
        return nullRef
    }

    /**
     * Disassembles [lhs] and [rhs], simplifies concrete refs, if it has any, and rewrites it in a DNF, except that the
     * last clause contains a guard and an ite for symbolic addresses. The guard ensures that all concrete refs are
     * bubbled up.
     *
     * For example,
     *
     * ```
     * ite1 =
     * (ite cond1 %0 (ite cond2 %1 0x0))
     *
     * ite2 =
     * (ite cond3 %2 (ite cond4 0x0 0x1))
     *
     * mkHeapRefEq(ite1, ite2) =
     * ((!cond1 && !cond2 && !cond3 && cond4) || ((cond1 || cond2) && cond3 && ite(cond1, %0, %1) == %2))
     * ```
     *
     * @return the new equal rewritten expression without [UConcreteHeapRef]s
     */
    override fun <T : KSort> mkEq(lhs: KExpr<T>, rhs: KExpr<T>, order: Boolean): KExpr<KBoolSort> =
        if (lhs.sort == addressSort) {
            mkHeapRefEq(lhs.asExpr(addressSort), rhs.asExpr(addressSort))
        } else {
            super.mkEq(lhs, rhs, order)
        }

    fun mkHeapRefEq(lhs: UHeapRef, rhs: UHeapRef): UBoolExpr =
        when {
            // fast checks
            lhs is USymbolicHeapRef && rhs is USymbolicHeapRef -> super.mkEq(lhs, rhs, order = true)
            lhs is UConcreteHeapRef && rhs is UConcreteHeapRef -> mkBool(lhs == rhs)
            // unfolding
            else -> {
                val (concreteRefsLhs, symbolicRefLhs) = splitUHeapRef(lhs)
                val (concreteRefsRhs, symbolicRefRhs) = splitUHeapRef(rhs)

                val concreteRefLhsToGuard = concreteRefsLhs.associate { it.expr.address to it.guard }

                val conjuncts = mutableListOf<UBoolExpr>(falseExpr)

                concreteRefsRhs.forEach { (concreteRefRhs, guardRhs) ->
                    val guardLhs = concreteRefLhsToGuard.getOrDefault(concreteRefRhs.address, falseExpr)
                    // mkAnd instead of mkAndNoFlat here is OK
                    val conjunct = mkAnd(guardLhs, guardRhs)
                    conjuncts += conjunct
                }

                if (symbolicRefLhs != null && symbolicRefRhs != null) {
                    val refsEq = super.mkEq(symbolicRefLhs.expr, symbolicRefRhs.expr, order = true)
                    // mkAnd instead of mkAndNoFlat here is OK
                    val conjunct = mkAnd(symbolicRefLhs.guard, symbolicRefRhs.guard, refsEq)
                    conjuncts += conjunct
                }

                // it's safe to use mkOr here
                mkOr(conjuncts)
            }
        }

    private val uConcreteHeapRefCache = mkAstInterner<UConcreteHeapRef>()
    fun mkConcreteHeapRef(address: UConcreteHeapAddress): UConcreteHeapRef =
        uConcreteHeapRefCache.createIfContextActive {
            UConcreteHeapRef(this, address)
        }

    private val registerReadingCache = mkAstInterner<URegisterReading<out USort>>()
    fun <Sort : USort> mkRegisterReading(idx: Int, sort: Sort): URegisterReading<Sort> =
        registerReadingCache.createIfContextActive { URegisterReading(this, idx, sort) }.cast()

    private val inputFieldReadingCache = mkAstInterner<UInputFieldReading<*, out USort>>()

    fun <Field, Sort : USort> mkInputFieldReading(
        region: UInputFieldRegion<Field, Sort>,
        address: UHeapRef,
    ): UInputFieldReading<Field, Sort> = inputFieldReadingCache.createIfContextActive {
        UInputFieldReading(this, region, address)
    }.cast()

    private val allocatedArrayReadingCache = mkAstInterner<UAllocatedArrayReading<*, out USort>>()

    fun <ArrayType, Sort : USort> mkAllocatedArrayReading(
        region: UAllocatedArrayRegion<ArrayType, Sort>,
        index: USizeExpr,
    ): UAllocatedArrayReading<ArrayType, Sort> = allocatedArrayReadingCache.createIfContextActive {
        UAllocatedArrayReading(this, region, index)
    }.cast()

    private val inputArrayReadingCache = mkAstInterner<UInputArrayReading<*, out USort>>()

    fun <ArrayType, Sort : USort> mkInputArrayReading(
        region: UInputArrayRegion<ArrayType, Sort>,
        address: UHeapRef,
        index: USizeExpr,
    ): UInputArrayReading<ArrayType, Sort> = inputArrayReadingCache.createIfContextActive {
        UInputArrayReading(this, region, address, index)
    }.cast()

    private val inputArrayLengthReadingCache = mkAstInterner<UInputArrayLengthReading<*>>()

    fun <ArrayType> mkInputArrayLengthReading(
        region: UInputArrayLengthRegion<ArrayType>,
        address: UHeapRef,
    ): UInputArrayLengthReading<ArrayType> = inputArrayLengthReadingCache.createIfContextActive {
        UInputArrayLengthReading(this, region, address)
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

    override fun uninterpretedSortDefaultValue(sort: KUninterpretedSort): KExpr<KUninterpretedSort> =
        if (sort == addressSort) {
            nullRef
        } else {
            super.uninterpretedSortDefaultValue(sort)
        }

}

val KAst.uctx
    get() = ctx as UContext
