package org.usvm.machine

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsRefType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.model.EtsScene
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UBv32Sort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.collection.field.UFieldLValue
import org.usvm.isFalse
import org.usvm.machine.expr.TSUndefinedSort
import org.usvm.machine.expr.TSUndefinedValue
import org.usvm.machine.expr.TSUnresolvedSort
import org.usvm.machine.interpreter.TSStepScope
import org.usvm.machine.types.ExprWithTypeConstraint
import org.usvm.machine.types.FakeType
import org.usvm.types.single
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

typealias TSSizeSort = UBv32Sort

class TSContext(
    val scene: EtsScene,
    components: TSComponents,
) : UContext<TSSizeSort>(components) {
    val undefinedSort: TSUndefinedSort by lazy { TSUndefinedSort(this) }

    val unresolvedSort: TSUnresolvedSort = TSUnresolvedSort(this)

    private val undefinedValue by lazy { TSUndefinedValue(this) }

    fun typeToSort(type: EtsType): USort = when (type) {
        is EtsBooleanType -> boolSort
        is EtsNumberType -> fp64Sort
        is EtsRefType -> addressSort
        is EtsUnknownType -> unresolvedSort
        else -> TODO("Support all JacoDB types, encountered $type")
    }

    @OptIn(ExperimentalContracts::class)
    fun UExpr<out USort>.isFakeObject(): Boolean {
        contract {
            returns(true) implies (this@isFakeObject is UConcreteHeapRef)
        }

        return sort == addressSort && this is UConcreteHeapRef && address > MAGIC_OFFSET
    }

    fun mkFakeValue(
        scope: TSStepScope,
        boolValue: UBoolExpr? = null,
        fpValue: UExpr<KFp64Sort>? = null,
        refValue: UHeapRef? = null,
    ): UConcreteHeapRef {
        require(boolValue != null || fpValue != null || refValue != null) {
            "Fake object should contain at least one value"
        }

        return scope.calcOnState {
            val fakeValueRef = createFakeObjectRef()
            val address = fakeValueRef.address

            val type = FakeType(
                boolTypeExpr = mkBool(boolValue != null),
                fpTypeExpr = mkBool(fpValue != null),
                refTypeExpr = mkBool(refValue != null),
            )
            memory.types.allocate(address, type)

            if (boolValue != null) {
                val boolLValue = ctx.getIntermediateBoolLValue(address)
                memory.write(boolLValue, boolValue, guard = ctx.trueExpr)
            }

            if (fpValue != null) {
                val fpLValue = ctx.getIntermediateFpLValue(address)
                memory.write(fpLValue, fpValue, guard = ctx.trueExpr)
            }

            if (refValue != null) {
                val refLValue = ctx.getIntermediateRefLValue(address)
                memory.write(refLValue, refValue, guard = ctx.trueExpr)
            }

            fakeValueRef
        }
    }

    fun createFakeObjectRef(): UConcreteHeapRef {
        val address = mkAddressCounter().freshAllocatedAddress() + MAGIC_OFFSET
        return mkConcreteHeapRef(address)
    }

    fun mkUndefinedValue(): TSUndefinedValue = undefinedValue

    fun getIntermediateBoolLValue(addr: Int): UFieldLValue<IntermediateLValueField, UBoolSort> {
        return UFieldLValue(boolSort, mkConcreteHeapRef(addr), IntermediateLValueField.BOOL)
    }

    fun getIntermediateFpLValue(addr: Int): UFieldLValue<IntermediateLValueField, KFp64Sort> {
        return UFieldLValue(fp64Sort, mkConcreteHeapRef(addr), IntermediateLValueField.FP)
    }

    fun getIntermediateRefLValue(addr: Int): UFieldLValue<IntermediateLValueField, UAddressSort> {
        return UFieldLValue(addressSort, mkConcreteHeapRef(addr), IntermediateLValueField.REF)
    }
}

const val MAGIC_OFFSET = 1000000

enum class IntermediateLValueField {
    BOOL, FP, REF
}
