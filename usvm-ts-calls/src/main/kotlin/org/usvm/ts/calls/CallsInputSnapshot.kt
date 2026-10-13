package org.usvm.ts.calls

import io.ksmt.expr.KBitVec16Value
import io.ksmt.sort.KBv16Sort
import io.ksmt.sort.KFp64Sort
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.extractInt
import org.usvm.machine.expr.toConcreteBoolValue
import org.usvm.machine.state.TsState
import org.usvm.model.UModelBase
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.util.componentType
import org.usvm.util.markDenseArrayOwnSlots
import org.usvm.util.markDenseInputArray

internal sealed interface CallsInputSnapshot {
    fun resolve(model: UModelBase<EtsType>): JsConcreteValue

    fun markDenseInputs(state: TsState) = Unit
}

internal data class BooleanInputSnapshot(
    val value: UBoolExpr,
) : CallsInputSnapshot {
    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue =
        JsConcreteValue.Boolean(model.eval(value).toConcreteBoolValue())
}

internal data class NumberInputSnapshot(
    val value: UExpr<KFp64Sort>,
) : CallsInputSnapshot {
    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue =
        JsConcreteValue.number(model.eval(value).extractDouble())
}

internal data class StringInputSnapshot(
    val length: UExpr<TsSizeSort>,
    val codeUnits: List<UExpr<KBv16Sort>>,
) : CallsInputSnapshot {
    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue {
        val concreteLength = model.eval(length).extractInt()
        require(concreteLength in 0..codeUnits.size) { "Resolved string length is outside its symbolic domain" }
        val value = buildString(concreteLength) {
            codeUnits.take(concreteLength).forEach { codeUnit ->
                val concreteCodeUnit = model.eval(codeUnit) as KBitVec16Value
                append((concreteCodeUnit.shortValue.toInt() and UTF16_CODE_UNIT_MASK).toChar())
            }
        }

        return JsConcreteValue.String(value)
    }
}

internal data class ArrayInputSnapshot(
    val length: UExpr<TsSizeSort>,
    val elements: List<CallsInputSnapshot>,
    val reference: UConcreteHeapRef,
    val runtimeType: EtsArrayType,
) : CallsInputSnapshot {
    override fun markDenseInputs(state: TsState) {
        elements.forEach { element -> element.markDenseInputs(state) }
        state.markDenseArrayOwnSlots(array = reference, length = length, capacity = elements.size)

        val elementSort = state.ctx.typeToSort(runtimeType.componentType)
        if (elementSort !is TsUnresolvedSort) {
            state.markDenseInputArray(array = reference, type = runtimeType)
        }
    }

    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue {
        val concreteLength = model.eval(length).extractInt()
        require(concreteLength in 0..elements.size) { "Resolved array length is outside its symbolic domain" }

        return JsConcreteValue.Array(elements.take(concreteLength).map { element -> element.resolve(model) })
    }
}

internal data class ObjectInputSnapshot(
    val fields: Map<String, CallsInputSnapshot>,
) : CallsInputSnapshot {
    override fun markDenseInputs(state: TsState) {
        fields.values.forEach { field -> field.markDenseInputs(state) }
    }

    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue =
        JsConcreteValue.Object(fields.mapValues { (_, snapshot) -> snapshot.resolve(model) })
}

internal data class ConstantInputSnapshot(val value: JsConcreteValue) : CallsInputSnapshot {
    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue = value
}

internal data class OptionalInputSnapshot(
    val chooseValue: UBoolExpr,
    val nested: CallsInputSnapshot,
    val nil: JsConcreteValue,
) : CallsInputSnapshot {
    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue =
        if (model.eval(chooseValue).toConcreteBoolValue()) nested.resolve(model) else nil

    override fun markDenseInputs(state: TsState) = nested.markDenseInputs(state)
}

private const val UTF16_CODE_UNIT_MASK = 0xffff
