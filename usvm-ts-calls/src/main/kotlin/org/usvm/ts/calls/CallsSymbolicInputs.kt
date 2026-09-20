package org.usvm.ts.calls

import io.ksmt.expr.KBitVec16Value
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
import org.usvm.USort
import org.usvm.api.initializeArrayLength
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.extractInt
import org.usvm.machine.expr.toConcreteBoolValue
import org.usvm.machine.state.TsState
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.ts.pbt.mapping.EtsInputBinding
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.util.markDenseInputArray
import org.usvm.util.markStringMaxLength
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue

internal fun PropertyDomain.isSupportedCallsSymbolicDomain(): Boolean = when (this) {
    BooleanDomain, is NumberDomain, is StringDomain -> true
    is ArrayDomain -> element == BooleanDomain || element is NumberDomain
    else -> false
}

internal fun callsSymbolicInputPreflight(inputs: List<PropertyInput>): String? {
    val unsupportedInput = inputs.firstOrNull { input -> !input.domain.isSupportedCallsSymbolicDomain() }
        ?: return null

    return "Unsupported symbolic input domain for ${unsupportedInput.name}: ${unsupportedInput.domain}"
}

internal class CallsSymbolicInputs(
    inputs: List<PropertyInput>,
    bindings: List<EtsInputBinding>,
) {
    private val boundInputs: List<Pair<PropertyInput, EtsInputBinding>>
    private var snapshots: List<CallsInputSnapshot>? = null

    init {
        require(inputs.size == bindings.size) { "Property inputs do not match mapped EtsIR bindings" }
        val bindingsByName = bindings.associateBy(EtsInputBinding::propertyInputName)
        require(bindingsByName.size == bindings.size) { "Mapped EtsIR input names must be unique" }

        boundInputs = inputs.map { input ->
            input to requireNotNull(bindingsByName[input.name]) {
                "Missing EtsIR binding for property input ${input.name}"
            }
        }
    }

    fun initialize(state: TsState) {
        check(snapshots == null) { "Calls symbolic inputs were initialized more than once" }
        snapshots = boundInputs.map { (input, binding) ->
            state.initializeInput(
                stackSlot = binding.stackSlot,
                domain = input.domain,
            )
        }
    }

    fun sortOverride(ctx: TsContext, stackSlot: Int): USort? {
        val domain = boundInputs.singleOrNull { (_, binding) -> binding.stackSlot == stackSlot }?.first?.domain
            ?: return null

        return with(ctx) {
            when (domain) {
                BooleanDomain -> boolSort
                is NumberDomain -> fp64Sort
                is StringDomain, is ArrayDomain -> addressSort
                else -> null
            }
        }
    }

    fun resolve(state: TsState): List<JsConcreteValue> {
        val initializedSnapshots = checkNotNull(snapshots) { "Calls symbolic inputs were not initialized" }
        val model = state.models.single()

        return initializedSnapshots.map { snapshot -> snapshot.resolve(model) }
    }
}

private fun TsState.initializeInput(
    stackSlot: Int,
    domain: PropertyDomain,
): CallsInputSnapshot = with(ctx) {
    when (domain) {
        BooleanDomain -> {
            val value: UBoolExpr = makeSymbolicPrimitive(boolSort)
            memory.write(
                mkRegisterStackLValue(boolSort, stackSlot),
                value.asExpr(boolSort),
                guard = trueExpr,
            )
            saveSortForLocal(stackSlot, boolSort)
            BooleanInputSnapshot(value)
        }

        is NumberDomain -> {
            val value: UExpr<KFp64Sort> = makeSymbolicPrimitive(fp64Sort)
            pathConstraints += numberDomainConstraint(value, domain)
            memory.write(
                mkRegisterStackLValue(fp64Sort, stackSlot),
                value.asExpr(fp64Sort),
                guard = trueExpr,
            )
            saveSortForLocal(stackSlot, fp64Sort)
            NumberInputSnapshot(value)
        }

        is StringDomain -> {
            initializeStringInput(stackSlot = stackSlot, domain = domain)
        }

        is ArrayDomain -> {
            initializeArrayInput(stackSlot = stackSlot, domain = domain)
        }

        else -> {
            error("Unsupported calls symbolic domain: $domain")
        }
    }
}

