package org.usvm.ts.calls

import io.ksmt.expr.KFpRoundingMode
import io.ksmt.sort.KBv16Sort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.api.initializeArrayLength
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TsSizeSort
import org.usvm.machine.state.TsState
import org.usvm.machine.types.EtsFakeType
import org.usvm.machine.types.extractValue
import org.usvm.machine.types.mkFakeValue
import org.usvm.sizeSort
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.util.allocateBoundedString
import org.usvm.util.componentType
import org.usvm.util.mkArrayIndexLValue

internal data class MaterializedCallsInput(val value: UExpr<*>, val snapshot: CallsInputSnapshot)

internal fun TsState.materializeCallsInput(domain: PropertyDomain): MaterializedCallsInput = with(ctx) {
    when (domain) {
        BooleanDomain -> {
            val value = makeSymbolicPrimitive(boolSort)
            MaterializedCallsInput(value, BooleanInputSnapshot(value))
        }
        is IntegerDomain -> {
            val value = initializeIntegerInput(domain)
            MaterializedCallsInput(value, NumberInputSnapshot(value))
        }
        is NumberDomain -> {
            val value = initializeNumberInput(domain)
            MaterializedCallsInput(value, NumberInputSnapshot(value))
        }
        is StringDomain -> {
            val length = initializeLength(minLength = domain.minLength, maxLength = domain.maxLength)
            val codeUnits: List<UExpr<KBv16Sort>> = List(domain.maxLength) { makeSymbolicPrimitive(bv16Sort) }
            val reference = allocateBoundedString(length = length, codeUnits = codeUnits)
            MaterializedCallsInput(reference, StringInputSnapshot(length = length, codeUnits = codeUnits))
        }
        is ConstantDomain -> MaterializedCallsInput(
            value = materializeCallsConstant(domain.value),
            snapshot = ConstantInputSnapshot(domain.value),
        )
        is OptionalDomain -> materializeCallsOptional(domain)
        is ArrayDomain -> materializeCallsArray(domain)
        else -> error("Unsupported Calls input domain: $domain")
    }
}

private fun TsState.initializeIntegerInput(domain: IntegerDomain): UExpr<KFp64Sort> = with(ctx) {
    if (domain.min == domain.max) return mkFp64(domain.min.toDouble())

    val value: UExpr<KFp64Sort> = makeSymbolicPrimitive(fp64Sort)
    val rounded = mkFpRoundToIntegralExpr(
        roundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero),
        value = value,
    )
    val negativeZero = mkAnd(mkFpIsZeroExpr(value), mkFpIsNegativeExpr(value))
    pathConstraints += mkAnd(
        mkFpEqualExpr(value, rounded),
        mkNot(negativeZero),
        mkFpLessOrEqualExpr(mkFp64(domain.min.toDouble()), value),
        mkFpLessOrEqualExpr(value, mkFp64(domain.max.toDouble())),
    )
    value
}

private fun TsState.materializeCallsConstant(value: JsConcreteValue): UExpr<*> = with(ctx) {
    when (value) {
        is JsConcreteValue.Boolean -> mkBool(value.value)
        is JsConcreteValue.Number -> mkFp64(value.toDouble())
        is JsConcreteValue.String -> mkInitializedStringConstant(value.value)
        JsConcreteValue.Null -> mkTsNullValue()
        JsConcreteValue.Undefined -> mkUndefinedValue()
        is JsConcreteValue.Array -> error("Array constants are not primitive domains")
    }
}

private fun TsState.materializeCallsOptional(domain: OptionalDomain): MaterializedCallsInput = with(ctx) {
    val nested = materializeCallsInput(domain.value)
    val nil = materializeCallsConstant(domain.nil)
    val chooseValue: UBoolExpr = makeSymbolicPrimitive(boolSort)
    val nestedBool = extractValue(nested.value, boolSort, ::getIntermediateBoolLValue)
    val nestedNumber = extractValue(nested.value, fp64Sort, ::getIntermediateFpLValue)
    val nestedReference = extractValue(nested.value, addressSort, ::getIntermediateRefLValue)
    val reference = mkIte(chooseValue, nestedReference.first ?: mkUndefinedValue(), nil.asExpr(addressSort))
    val valueType = EtsFakeType(
        boolTypeExpr = mkAnd(chooseValue, nestedBool.second),
        fpTypeExpr = mkAnd(chooseValue, nestedNumber.second),
        refTypeExpr = mkOr(mkNot(chooseValue), mkAnd(chooseValue, nestedReference.second)),
    )
    val value = mkFakeValue(
        scope = null,
        boolValue = nestedBool.first ?: falseExpr,
        fpValue = nestedNumber.first ?: mkFp64(0.0),
        refValue = reference,
        valueType = valueType,
    )
    MaterializedCallsInput(value, OptionalInputSnapshot(chooseValue, nested.snapshot, domain.nil))
}

