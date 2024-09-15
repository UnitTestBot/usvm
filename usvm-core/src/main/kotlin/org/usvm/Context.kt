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
import io.ksmt.utils.uncheckedCast
import org.usvm.collection.array.UAllocatedArray
import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UInputArray
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.length.UInputArrayLengthReading
import org.usvm.collection.array.length.UInputArrayLengths
import org.usvm.collection.field.UInputFieldReading
import org.usvm.collection.field.UInputFields
import org.usvm.collection.map.length.UInputMapLengthCollection
import org.usvm.collection.map.length.UInputMapLengthReading
import org.usvm.collection.map.primitive.UAllocatedMap
import org.usvm.collection.map.primitive.UAllocatedMapReading
import org.usvm.collection.map.primitive.UInputMap
import org.usvm.collection.map.primitive.UInputMapReading
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeys
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeysReading
import org.usvm.collection.map.ref.UInputRefMap
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeys
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithInputKeysReading
import org.usvm.collection.set.primitive.UAllocatedSet
import org.usvm.collection.set.primitive.UAllocatedSetReading
import org.usvm.collection.set.primitive.UInputSet
import org.usvm.collection.set.primitive.UInputSetReading
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElements
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithAllocatedElements
import org.usvm.collection.set.ref.UInputRefSetWithAllocatedElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithInputElements
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsReading
import org.usvm.collection.string.UCharAtExpr
import org.usvm.collection.string.UCharExpr
import org.usvm.collection.string.UCharToLowerExpr
import org.usvm.collection.string.UCharToUpperExpr
import org.usvm.collection.string.UFloatFromStringExpr
import org.usvm.collection.string.UIntFromStringExpr
import org.usvm.collection.string.URegexExpr
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
import org.usvm.collection.string.UStringSort
import org.usvm.collection.string.UStringToLowerExpr
import org.usvm.collection.string.UStringToUpperExpr
import org.usvm.memory.UAddressCounter
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.splitUHeapRef
import org.usvm.regions.Region
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.types.UTypeSystem

