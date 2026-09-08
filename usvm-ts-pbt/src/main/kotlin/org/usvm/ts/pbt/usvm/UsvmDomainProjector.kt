package org.usvm.ts.pbt.usvm

import io.ksmt.expr.KFpRoundingMode
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.initializeArrayLength
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.state.TsState
import org.usvm.machine.types.mkFakeValue
import org.usvm.sizeSort
import org.usvm.ts.pbt.mapping.EtsInputBinding
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TupleDomain
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkRegisterStackLValue

/** One symbolic input written to the mapped EtsIR stack slot. */
data class UsvmProjectedInput(
    val inputName: String,
    val path: String,
    val stackSlot: Int,
    val etsType: EtsType,
    val value: UExpr<out USort>,
)

/** Constraints created exclusively from declared Kotlin property domains. */
data class UsvmDeclaredDomainProjection(
    val inputs: List<UsvmProjectedInput>,
    val initialState: TsState,
)

/** Materializes declared property domains in a real USVM TypeScript initial state. */
class UsvmDomainProjector(
    private val options: UsvmProjectionOptions = UsvmProjectionOptions(),
) {
    fun configure(
        state: TsState,
        inputs: List<PropertyInput>,
        bindings: List<EtsInputBinding>,
    ): UsvmDeclaredDomainProjection {
        require(inputs.size == bindings.size) {
            "Property input count ${inputs.size} does not match EtsIR binding count ${bindings.size}"
        }

        val preparedInputs = inputs.zip(bindings).mapIndexed { index, (input, binding) ->
            require(input.name == binding.propertyInputName) {
                "Property input ${input.name} does not match EtsIR binding ${binding.propertyInputName}"
            }

            val path = "inputs[$index].domain"
            val capability = UsvmProjectionCapabilityResolver().domainCapabilityForProjector(
                domain = input.domain,
                etsType = binding.parameter.type,
                path = path,
                options = options,
            )

            PreparedInput(input, binding, path, capability)
        }
        preparedInputs.forEach { prepared ->
            require(prepared.capability.level != org.usvm.ts.pbt.backend.ProjectionLevel.UNSUPPORTED) {
                prepared.capability.diagnostics.joinToString { diagnostic -> diagnostic.message }
            }
        }

        val projectedInputs = preparedInputs.map { prepared ->
            val input = prepared.input
            val binding = prepared.binding
            val path = prepared.path

            val value = Materializer(state).materialize(input.domain, binding.parameter.type)
            writeStackValue(state, binding.stackSlot, value)

            UsvmProjectedInput(
                inputName = input.name,
                path = path,
                stackSlot = binding.stackSlot,
                etsType = binding.parameter.type,
                value = value,
            )
        }

        return UsvmDeclaredDomainProjection(
            inputs = projectedInputs,
            initialState = state.clone(),
        )
    }

    private data class PreparedInput(
        val input: PropertyInput,
        val binding: EtsInputBinding,
        val path: String,
        val capability: org.usvm.ts.pbt.backend.ProjectionCapability,
    )

    private inner class Materializer(private val state: TsState) {
        fun materialize(domain: PropertyDomain, etsType: EtsType): UExpr<out USort> = when (domain) {
            BooleanDomain -> state.makeSymbolicPrimitive(state.ctx.boolSort)
            is IntegerDomain -> materializeInteger(domain)
            is NumberDomain -> materializeNumber(domain)
            is StringDomain -> materializeString(domain)
            is ConstantDomain -> materializeConstant(domain.value)
            is OptionalDomain -> materializeOptional(domain, etsType)
            is TupleDomain -> materializeTuple(domain, etsType)
            is ArrayDomain -> materializeArray(domain, etsType as EtsArrayType)
        }

        private fun materializeInteger(domain: IntegerDomain): UExpr<KFp64Sort> = with(state.ctx) {
            val value = state.makeSymbolicPrimitive(fp64Sort)
            val rounded = mkFpRoundToIntegralExpr(
                roundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardZero),
                value = value,
            )
            val negativeZero = mkAnd(mkFpIsZeroExpr(value), mkFpIsNegativeExpr(value))
            val minimum = mkFp(domain.min.toDouble(), fp64Sort)
            val maximum = mkFp(domain.max.toDouble(), fp64Sort)
            val isNumber = mkFpIsNaNExpr(value).not()
            val isIntegral = mkFpEqualExpr(value, rounded)
            val meetsMinimum = mkFpGreaterOrEqualExpr(value, minimum)
            val meetsMaximum = mkFpLessOrEqualExpr(value, maximum)

            state.pathConstraints += mkAnd(
                isNumber,
                isIntegral,
                negativeZero.not(),
                meetsMinimum,
                meetsMaximum,
            )

            value
        }

        private fun materializeNumber(domain: NumberDomain): UExpr<KFp64Sort> = with(state.ctx) {
            val value = state.makeSymbolicPrimitive(fp64Sort)
            val minimum = mkFp(domain.min.toDouble(), fp64Sort)
            val maximum = mkFp(domain.max.toDouble(), fp64Sort)
            val meetsMinimum = mkFpGreaterOrEqualExpr(value, minimum)
            val meetsMaximum = mkFpLessOrEqualExpr(value, maximum)
            val insideBounds = mkAnd(meetsMinimum, meetsMaximum)
            val constraint = if (domain.allowNaN) {
                mkOr(mkFpIsNaNExpr(value), insideBounds)
            } else {
                insideBounds
            }

            state.pathConstraints += constraint

            value
        }

        private fun materializeString(domain: StringDomain): UConcreteHeapRef = with(state.ctx) {
            val value = state.memory.allocConcrete(EtsStringType)
            val stringArrayType = EtsArrayType(EtsStringType, dimensions = 1)
            val descriptor = arrayDescriptorOf(stringArrayType)
            val length = state.makeSymbolicPrimitive(sizeSort)
            val minimumLength = mkBv(domain.minLength)
            val maximumLength = mkBv(domain.maxLength)
            val meetsMinimum = mkBvSignedGreaterOrEqualExpr(length, minimumLength)
            val meetsMaximum = mkBvSignedLessOrEqualExpr(length, maximumLength)

            state.memory.initializeArrayLength(value, descriptor, sizeSort, length)
            state.pathConstraints += mkAnd(meetsMinimum, meetsMaximum)

            value
        }

        private fun materializeConstant(value: JsConcreteValue): UExpr<out USort> = with(state.ctx) {
            when (value) {
                is JsConcreteValue.Boolean -> mkBool(value.value)
                is JsConcreteValue.Number -> mkFp(value.toDouble(), fp64Sort)
                is JsConcreteValue.String -> state.mkInitializedStringConstant(value.value)
                JsConcreteValue.Null -> mkTsNullValue()
                JsConcreteValue.Undefined -> mkUndefinedValue()
                is JsConcreteValue.Array -> error("Array constants are not valid property-domain primitives")
            }
        }

        private fun materializeOptional(
            domain: OptionalDomain,
            etsType: EtsType,
        ): UExpr<out USort> = with(state.ctx) {
            val unionType = etsType as EtsUnionType
            val nestedType = unionType.types.first { type ->
                UsvmProjectionCapabilityResolver().domainCompatibilityForProjector(domain.value, type)
            }
            val nestedValue = materialize(domain.value, nestedType)
            val nilValue = materializeConstant(domain.nil)
            val chooseValue = state.makeSymbolicPrimitive(boolSort)

            if (nestedValue.sort == nilValue.sort) {
                return@with sameSortIte(chooseValue, nestedValue, nilValue)
            }

            val fakeValue = state.mkFakeValue(
                scope = null,
                boolValue = nestedValue.asOptionalBool(),
                fpValue = nestedValue.asOptionalFp(),
                refValue = (nestedValue.asOptionalRef() ?: nilValue.asOptionalRef()),
            )
            val fakeType = fakeValue.getFakeType(state.memory)
            val nestedTypeExpr = when (nestedValue.sort) {
                boolSort -> fakeType.boolTypeExpr
                fp64Sort -> fakeType.fpTypeExpr
                addressSort -> fakeType.refTypeExpr
                else -> error("Unsupported optional value sort ${nestedValue.sort}")
            }
            val nilTypeExpr = when (nilValue.sort) {
                boolSort -> fakeType.boolTypeExpr
                fp64Sort -> fakeType.fpTypeExpr
                addressSort -> fakeType.refTypeExpr
                else -> error("Unsupported optional nil sort ${nilValue.sort}")
            }

            state.pathConstraints += mkEq(nestedTypeExpr, chooseValue)
            state.pathConstraints += mkEq(nilTypeExpr, chooseValue.not())

            fakeValue
        }

        private fun materializeTuple(
            domain: TupleDomain,
            etsType: EtsType,
        ): UConcreteHeapRef = with(state.ctx) {
            val elementTypes = when (etsType) {
                is EtsTupleType -> etsType.types
                is EtsArrayType -> List(domain.elements.size) { etsType.elementType }
                else -> error("Unsupported tuple EtsIR type $etsType")
            }
            val arrayType = EtsArrayType(EtsUnknownType, dimensions = 1)
            val descriptor = arrayDescriptorOf(arrayType)
            val array = state.memory.allocConcrete(descriptor)

            state.memory.initializeArrayLength(array, descriptor, sizeSort, mkBv(domain.elements.size))
            domain.elements.zip(elementTypes).forEachIndexed { index, (elementDomain, elementType) ->
                val element = box(materialize(elementDomain, elementType))
                val lValue = mkArrayIndexLValue(
                    sort = addressSort,
                    ref = array,
                    index = mkBv(index),
                    type = arrayType,
                )

                state.memory.write(lValue, element, guard = trueExpr)
            }

            array
        }

        private fun materializeArray(
            domain: ArrayDomain,
            etsType: EtsArrayType,
        ): UConcreteHeapRef = with(state.ctx) {
            val descriptor = arrayDescriptorOf(etsType)
            val array = state.memory.allocConcrete(descriptor)
            val length = state.makeSymbolicPrimitive(sizeSort)
            val minimumLength = mkBv(domain.minLength)
            val maximumLength = mkBv(domain.maxLength)
            val meetsMinimum = mkBvSignedGreaterOrEqualExpr(length, minimumLength)
            val meetsMaximum = mkBvSignedLessOrEqualExpr(length, maximumLength)

            state.memory.initializeArrayLength(array, descriptor, sizeSort, length)
            state.pathConstraints += mkAnd(meetsMinimum, meetsMaximum)

            repeat(domain.maxLength) { index ->
                val element = materialize(domain.element, etsType.elementType)
                val guard = mkBvSignedLessExpr(mkBv(index), length)

                writeArrayElement(array, etsType, index, element, guard)
            }

            array
        }

        private fun writeArrayElement(
            array: UConcreteHeapRef,
            arrayType: EtsArrayType,
            index: Int,
            value: UExpr<out USort>,
            guard: UBoolExpr,
        ) = with(state.ctx) {
            val descriptor = arrayDescriptorOf(arrayType) as EtsArrayType
            val elementSort = typeToSort(descriptor.elementType)
            if (elementSort is TsUnresolvedSort) {
                val lValue = mkArrayIndexLValue(
                    sort = addressSort,
                    ref = array,
                    index = mkBv(index),
                    type = arrayType,
                )

                state.memory.write(lValue, box(value), guard)
            } else {
                writeArrayElementWithKnownSort(array, arrayType, index, value, guard, elementSort)
            }
        }

        private fun writeArrayElementWithKnownSort(
            array: UConcreteHeapRef,
            arrayType: EtsArrayType,
            index: Int,
            value: UExpr<out USort>,
            guard: UBoolExpr,
            sort: USort,
        ) = with(state.ctx) {
            when (sort) {
                boolSort -> {
                    val lValue = mkArrayIndexLValue(boolSort, array, mkBv(index), arrayType)

                    state.memory.write(lValue, value.asExpr(boolSort), guard)
                }

                fp64Sort -> {
                    val lValue = mkArrayIndexLValue(fp64Sort, array, mkBv(index), arrayType)

                    state.memory.write(lValue, value.asExpr(fp64Sort), guard)
                }

                addressSort -> {
                    val lValue = mkArrayIndexLValue(addressSort, array, mkBv(index), arrayType)

                    state.memory.write(lValue, value.asExpr(addressSort), guard)
                }

                else -> error("Unsupported projected array element sort $sort")
            }
        }

        private fun box(value: UExpr<out USort>): UConcreteHeapRef = with(state.ctx) {
            if (value is UConcreteHeapRef && value.isFakeObject()) return@with value

            state.mkFakeValue(
                scope = null,
                boolValue = value.asOptionalBool(),
                fpValue = value.asOptionalFp(),
                refValue = value.asOptionalRef(),
            )
        }

        private fun UExpr<out USort>.asOptionalBool(): UExpr<KBoolSort>? =
            takeIf { sort == state.ctx.boolSort }?.asExpr(state.ctx.boolSort)

        private fun UExpr<out USort>.asOptionalFp(): UExpr<KFp64Sort>? =
            takeIf { sort == state.ctx.fp64Sort }?.asExpr(state.ctx.fp64Sort)

        private fun UExpr<out USort>.asOptionalRef(): UExpr<UAddressSort>? =
            takeIf { sort == state.ctx.addressSort }?.asExpr(state.ctx.addressSort)

        @Suppress("UNCHECKED_CAST")
        private fun sameSortIte(
            condition: UBoolExpr,
            trueValue: UExpr<out USort>,
            falseValue: UExpr<out USort>,
        ): UExpr<out USort> = state.ctx.mkIte(
            condition,
            trueValue as UExpr<USort>,
            falseValue as UExpr<USort>,
        )
    }
}

private fun writeStackValue(state: TsState, stackSlot: Int, value: UExpr<out USort>): Unit = with(state.ctx) {
    require(value.sort == boolSort || value.sort == fp64Sort || value.sort == addressSort) {
        "Unsupported projected stack sort ${value.sort}"
    }

    val lValue = mkRegisterStackLValue(value.sort, stackSlot)

    state.memory.write(lValue, value.cast(), guard = trueExpr)
    state.saveSortForLocal(stackSlot, value.sort)
}
