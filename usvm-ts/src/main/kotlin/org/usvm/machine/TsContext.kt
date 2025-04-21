package org.usvm.machine

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UBv32Sort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.typeStreamOf
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.expr.TsUndefinedSort
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.FakeType
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

    /**
     * In TS we treat undefined value as a null reference in other objects.
     * For real null represented in the language we create another reference.
     */
    private val undefinedValue: UHeapRef = mkNullRef()
    fun mkUndefinedValue(): UHeapRef = undefinedValue

    private val nullValue: UConcreteHeapRef = mkConcreteHeapRef(addressCounter.freshStaticAddress())
    fun mkTsNullValue(): UConcreteHeapRef = nullValue

    fun typeToSort(type: EtsType): USort = when (type) {
        is EtsBooleanType -> boolSort
        is EtsNumberType -> fp64Sort
        is EtsNullType -> addressSort
        is EtsUndefinedType -> addressSort
        is EtsUnionType -> unresolvedSort
        is EtsRefType -> addressSort
        is EtsAnyType -> unresolvedSort
        is EtsUnknownType -> unresolvedSort
        else -> TODO("${type::class.simpleName} is not yet supported: $type")
    }

    // TODO: for now, ALL descriptors for array are UNKNOWN
    //  in order to make ALL reading/writing, including '.length' access consistent
    //  and possible in cases when the array type is not known.
    //  For example, when we access '.length' of some array, we do not care about its type,
    //  but we HAVE TO use some type consistent with the type used when this array was created.
    //  Note: Using UnknownType everywhere does not lead to any errors yet,
    //  since we do not rely on array types in any way.
    fun arrayDescriptorOf(type: EtsArrayType): EtsType = EtsUnknownType

    fun UConcreteHeapRef.getFakeType(memory: UReadOnlyMemory<*>): FakeType {
        check(isFakeObject())
        return memory.typeStreamOf(this).single() as FakeType
    }

    fun UConcreteHeapRef.getFakeType(scope: TsStepScope): FakeType =
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
                    memory.types.allocate(ref.address, FakeType.mkBool(this@TsContext))
                }

                fp64Sort -> {
                    val lValue = getIntermediateFpLValue(ref.address)
                    memory.write(lValue, asExpr(fp64Sort), guard = trueExpr)
                    memory.types.allocate(ref.address, FakeType.mkFp(this@TsContext))
                }

                addressSort -> {
                    val lValue = getIntermediateRefLValue(ref.address)
                    memory.write(lValue, asExpr(addressSort), guard = trueExpr)
                    memory.types.allocate(ref.address, FakeType.mkRef(this@TsContext))
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

    fun createFakeObjectRef(): UConcreteHeapRef {
        val address = mkAddressCounter().freshAllocatedAddress() + MAGIC_OFFSET
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
}

const val MAGIC_OFFSET = 1000000

enum class IntermediateLValueField {
    BOOL, FP, REF
}
