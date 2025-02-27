package org.usvm.machine

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNullType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsRefType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsUnionType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.model.EtsScene
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UBv32Sort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.expr.TsUndefinedSort
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.FakeType
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
    private val undefinedValue: UExpr<UAddressSort>
        get() = mkNullRef()

    fun typeToSort(type: EtsType): USort = when (type) {
        is EtsBooleanType -> boolSort
        is EtsNumberType -> fp64Sort
        is EtsRefType -> addressSort
        is EtsNullType -> addressSort
        is EtsUndefinedType -> addressSort
        is EtsUnknownType -> unresolvedSort
        is EtsUnionType -> unresolvedSort
        is EtsAnyType -> unresolvedSort
        else -> TODO("Support all JacoDB types, encountered $type")
    }

    @OptIn(ExperimentalContracts::class)
    fun UExpr<out USort>.isFakeObject(): Boolean {
        contract {
            returns(true) implies (this@isFakeObject is UConcreteHeapRef)
        }

        return sort == addressSort && this is UConcreteHeapRef && address > MAGIC_OFFSET
    }

    fun UExpr<out USort>.toFakeObject(scope: TsStepScope): UConcreteHeapRef {
        if (isFakeObject()) {
            return this
        }

        val ref = createFakeObjectRef()

        scope.doWithState {
            when (sort) {
                boolSort -> {
                    val lvalue = getIntermediateBoolLValue(ref.address)
                    memory.write(lvalue, this@toFakeObject.asExpr(boolSort), guard = trueExpr)
                    memory.types.allocate(ref.address, FakeType.fromBool(this@TsContext))
                }

                fp64Sort -> {
                    val lValue = getIntermediateFpLValue(ref.address)
                    memory.write(lValue, this@toFakeObject.asExpr(fp64Sort), guard = trueExpr)
                    memory.types.allocate(ref.address, FakeType.fromFp(this@TsContext))
                }

                addressSort -> {
                    val lValue = getIntermediateRefLValue(ref.address)
                    memory.write(lValue, this@toFakeObject.asExpr(addressSort), guard = trueExpr)
                    memory.types.allocate(ref.address, FakeType.fromRef(this@TsContext))
                }

                else -> TODO("Not yet supported")
            }
        }

        return ref
    }

    fun UExpr<out USort>.extractSingleValueFromFakeObjectOrNull(scope: TsStepScope): UExpr<out USort>? {
        if (!isFakeObject()) return null

        val type = scope.calcOnState {
            memory.types.getTypeStream(this@extractSingleValueFromFakeObjectOrNull).single() as FakeType
        }

        return scope.calcOnState {
            when {
                type.boolTypeExpr.isTrue -> {
                    val lValue = getIntermediateBoolLValue(address)
                    memory.read(lValue).asExpr(boolSort)
                }

                type.fpTypeExpr.isTrue -> {
                    val lValue = getIntermediateFpLValue(address)
                    memory.read(lValue).asExpr(fp64Sort)
                }

                type.refTypeExpr.isTrue -> {
                    val lValue = getIntermediateRefLValue(address)
                    memory.read(lValue).asExpr(addressSort)
                }

                else -> null
            }
        }
    }

    fun createFakeObjectRef(): UConcreteHeapRef {
        val address = mkAddressCounter().freshAllocatedAddress() + MAGIC_OFFSET
        return mkConcreteHeapRef(address)
    }

    fun mkUndefinedValue(): UExpr<UAddressSort> = undefinedValue

    fun mkTsNullValue(): UConcreteHeapRef = nullValue
    private val nullValue: UConcreteHeapRef = mkConcreteHeapRef(addressCounter.freshStaticAddress())

    fun getIntermediateBoolLValue(addr: Int): UFieldLValue<IntermediateLValueField, UBoolSort> {
        return mkFieldLValue(boolSort, mkConcreteHeapRef(addr), IntermediateLValueField.BOOL)
    }

    fun getIntermediateFpLValue(addr: Int): UFieldLValue<IntermediateLValueField, KFp64Sort> {
        return mkFieldLValue(fp64Sort, mkConcreteHeapRef(addr), IntermediateLValueField.FP)
    }

    fun getIntermediateRefLValue(addr: Int): UFieldLValue<IntermediateLValueField, UAddressSort> {
        return mkFieldLValue(addressSort, mkConcreteHeapRef(addr), IntermediateLValueField.REF)
    }
}

const val MAGIC_OFFSET = 1000000

enum class IntermediateLValueField {
    BOOL, FP, REF
}
