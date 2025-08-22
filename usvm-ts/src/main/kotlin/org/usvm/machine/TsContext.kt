package org.usvm.machine

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsEnumValueType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsValue
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UBv32Sort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.allocateConcreteRef
import org.usvm.api.allocateStaticRef
import org.usvm.api.typeStreamOf
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.Constants.Companion.MAGIC_OFFSET
import org.usvm.machine.expr.TsUndefinedSort
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.TsVoidSort
import org.usvm.machine.expr.TsVoidValue
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.EtsFakeType
import org.usvm.memory.UReadOnlyMemory
import org.usvm.types.single
import org.usvm.util.mkFieldLValue
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

typealias TsSizeSort = UBv32Sort

class TsContext(
    val scene: EtsScene,
    components: TsComponents,
) : UContext<TsSizeSort>(components) {
    val undefinedSort: TsUndefinedSort by lazy { TsUndefinedSort(this) }

    val unresolvedSort: TsUnresolvedSort = TsUnresolvedSort(this)

    val voidSort: TsVoidSort by lazy { TsVoidSort(this) }
    val voidValue: TsVoidValue by lazy { TsVoidValue(this) }

    @Deprecated("Use mkUndefinedValue() or mkTsNullValue() instead")
    override fun mkNullRef(): Nothing {
        error("Use mkUndefinedValue() or mkTsNullValue() instead of mkNullRef() in TS context")
    }

    /**
     * In TS we treat undefined value as a null reference in other objects.
     * For real null represented in the language we create another reference.
     */
    private val undefinedValue: UHeapRef = nullRef
    fun mkUndefinedValue(): UHeapRef = undefinedValue

    private val nullValue: UConcreteHeapRef = allocateStaticRef()
    fun mkTsNullValue(): UConcreteHeapRef = nullValue

    // String constant caching at context level
    private val stringConstants: MutableMap<String, UConcreteHeapRef> = mutableMapOf()

    /**
     * Reverse mapping from heap references to their original string constant values.
     * This is used during test resolution to retrieve the actual string content when
     * encountering a heap reference that represents a string constant.
     */
    private val heapRefToStringConstant: MutableMap<UConcreteHeapRef, String> = mutableMapOf()

    /**
     * Returns a heap reference for a string constant without any initialization.
     */
    fun mkStringConstantRef(value: String): UConcreteHeapRef {
        return stringConstants.getOrPut(value) {
            val ref = allocateConcreteRef()
            heapRefToStringConstant[ref] = value
            ref
        }
    }

    /**
     * Creates a fully initialized string constant in the given state.
     * This should be used when you need a complete string object with memory initialization.
     */
    fun mkStringConstant(value: String, scope: TsStepScope): UConcreteHeapRef {
        return scope.calcOnState {
            mkInitializedStringConstant(value)
        }
    }

    /**
     * Gets the original string value for a heap reference that represents a string constant.
     * Used by test resolver to retrieve string values.
     */
    fun getStringConstantValue(ref: UConcreteHeapRef): String? {
        return heapRefToStringConstant[ref]
    }

    fun getLocalIdx(local: EtsValue, method: EtsMethod): Int? =
        // Note: below, 'n' means the number of arguments
        when (local) {
            // Note: 'this' has index 0
            is EtsThis -> 0

            // Note: arguments have indices from 1 to n
            is EtsParameterRef -> local.index + 1

            // Note: locals have indices starting from (n+1)
            is EtsLocal -> method.locals.indexOfFirst { it.name == local.name }
                .takeIf { it >= 0 }
                ?.let { it + method.parameters.size + 1 }

            else -> error("Unexpected local: $local")
        }

    fun typeToSort(type: EtsType): USort = when (type) {
        is EtsBooleanType -> boolSort
        is EtsNumberType -> fp64Sort
        is EtsStringType -> addressSort
        is EtsNullType -> addressSort
        is EtsUndefinedType -> addressSort
        is EtsUnionType -> unresolvedSort
        is EtsRefType -> addressSort
        is EtsAnyType -> unresolvedSort
        is EtsUnknownType -> unresolvedSort
        is EtsAliasType -> typeToSort(type.originalType)
        is EtsEnumValueType -> unresolvedSort

        is EtsGenericType -> {
            if (type.constraint == null && type.defaultType == null) {
                unresolvedSort
            } else {
                TODO("Not yet implemented")
            }
        }

        else -> TODO("${type::class.simpleName} is not yet supported: $type")
    }

    fun arrayDescriptorOf(type: EtsArrayType): EtsType {
        return when (type.elementType) {
            is EtsBooleanType -> EtsArrayType(EtsBooleanType, dimensions = 1)
            is EtsNumberType -> EtsArrayType(EtsNumberType, dimensions = 1)
            is EtsArrayType -> TODO("Unsupported yet: $type")
            is EtsUnionType -> EtsArrayType(type.elementType, dimensions = 1)
            else -> EtsArrayType(EtsUnknownType, dimensions = 1)
        }
    }

    fun UConcreteHeapRef.getFakeType(memory: UReadOnlyMemory<*>): EtsFakeType {
        check(isFakeObject())
        return memory.typeStreamOf(this).single() as EtsFakeType
    }

    fun UConcreteHeapRef.getFakeType(scope: TsStepScope): EtsFakeType =
        scope.calcOnState { getFakeType(memory) }

    @OptIn(ExperimentalContracts::class)
    fun UExpr<*>.isFakeObject(): Boolean {
        contract {
            returns(true) implies (this@isFakeObject is UConcreteHeapRef)
        }

        return sort == addressSort && this is UConcreteHeapRef && address > MAGIC_OFFSET
    }

    fun UExpr<*>.toFakeObject(scope: TsStepScope): UConcreteHeapRef {
        if (isFakeObject()) {
            return this
        }

        val ref = createFakeObjectRef()

        scope.doWithState {
            when (sort) {
                boolSort -> {
                    val lvalue = getIntermediateBoolLValue(ref.address)
                    memory.write(lvalue, asExpr(boolSort), guard = trueExpr)
                    memory.types.allocate(ref.address, EtsFakeType.mkBool(this@TsContext))
                }

                fp64Sort -> {
                    val lValue = getIntermediateFpLValue(ref.address)
                    memory.write(lValue, asExpr(fp64Sort), guard = trueExpr)
                    memory.types.allocate(ref.address, EtsFakeType.mkFp(this@TsContext))
                }

                addressSort -> {
                    val lValue = getIntermediateRefLValue(ref.address)
                    memory.write(lValue, asExpr(addressSort), guard = trueExpr)
                    memory.types.allocate(ref.address, EtsFakeType.mkRef(this@TsContext))
                }

                else -> TODO("Not yet supported")
            }
        }

        return ref
    }

    fun UExpr<*>.extractSingleValueFromFakeObjectOrNull(scope: TsStepScope): UExpr<*>? {
        if (!isFakeObject()) return null

        val type = getFakeType(scope)
        return when {
            type.boolTypeExpr.isTrue -> extractBool(scope)
            type.fpTypeExpr.isTrue -> extractFp(scope)
            type.refTypeExpr.isTrue -> extractRef(scope)
            else -> null
        }
    }

    fun UHeapRef.unwrapRef(scope: TsStepScope): UHeapRef {
        if (isFakeObject()) {
            return extractRef(scope)
        }
        return this
    }

    fun UHeapRef.unwrapRefWithPathConstraint(scope: TsStepScope): UHeapRef {
        if (isFakeObject()) {
            scope.assert(getFakeType(scope).refTypeExpr)
            return extractRef(scope)
        }
        return this
    }

    fun createFakeObjectRef(): UConcreteHeapRef {
        val address = addressCounter.freshAllocatedAddress() + MAGIC_OFFSET
        return mkConcreteHeapRef(address)
    }

    fun getIntermediateBoolLValue(addr: Int): UFieldLValue<IntermediateLValueField, UBoolSort> {
        require(addr > MAGIC_OFFSET)
        return mkFieldLValue(boolSort, mkConcreteHeapRef(addr), IntermediateLValueField.BOOL)
    }

    fun getIntermediateFpLValue(addr: Int): UFieldLValue<IntermediateLValueField, KFp64Sort> {
        require(addr > MAGIC_OFFSET)
        return mkFieldLValue(fp64Sort, mkConcreteHeapRef(addr), IntermediateLValueField.FP)
    }

    fun getIntermediateRefLValue(addr: Int): UFieldLValue<IntermediateLValueField, UAddressSort> {
        require(addr > MAGIC_OFFSET)
        return mkFieldLValue(addressSort, mkConcreteHeapRef(addr), IntermediateLValueField.REF)
    }

    fun UConcreteHeapRef.extractBool(memory: UReadOnlyMemory<*>): UBoolExpr {
        check(isFakeObject())
        val lValue = getIntermediateBoolLValue(address)
        return memory.read(lValue)
    }

    fun UConcreteHeapRef.extractFp(memory: UReadOnlyMemory<*>): UExpr<KFp64Sort> {
        check(isFakeObject())
        val lValue = getIntermediateFpLValue(address)
        return memory.read(lValue)
    }

    fun UConcreteHeapRef.extractRef(memory: UReadOnlyMemory<*>): UHeapRef {
        check(isFakeObject())
        val lValue = getIntermediateRefLValue(address)
        return memory.read(lValue)
    }

    fun UConcreteHeapRef.extractBool(scope: TsStepScope): UBoolExpr {
        return scope.calcOnState { extractBool(memory) }
    }

    fun UConcreteHeapRef.extractFp(scope: TsStepScope): UExpr<KFp64Sort> {
        return scope.calcOnState { extractFp(memory) }
    }

    fun UConcreteHeapRef.extractRef(scope: TsStepScope): UHeapRef {
        return scope.calcOnState { extractRef(memory) }
    }

    // This is an identifier for a special function representing the 'resolve' function used in promises.
    // It is not a real function in the code, but we need it to handle promise resolution.
    val resolveFunctionRef: UConcreteHeapRef = allocateConcreteRef()

    // This is an identifier for a special function representing the 'reject' function used in promises.
    // It is not a real function in the code, but we need it to handle promise rejection.
    val rejectFunctionRef: UConcreteHeapRef = allocateConcreteRef()
}

class Constants {
    companion object {
        const val STATIC_METHODS_FORK_LIMIT = 5
        const val MAGIC_OFFSET = 1000000
    }
}

enum class IntermediateLValueField {
    BOOL, FP, REF
}
