package org.usvm

import io.ksmt.KAst
import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KSort
import io.ksmt.sort.KSortVisitor
import io.ksmt.sort.KUninterpretedSort
import io.ksmt.utils.DefaultValueSampler
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.usvm.memory.UAllocatedArrayRegion
import org.usvm.memory.UInputArrayLengthRegion
import org.usvm.memory.UInputArrayRegion
import org.usvm.memory.UInputFieldRegion
import org.usvm.memory.splitUHeapRef
import org.usvm.solver.USolverBase

@Suppress("LeakingThis")
open class UContext(
    components: UComponents<*, *, *>,
    operationMode: OperationMode = OperationMode.CONCURRENT,
    astManagementMode: AstManagementMode = AstManagementMode.GC,
    simplificationMode: SimplificationMode = SimplificationMode.SIMPLIFY
) : KContext(operationMode, astManagementMode, simplificationMode) {

    private val solver by lazy { components.mkSolver(this) }
    private val typeSystem = components.mkTypeSystem(this)

    @Suppress("UNCHECKED_CAST")
    fun <Field, Type, Method> solver(): USolverBase<Field, Type, Method> =
        this.solver as USolverBase<Field, Type, Method>

    @Suppress("UNCHECKED_CAST")
    fun <Type> typeSystem(): UTypeSystem<Type> =
        this.typeSystem as UTypeSystem<Type>

    val addressSort: UAddressSort = mkUninterpretedSort("Address")
    val sizeSort: USizeSort = bv32Sort

    val nullRef: UNullRef = UNullRef(this)

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

    override fun boolSortDefaultValue(): KExpr<KBoolSort> = falseExpr

    override fun <S : KBvSort> bvSortDefaultValue(sort: S): KExpr<S> = mkBv(0, sort)

    fun mkUValueSampler(): KSortVisitor<KExpr<*>> {
        return UValueSampler(this)
    }

    val uValueSampler: KSortVisitor<KExpr<*>> by lazy { mkUValueSampler() }

    class UValueSampler(val uctx: UContext) : DefaultValueSampler(uctx) {
        override fun visit(sort: KUninterpretedSort): KExpr<*> =
            if (sort == uctx.addressSort) {
                uctx.nullRef
            } else {
                super.visit(sort)
            }
    }
}


fun <T : KSort> T.sampleUValue(): KExpr<T> =
    accept(uctx.uValueSampler).asExpr(this)

val KAst.uctx
    get() = ctx as UContext