private fun PropertyDomain.callsRuntimeType(): EtsType = when (this) {
    BooleanDomain -> EtsBooleanType
    is IntegerDomain, is NumberDomain -> EtsNumberType
    is StringDomain -> EtsStringType
    is ArrayDomain -> {
        val nestedType = element.callsRuntimeType()
        if (nestedType is EtsArrayType) {
            nestedType.copy(dimensions = nestedType.dimensions + 1)
        } else {
            EtsArrayType(elementType = nestedType, dimensions = 1)
        }
    }
    is ConstantDomain -> when (value) {
        is JsConcreteValue.Boolean -> EtsBooleanType
        is JsConcreteValue.Number -> EtsNumberType
        is JsConcreteValue.String -> EtsStringType
        else -> error("Array elements require a concrete runtime kind")
    }
    else -> error("Unsupported array element domain: $this")
}

private fun TsState.materializeCallsArray(domain: ArrayDomain): MaterializedCallsInput = with(ctx) {
    val runtimeType = domain.callsRuntimeType() as EtsArrayType
    val descriptor = arrayDescriptorOf(runtimeType)
    val arrayRef = memory.allocConcrete(runtimeType)
    val length = initializeLength(minLength = domain.minLength, maxLength = domain.maxLength)
    memory.initializeArrayLength(
        arrayHeapRef = arrayRef,
        type = descriptor,
        sizeSort = sizeSort,
        count = length,
    )
    val elementSort = typeToSort(runtimeType.componentType)
    val elements = List(domain.maxLength) { index ->
        val element = materializeCallsInput(domain.element)
        val liveIndex = mkBvSignedLessExpr(mkBv(index), length)
        when (elementSort) {
            boolSort -> memory.write(
                mkArrayIndexLValue(boolSort, arrayRef, mkBv(index), runtimeType),
                element.value.asExpr(boolSort),
                guard = liveIndex,
            )
            fp64Sort -> memory.write(
                mkArrayIndexLValue(fp64Sort, arrayRef, mkBv(index), runtimeType),
                element.value.asExpr(fp64Sort),
                guard = liveIndex,
            )
            addressSort -> memory.write(
                mkArrayIndexLValue(addressSort, arrayRef, mkBv(index), runtimeType),
                element.value.asExpr(addressSort),
                guard = liveIndex,
            )
            else -> error("Unsupported Calls element sort: $elementSort")
        }
        element.snapshot
    }
    val snapshot = ArrayInputSnapshot(
        length = length,
        elements = elements,
        reference = arrayRef,
        runtimeType = runtimeType,
    )
    MaterializedCallsInput(arrayRef, snapshot)
}

private fun TsState.initializeLength(
    minLength: Int,
    maxLength: Int,
): UExpr<TsSizeSort> = with(ctx) {
    if (minLength == maxLength) return mkBv(minLength)

    val length: UExpr<TsSizeSort> = makeSymbolicPrimitive(sizeSort)
    val lengthBounds = mkAnd(
        mkBvSignedGreaterOrEqualExpr(length, mkBv(minLength)),
        mkBvSignedLessOrEqualExpr(length, mkBv(maxLength)),
    )
    pathConstraints += lengthBounds
    length
}

private fun TsState.initializeNumberInput(domain: NumberDomain): UExpr<KFp64Sort> = with(ctx) {
    val lowerBound = domain.min.toDouble()
    val upperBound = domain.max.toDouble()
    val hasSingleNumericValue = lowerBound == upperBound && !domain.allowNaN
    if (hasSingleNumericValue) {
        // Numeric equality admits both signs of zero. Keep that choice symbolic even for [0, 0].
        return if (lowerBound == 0.0) {
            val positiveSign: UBoolExpr = makeSymbolicPrimitive(boolSort)
            val positiveZero = mkFp64(0.0)
            val negativeZero = mkFpNegationExpr(positiveZero)
            mkIte(positiveSign, positiveZero, negativeZero)
        } else {
            mkFp64(lowerBound)
        }
    }

    val value: UExpr<KFp64Sort> = makeSymbolicPrimitive(fp64Sort)
    pathConstraints += numberDomainConstraint(value, domain)
    value
}

private fun TsState.numberDomainConstraint(
    value: UExpr<KFp64Sort>,
    domain: NumberDomain,
): UBoolExpr = with(ctx) {
    val lowerBound = mkFp64(domain.min.toDouble())
    val upperBound = mkFp64(domain.max.toDouble())
    val inBounds = mkAnd(
        mkFpLessOrEqualExpr(lowerBound, value),
        mkFpLessOrEqualExpr(value, upperBound),
    )

    if (domain.allowNaN) mkOr(mkFpIsNaNExpr(value), inBounds) else inBounds
}
