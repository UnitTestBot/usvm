package org.usvm.machine

import io.ksmt.sort.KFp64Sort
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsRefType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.model.EtsScene
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UBv32Sort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.USymbolicHeapRef
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.expr.TSNullRefExpr
import org.usvm.machine.expr.TSUnresolvedSort
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

typealias TSSizeSort = UBv32Sort

class TSContext(
    val scene: EtsScene,
    components: TSComponents,
) : UContext<TSSizeSort>(components) {
    val unresolvedSort: TSUnresolvedSort = TSUnresolvedSort(this)

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
        is EtsUndefinedType -> addressSort
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

    fun createFakeObjectRef(): UConcreteHeapRef {
        val address = mkAddressCounter().freshAllocatedAddress() + MAGIC_OFFSET
        return mkConcreteHeapRef(address)
    }

    fun mkUndefinedValue(): UExpr<UAddressSort> = undefinedValue

    fun mkTSNullRefValue(): USymbolicHeapRef = TSNullRefExpr(this)

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