private fun TsState.initializeStringInput(
    stackSlot: Int,
    domain: StringDomain,
): StringInputSnapshot = with(ctx) {
    val stringRef = memory.allocConcrete(EtsStringType)
    val characterArrayType = EtsArrayType(EtsNumberType, dimensions = 1)
    val descriptor = arrayDescriptorOf(characterArrayType)
    val charactersRef = memory.allocConcrete(descriptor)
    val length: UExpr<TsSizeSort> = makeSymbolicPrimitive(sizeSort)
    val codeUnits: List<UExpr<KBv16Sort>> = List(domain.maxLength) { makeSymbolicPrimitive(bv16Sort) }

    constrainLength(length = length, minLength = domain.minLength, maxLength = domain.maxLength)
    memory.initializeArrayLength(
        arrayHeapRef = charactersRef,
        type = descriptor,
        sizeSort = sizeSort,
        count = length,
    )
    codeUnits.forEachIndexed { index, codeUnit ->
        val liveIndex = mkBvSignedLessExpr(mkBv(index), length)
        memory.write(
            mkArrayIndexLValue(
                sort = bv16Sort,
                ref = charactersRef,
                index = mkBv(index),
                type = characterArrayType,
            ),
            codeUnit,
            guard = liveIndex,
        )
    }
    memory.write(
        mkFieldLValue(addressSort, stringRef, "value"),
        charactersRef.asExpr(addressSort),
        guard = trueExpr,
    )
    markStringMaxLength(string = stringRef, maxLength = domain.maxLength)
    memory.write(
        mkRegisterStackLValue(addressSort, stackSlot),
        stringRef.asExpr(addressSort),
        guard = trueExpr,
    )
    saveSortForLocal(stackSlot, addressSort)

    StringInputSnapshot(length = length, codeUnits = codeUnits)
}

private fun TsState.initializeArrayInput(
    stackSlot: Int,
    domain: ArrayDomain,
): ArrayInputSnapshot = with(ctx) {
    val runtimeType = when (domain.element) {
        BooleanDomain -> EtsArrayType(EtsBooleanType, dimensions = 1)
        is NumberDomain -> EtsArrayType(EtsNumberType, dimensions = 1)
        else -> error("Unsupported calls symbolic array element domain: ${domain.element}")
    }
    val descriptor = arrayDescriptorOf(runtimeType)
    val arrayRef = memory.allocConcrete(descriptor)
    val length: UExpr<TsSizeSort> = makeSymbolicPrimitive(sizeSort)

    constrainLength(length = length, minLength = domain.minLength, maxLength = domain.maxLength)
    memory.initializeArrayLength(
        arrayHeapRef = arrayRef,
        type = descriptor,
        sizeSort = sizeSort,
        count = length,
    )
    val elements = when (val elementDomain = domain.element) {
        BooleanDomain -> List(domain.maxLength) { index ->
            val value = makeSymbolicPrimitive(boolSort)
            val liveIndex = mkBvSignedLessExpr(mkBv(index), length)
            memory.write(
                mkArrayIndexLValue(boolSort, arrayRef, mkBv(index), runtimeType),
                value,
                guard = liveIndex,
            )
            BooleanInputSnapshot(value)
        }

        is NumberDomain -> List(domain.maxLength) { index ->
            val value = makeSymbolicPrimitive(fp64Sort)
            val liveIndex = mkBvSignedLessExpr(mkBv(index), length)
            pathConstraints += numberDomainConstraint(value, elementDomain)
            memory.write(
                mkArrayIndexLValue(fp64Sort, arrayRef, mkBv(index), runtimeType),
                value,
                guard = liveIndex,
            )
            NumberInputSnapshot(value)
        }

        else -> error("Unsupported calls symbolic array element domain: $elementDomain")
    }
    memory.write(
        mkRegisterStackLValue(addressSort, stackSlot),
        arrayRef.asExpr(addressSort),
        guard = trueExpr,
    )
    saveSortForLocal(stackSlot, addressSort)
    markDenseInputArray(array = arrayRef, type = runtimeType)

    ArrayInputSnapshot(length = length, elements = elements)
}

private fun TsState.constrainLength(
    length: UExpr<TsSizeSort>,
    minLength: Int,
    maxLength: Int,
) = with(ctx) {
    pathConstraints += mkBvSignedGreaterOrEqualExpr(length, mkBv(minLength))
    pathConstraints += mkBvSignedLessOrEqualExpr(length, mkBv(maxLength))
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

private sealed interface CallsInputSnapshot {
    fun resolve(model: UModelBase<EtsType>): JsConcreteValue
}

private data class BooleanInputSnapshot(
    val value: UBoolExpr,
) : CallsInputSnapshot {
    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue =
        JsConcreteValue.Boolean(model.eval(value).toConcreteBoolValue())
}

private data class NumberInputSnapshot(
    val value: UExpr<KFp64Sort>,
) : CallsInputSnapshot {
    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue =
        JsConcreteValue.number(model.eval(value).extractDouble())
}

private data class StringInputSnapshot(
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

private data class ArrayInputSnapshot(
    val length: UExpr<TsSizeSort>,
    val elements: List<CallsInputSnapshot>,
) : CallsInputSnapshot {
    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue {
        val concreteLength = model.eval(length).extractInt()
        require(concreteLength in 0..elements.size) { "Resolved array length is outside its symbolic domain" }

        return JsConcreteValue.Array(elements.take(concreteLength).map { element -> element.resolve(model) })
    }
}

private const val UTF16_CODE_UNIT_MASK = 0xffff