@Suppress("LeakingThis")
open class UContext<USizeSort : USort>(
    components: UComponents<*, USizeSort>,
    operationMode: OperationMode = OperationMode.CONCURRENT,
    astManagementMode: AstManagementMode = AstManagementMode.GC,
    simplificationMode: SimplificationMode = SimplificationMode.SIMPLIFY,
) : KContext(operationMode, astManagementMode, simplificationMode) {

    private val solver by lazy { components.mkSolver(this) }
    private val typeSystem by lazy { components.mkTypeSystem(this) }
    private val softConstraintsProvider by lazy { components.mkSoftConstraintsProvider(this) }
    private val composerBuilder: (UReadOnlyMemory<*>) -> UComposer<*, USizeSort> by lazy {
        @Suppress("UNCHECKED_CAST")
        components.mkComposer(this) as (UReadOnlyMemory<*>) -> UComposer<*, USizeSort>
    }

    val sizeExprs by lazy { components.mkSizeExprProvider(this) }
    val statesForkProvider by lazy { components.mkStatesForkProvider() }

    private var currentStateId = 0u

    /**
     * Generates new deterministic id of state in this context.
     */
    fun getNextStateId(): StateId {
        return currentStateId++
    }

    fun <Type> solver(): USolverBase<Type> = this.solver.uncheckedCast()

    @Suppress("UNCHECKED_CAST")
    fun <Type> typeSystem(): UTypeSystem<Type> =
        this.typeSystem as UTypeSystem<Type>

    fun <Type> softConstraintsProvider(): USoftConstraintsProvider<Type, USizeSort> = softConstraintsProvider.cast()

    fun <Type> composer(memory: UReadOnlyMemory<Type>): UComposer<Type, USizeSort> = composerBuilder(memory).cast()

    val addressSort: UAddressSort = mkUninterpretedSort("Address")
    val nullRef: UNullRef = UNullRef(this)

    fun mkNullRef(): USymbolicHeapRef {
        return nullRef
    }

    val addressCounter = UAddressCounter()

    fun mkAddressCounter(): UAddressCounter {
        return addressCounter
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
        mkHeapEqWithFastChecks(lhs, rhs) {
            // unfolding
            val (concreteRefsLhs, symbolicRefsLhs) = splitUHeapRef(lhs, ignoreNullRefs = false, collapseHeapRefs = true)
            val (concreteRefsRhs, symbolicRefsRhs) = splitUHeapRef(rhs, ignoreNullRefs = false, collapseHeapRefs = true)

            val symbolicRefLhs = symbolicRefsLhs.singleOrNull()
            val symbolicRefRhs = symbolicRefsRhs.singleOrNull()

            val concreteRefLhsToGuard = concreteRefsLhs.associate { it.expr.address to it.guard }

            val conjuncts = mutableListOf<UBoolExpr>(falseExpr)

            concreteRefsRhs.forEach { (concreteRefRhs, guardRhs) ->
                val guardLhs = concreteRefLhsToGuard.getOrDefault(concreteRefRhs.address, falseExpr)
                // mkAnd instead of mkAnd with flat=false here is OK
                val conjunct = mkAnd(guardLhs, guardRhs)
                conjuncts += conjunct
            }

            if (symbolicRefLhs != null && symbolicRefRhs != null) {
                val refsEq = super.mkEq(symbolicRefLhs.expr, symbolicRefRhs.expr, order = true)
                // mkAnd instead of mkAnd with flat=false here is OK
                val conjunct = mkAnd(symbolicRefLhs.guard, symbolicRefRhs.guard, refsEq)
                conjuncts += conjunct
            }

            // it's safe to use mkOr here
            mkOr(conjuncts)
        }

    private inline fun mkHeapEqWithFastChecks(
        lhs: UHeapRef,
        rhs: UHeapRef,
        blockOnFailedFastChecks: () -> UBoolExpr,
    ): UBoolExpr = when {
        lhs is USymbolicHeapRef && rhs is USymbolicHeapRef -> super.mkEq(lhs, rhs, order = true)
        isAllocatedConcreteHeapRef(lhs) && isAllocatedConcreteHeapRef(rhs) -> mkBool(lhs == rhs)
        isStaticHeapRef(lhs) && isStaticHeapRef(rhs) -> mkBool(lhs == rhs)

        isAllocatedConcreteHeapRef(lhs) && isStaticHeapRef(rhs) -> falseExpr
        isStaticHeapRef(lhs) && isAllocatedConcreteHeapRef(rhs) -> falseExpr

        isStaticHeapRef(lhs) && rhs is UNullRef -> falseExpr
        lhs is UNullRef && isStaticHeapRef(rhs) -> falseExpr

        lhs is USymbolicHeapRef && isStaticHeapRef(rhs) -> super.mkEq(lhs, rhs, order = true)
        isStaticHeapRef(lhs) && rhs is USymbolicHeapRef -> super.mkEq(lhs, rhs, order = true)

        else -> blockOnFailedFastChecks()
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
        region: UInputFields<Field, Sort>,
        address: UHeapRef,
    ): UInputFieldReading<Field, Sort> = inputFieldReadingCache.createIfContextActive {
        UInputFieldReading(this, region, address)
    }.cast()

    private val allocatedArrayReadingCache = mkAstInterner<UAllocatedArrayReading<*, out USort, USizeSort>>()

    fun <ArrayType, Sort : USort> mkAllocatedArrayReading(
        region: UAllocatedArray<ArrayType, Sort, USizeSort>,
        index: UExpr<USizeSort>,
    ): UAllocatedArrayReading<ArrayType, Sort, USizeSort> = allocatedArrayReadingCache.createIfContextActive {
        UAllocatedArrayReading(this, region, index)
    }.cast()

    private val inputArrayReadingCache = mkAstInterner<UInputArrayReading<*, out USort, USizeSort>>()

    fun <ArrayType, Sort : USort> mkInputArrayReading(
        region: UInputArray<ArrayType, Sort, USizeSort>,
        address: UHeapRef,
        index: UExpr<USizeSort>,
    ): UInputArrayReading<ArrayType, Sort, USizeSort> = inputArrayReadingCache.createIfContextActive {
        UInputArrayReading(this, region, address, index)
    }.cast()

    private val inputArrayLengthReadingCache = mkAstInterner<UInputArrayLengthReading<*, USizeSort>>()

    fun <ArrayType> mkInputArrayLengthReading(
        region: UInputArrayLengths<ArrayType, USizeSort>,
        address: UHeapRef,
    ): UInputArrayLengthReading<ArrayType, USizeSort> = inputArrayLengthReadingCache.createIfContextActive {
        UInputArrayLengthReading(this, region, address)
    }.cast()

    private val allocatedSymbolicMapReadingCache = mkAstInterner<UAllocatedMapReading<*, *, *, *>>()

    fun <MapType, KeySort : USort, Sort : USort, Reg : Region<Reg>> mkAllocatedMapReading(
        region: UAllocatedMap<MapType, KeySort, Sort, Reg>,
        key: UExpr<KeySort>
    ): UAllocatedMapReading<MapType, KeySort, Sort, Reg> =
        allocatedSymbolicMapReadingCache.createIfContextActive {
            UAllocatedMapReading(this, region, key)
        }.cast()

    private val inputSymbolicMapReadingCache = mkAstInterner<UInputMapReading<*, *, *, *>>()

    fun <MapType, KeySort : USort, Reg : Region<Reg>, Sort : USort> mkInputMapReading(
        region: UInputMap<MapType, KeySort, Sort, Reg>,
        address: UHeapRef,
        key: UExpr<KeySort>
    ): UInputMapReading<MapType, KeySort, Sort, Reg> =
        inputSymbolicMapReadingCache.createIfContextActive {
            UInputMapReading(this, region, address, key)
        }.cast()

    private val allocatedSymbolicRefMapWithInputKeysReadingCache =
        mkAstInterner<UAllocatedRefMapWithInputKeysReading<*, *>>()

    fun <MapType, Sort : USort> mkAllocatedRefMapWithInputKeysReading(
        region: UAllocatedRefMapWithInputKeys<MapType, Sort>,
        keyRef: UHeapRef
    ): UAllocatedRefMapWithInputKeysReading<MapType, Sort> =
        allocatedSymbolicRefMapWithInputKeysReadingCache.createIfContextActive {
            UAllocatedRefMapWithInputKeysReading(this, region, keyRef)
        }.cast()

    private val inputSymbolicRefMapWithAllocatedKeysReadingCache =
        mkAstInterner<UInputRefMapWithAllocatedKeysReading<*, *>>()

    fun <MapType, Sort : USort> mkInputRefMapWithAllocatedKeysReading(
        region: UInputRefMapWithAllocatedKeys<MapType, Sort>,
        mapRef: UHeapRef
    ): UInputRefMapWithAllocatedKeysReading<MapType, Sort> =
        inputSymbolicRefMapWithAllocatedKeysReadingCache.createIfContextActive {
            UInputRefMapWithAllocatedKeysReading(this, region, mapRef)
        }.cast()

    private val inputSymbolicRefMapWithInputKeysReadingCache =
        mkAstInterner<UInputRefMapWithInputKeysReading<*, *>>()

    fun <MapType, Sort : USort> mkInputRefMapWithInputKeysReading(
        region: UInputRefMap<MapType, Sort>,
        mapRef: UHeapRef,
        keyRef: UHeapRef
    ): UInputRefMapWithInputKeysReading<MapType, Sort> =
        inputSymbolicRefMapWithInputKeysReadingCache.createIfContextActive {
            UInputRefMapWithInputKeysReading(this, region, mapRef, keyRef)
        }.cast()

    private val inputSymbolicMapLengthReadingCache = mkAstInterner<UInputMapLengthReading<*, USizeSort>>()

    fun <MapType> mkInputMapLengthReading(
        region: UInputMapLengthCollection<MapType, USizeSort>,
        address: UHeapRef
    ): UInputMapLengthReading<MapType, USizeSort> =
        inputSymbolicMapLengthReadingCache.createIfContextActive {
            UInputMapLengthReading(this, region, address)
        }.cast()


    private val allocatedSymbolicSetReadingCache = mkAstInterner<UAllocatedSetReading<*, *, *>>()

    fun <SetType, ElementSort : USort, Reg : Region<Reg>> mkAllocatedSetReading(
        region: UAllocatedSet<SetType, ElementSort, Reg>,
        element: UExpr<ElementSort>
    ): UAllocatedSetReading<SetType, ElementSort, Reg> =
        allocatedSymbolicSetReadingCache.createIfContextActive {
            UAllocatedSetReading(this, region, element)
        }.cast()

    private val inputSymbolicSetReadingCache = mkAstInterner<UInputSetReading<*, *, *>>()

    fun <SetType, ElementSort : USort, Reg : Region<Reg>> mkInputSetReading(
        region: UInputSet<SetType, ElementSort, Reg>,
        address: UHeapRef,
        element: UExpr<ElementSort>
    ): UInputSetReading<SetType, ElementSort, Reg> =
        inputSymbolicSetReadingCache.createIfContextActive {
            UInputSetReading(this, region, address, element)
        }.cast()

    private val allocatedSymbolicRefSetWithInputElementsReadingCache =
        mkAstInterner<UAllocatedRefSetWithInputElementsReading<*>>()

    fun <SetType> mkAllocatedRefSetWithInputElementsReading(
        region: UAllocatedRefSetWithInputElements<SetType>,
        elementRef: UHeapRef
    ): UAllocatedRefSetWithInputElementsReading<SetType> =
        allocatedSymbolicRefSetWithInputElementsReadingCache.createIfContextActive {
            UAllocatedRefSetWithInputElementsReading(this, region, elementRef)
        }.cast()

    private val inputSymbolicRefSetWithAllocatedElementsReadingCache =
        mkAstInterner<UInputRefSetWithAllocatedElementsReading<*>>()

    fun <SetType> mkInputRefSetWithAllocatedElementsReading(
        region: UInputRefSetWithAllocatedElements<SetType>,
        setRef: UHeapRef
    ): UInputRefSetWithAllocatedElementsReading<SetType> =
        inputSymbolicRefSetWithAllocatedElementsReadingCache.createIfContextActive {
            UInputRefSetWithAllocatedElementsReading(this, region, setRef)
        }.cast()

    private val inputSymbolicRefSetWithInputElementsReadingCache =
        mkAstInterner<UInputRefSetWithInputElementsReading<*>>()

    fun <SetType> mkInputRefSetWithInputElementsReading(
        region: UInputRefSetWithInputElements<SetType>,
        setRef: UHeapRef,
        elementRef: UHeapRef
    ): UInputRefSetWithInputElementsReading<SetType> =
        inputSymbolicRefSetWithInputElementsReadingCache.createIfContextActive {
            UInputRefSetWithInputElementsReading(this, region, setRef, elementRef)
        }.cast()

    private val indexedMethodReturnValueCache = mkAstInterner<UIndexedMethodReturnValue<Any, out USort>>()

    fun <Method, Sort : USort> mkIndexedMethodReturnValue(
        method: Method,
        callIndex: Int,
        sort: Sort,
    ): UIndexedMethodReturnValue<Method, Sort> = indexedMethodReturnValueCache.createIfContextActive {
        UIndexedMethodReturnValue(this, method.cast(), callIndex, sort)
    }.cast()

    private val trackedSymbols = mkAstInterner<UTrackedSymbol<out USort>>()
    private var trackedIndex = 0

    fun <Sort : USort> mkTrackedSymbol(
        sort: Sort
    ): UTrackedSymbol<Sort> = trackedSymbols.createIfContextActive {
        UTrackedSymbol(this, name = "tracked#${trackedIndex++}", sort)
    }.cast()
    
    private val isSubtypeExprCache = mkAstInterner<UIsSubtypeExpr<Any>>()
    fun <Type> mkIsSubtypeExpr(
        ref: UHeapRef, type: Type,
    ): UIsSubtypeExpr<Type> = isSubtypeExprCache.createIfContextActive {
        UIsSubtypeExpr(this, ref, type.cast())
    }.cast()

    private val isSupertypeExprCache = mkAstInterner<UIsSupertypeExpr<Any>>()
    fun <Type> mkIsSupertypeExpr(
        ref: UHeapRef, type: Type,
    ): UIsSupertypeExpr<Type> = isSupertypeExprCache.createIfContextActive {
        UIsSupertypeExpr(this, ref, type.cast())
    }.cast()

    fun mkConcreteHeapRefDecl(address: UConcreteHeapAddress): UConcreteHeapRefDecl =
        UConcreteHeapRefDecl(this, address)

    override fun boolSortDefaultValue(): KExpr<KBoolSort> = falseExpr

    override fun <S : KBvSort> bvSortDefaultValue(sort: S): KExpr<S> = mkBv(0, sort)

    fun mkUValueSampler(): KSortVisitor<KExpr<*>> {
        return UValueSampler(this)
    }

    val uValueSampler: KSortVisitor<KExpr<*>> by lazy { mkUValueSampler() }

    class UValueSampler(val uctx: UContext<*>) : DefaultValueSampler(uctx) {
        override fun visit(sort: KUninterpretedSort): KExpr<*> =
            if (sort == uctx.addressSort) {
                uctx.nullRef
            } else {
                super.visit(sort)
            }
    }

    inline fun <T : KSort> mkIte(
        condition: KExpr<KBoolSort>,
        trueBranch: () -> KExpr<T>,
        falseBranch: () -> KExpr<T>
    ): KExpr<T> =
        when (condition) {
            is UTrue -> trueBranch()
            is UFalse -> falseBranch()
            else -> mkIte(condition, trueBranch(), falseBranch())
        }

    val charSort: UCharSort = bv16Sort
    val stringSort = UStringSort(this)

    fun mkChar(value: Char): UCharExpr =
        mkBv(value.code, charSort)

    fun mkCharAddExpr(lhs: UCharExpr, rhs: UCharExpr): UCharExpr =
        mkBvAddExpr(lhs, rhs)

    /**
     * Strings with negative concrete heap addresses -- can be aliased by input strings
     */
    val internedStrings = mutableMapOf<String, UConcreteHeapRef>()

    private val stringLiteralsCache = mkAstInterner<UStringLiteralExpr>()
    fun mkStringLiteral(string: String): UStringLiteralExpr = stringLiteralsCache.createIfContextActive {
        UStringLiteralExpr(this, string)
    }

    fun mkEmptyString() = mkStringLiteral("")

    private val stringFromLanguageExprCache = mkAstInterner<UStringFromLanguageExpr>()
    fun mkStringFromLanguage(ref: UHeapRef): UStringFromLanguageExpr =
        stringFromLanguageExprCache.createIfContextActive {
            UStringFromLanguageExpr(this, ref)
        }

    private val stringFromArrayExprCache = mkAstInterner<UStringFromArrayExpr<*, USizeSort>>()
    fun <ArrayType> mkStringFromArray(
        contentArray: UAllocatedArray<ArrayType, UCharSort, USizeSort>,
        arrayType: ArrayType,
        length: UExpr<USizeSort>
    ): UStringFromArrayExpr<ArrayType, USizeSort> =
        stringFromArrayExprCache.createIfContextActive {
            UStringFromArrayExpr(contentArray, arrayType, length)
        }.cast()

    private val stringConcatExprCache = mkAstInterner<UStringConcatExpr>()
    fun mkStringConcatExpr(left: UStringExpr, right: UStringExpr): UStringConcatExpr =
        stringConcatExprCache.createIfContextActive {
            UStringConcatExpr(left, right)
        }
    
    private val stringLengthExprCache = mkAstInterner<UStringLengthExpr<USizeSort>>()
    fun mkStringLengthExpr(string: UStringExpr): UStringLengthExpr<USizeSort> =
        stringLengthExprCache.createIfContextActive {
            UStringLengthExpr(this, string)
        }

    private val charAtExprCache = mkAstInterner<UCharAtExpr<USizeSort>>()
    fun mkCharAtExpr(string: UStringExpr, index: UExpr<USizeSort>): UCharAtExpr<USizeSort> =
        charAtExprCache.createIfContextActive {
            UCharAtExpr(string, index)
        }

    private val stringHashCodeExprCache = mkAstInterner<UStringHashCodeExpr<USizeSort>>()
    fun mkStringHashCodeExpr(string: UStringExpr): UStringHashCodeExpr<USizeSort> =
        stringHashCodeExprCache.createIfContextActive {
            UStringHashCodeExpr(sizeSort, string)
        }

    private val stringLtExprCache = mkAstInterner<UStringLtExpr>()
    fun mkStringLtExpr(left: UStringExpr, right: UStringExpr): UStringLtExpr =
        stringLtExprCache.createIfContextActive {
            UStringLtExpr(left, right)
        }

    private val stringLeExprCache = mkAstInterner<UStringLeExpr>()
    fun mkStringLeExpr(left: UStringExpr, right: UStringExpr): UStringLeExpr =
        stringLeExprCache.createIfContextActive {
            UStringLeExpr(left, right)
        }

    private val stringSliceExprCache = mkAstInterner<UStringSliceExpr<USizeSort>>()
    fun mkStringSliceExpr(string: UStringExpr, startIndex: UExpr<USizeSort>, length: UExpr<USizeSort>): UStringSliceExpr<USizeSort> =
        stringSliceExprCache.createIfContextActive {
            UStringSliceExpr(string, startIndex, length)
        }

    private val stringFromIntExprCache = mkAstInterner<UStringFromIntExpr<USizeSort>>()
    fun mkStringFromIntExpr(value: UExpr<USizeSort>, radix: Int): UStringFromIntExpr<USizeSort> =
        stringFromIntExprCache.createIfContextActive {
            UStringFromIntExpr(value, radix)
        }.cast()

    private val intFromStringExprCache = mkAstInterner<UIntFromStringExpr<USizeSort>>()
    fun mkIntFromStringExpr(string: UStringExpr, radix: Int): UIntFromStringExpr<USizeSort> =
        intFromStringExprCache.createIfContextActive {
            UIntFromStringExpr(sizeSort, string, radix)
        }

    private val stringFromFloatExprCache = mkAstInterner<UStringFromFloatExpr<*>>()
    fun <UFloatSort: USort> mkStringFromFloatExpr(value: UExpr<UFloatSort>): UStringFromFloatExpr<UFloatSort> =
        stringFromFloatExprCache.createIfContextActive {
            UStringFromFloatExpr(value)
        }.cast()

    private val floatFromStringExprCache = mkAstInterner<UFloatFromStringExpr<*>>()
    fun <UFloatSort: USort> mkFloatFromStringExpr(string: UStringExpr, floatSort: UFloatSort): UFloatFromStringExpr<UFloatSort> =
        floatFromStringExprCache.createIfContextActive {
            UFloatFromStringExpr(floatSort, string)
        }.cast()

    private val stringRepeatExprCache = mkAstInterner<UStringRepeatExpr<USizeSort>>()
    fun mkStringRepeatExpr(string: UStringExpr, times: UExpr<USizeSort>): UStringRepeatExpr<USizeSort> =
        stringRepeatExprCache.createIfContextActive {
            UStringRepeatExpr(string, times)
        }

    private val stringToUpperExprCache = mkAstInterner<UStringToUpperExpr>()
    fun mkStringToUpperExpr(string: UStringExpr): UStringToUpperExpr =
        stringToUpperExprCache.createIfContextActive {
            UStringToUpperExpr(string)
        }

    private val stringToLowerExprCache = mkAstInterner<UStringToLowerExpr>()
    fun mkStringToLowerExpr(string: UStringExpr): UStringToLowerExpr =
        stringToLowerExprCache.createIfContextActive {
            UStringToLowerExpr(string)
        }

    private val charToUpperExprCache = mkAstInterner<UCharToUpperExpr>()
    fun mkCharToUpperExpr(char: UCharExpr): UCharToUpperExpr =
        charToUpperExprCache.createIfContextActive {
            UCharToUpperExpr(char)
        }

    private val charToLowerExprCache = mkAstInterner<UCharToLowerExpr>()
    fun mkCharToLowerExpr(char: UCharExpr): UCharToLowerExpr =
        charToLowerExprCache.createIfContextActive {
            UCharToLowerExpr(char)
        }

    private val stringReverseExprCache = mkAstInterner<UStringReverseExpr>()
    fun mkStringReverseExpr(string: UStringExpr): UStringReverseExpr =
        stringReverseExprCache.createIfContextActive {
            UStringReverseExpr(string)
        }

    private val stringIndexOfExprCache = mkAstInterner<UStringIndexOfExpr<USizeSort>>()
    fun mkStringIndexOfExpr(string: UStringExpr, pattern: UStringExpr): UStringIndexOfExpr<USizeSort> =
        stringIndexOfExprCache.createIfContextActive {
            UStringIndexOfExpr(sizeSort, string, pattern)
        }

    private val regexMatchesExprCache = mkAstInterner<URegexMatchesExpr>()
    fun mkRegexMatchesExpr(string: UStringExpr, pattern: URegexExpr): URegexMatchesExpr =
        regexMatchesExprCache.createIfContextActive {
            URegexMatchesExpr(boolSort, string, pattern)
        }

    private val stringReplaceFirstExprCache = mkAstInterner<UStringReplaceFirstExpr>()
    fun mkStringReplaceFirstExpr(where: UStringExpr, what: UStringExpr, with: UStringExpr): UStringReplaceFirstExpr =
        stringReplaceFirstExprCache.createIfContextActive {
            UStringReplaceFirstExpr(where, what, with)
        }

    private val stringReplaceAllExprCache = mkAstInterner<UStringReplaceAllExpr>()
    fun mkStringReplaceAllExpr(where: UStringExpr, what: UStringExpr, with: UStringExpr): UStringReplaceAllExpr =
        stringReplaceAllExprCache.createIfContextActive {
            UStringReplaceAllExpr(where, what, with)
        }

    private val regexReplaceFirstExprCache = mkAstInterner<URegexReplaceFirstExpr>()
    fun mkRegexReplaceFirstExpr(where: UStringExpr, what: URegexExpr, with: UStringExpr): URegexReplaceFirstExpr =
        regexReplaceFirstExprCache.createIfContextActive {
            URegexReplaceFirstExpr(where, what, with)
        }

    private val regexReplaceAllExprCache = mkAstInterner<URegexReplaceAllExpr>()
    fun mkRegexReplaceAllExpr(where: UStringExpr, what: URegexExpr, with: UStringExpr): URegexReplaceAllExpr =
        regexReplaceAllExprCache.createIfContextActive {
            URegexReplaceAllExpr(where, what, with)
        }
}

val <USizeSort : USort> UContext<USizeSort>.sizeSort: USizeSort get() = sizeExprs.sizeSort

fun <USizeSort : USort> UContext<USizeSort>.mkSizeExpr(size: Int): UExpr<USizeSort> =
    sizeExprs.mkSizeExpr(size)

fun <USizeSort : USort> UContext<USizeSort>.getIntValue(expr: UExpr<USizeSort>): Int? =
    sizeExprs.getIntValue(expr)

fun <USizeSort : USort> UContext<USizeSort>.mkSizeSubExpr(
    lhs: UExpr<USizeSort>,
    rhs: UExpr<USizeSort>
): UExpr<USizeSort> =
    sizeExprs.mkSizeSubExpr(lhs, rhs)

fun <USizeSort : USort> UContext<USizeSort>.mkSizeLeExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr =
    sizeExprs.mkSizeLeExpr(lhs, rhs)

fun <USizeSort : USort> UContext<USizeSort>.mkSizeAddExpr(
    lhs: UExpr<USizeSort>,
    rhs: UExpr<USizeSort>
): UExpr<USizeSort> =
    sizeExprs.mkSizeAddExpr(lhs, rhs)

fun <USizeSort : USort> UContext<USizeSort>.mkSizeMulExpr(
    lhs: UExpr<USizeSort>,
    rhs: UExpr<USizeSort>
): UExpr<USizeSort> =
    sizeExprs.mkSizeMulExpr(lhs, rhs)

fun <USizeSort : USort> UContext<USizeSort>.mkSizeModExpr(
    lhs: UExpr<USizeSort>,
    rhs: UExpr<USizeSort>
): UExpr<USizeSort> =
    sizeExprs.mkSizeModExpr(lhs, rhs)

fun <USizeSort : USort> UContext<USizeSort>.mkSizeGtExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr =
    sizeExprs.mkSizeGtExpr(lhs, rhs)

fun <USizeSort : USort> UContext<USizeSort>.mkSizeGeExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr =
    sizeExprs.mkSizeGeExpr(lhs, rhs)

fun <USizeSort : USort> UContext<USizeSort>.mkSizeLtExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr =
    sizeExprs.mkSizeLtExpr(lhs, rhs)

fun <T : KSort> T.sampleUValue(): KExpr<T> =
    accept(uctx.uValueSampler).asExpr(this)

val KAst.uctx
    get() = ctx as UContext<*>

fun <USizeSort : USort> UContext<*>.withSizeSort(): UContext<USizeSort> = cast()
inline fun <USizeSort : USort, R> UContext<*>.withSizeSort(block: UContext<USizeSort>.() -> R): R =
    block(withSizeSort())

val UConcreteChar.character
    get() = Char(this.shortValue.toInt())
