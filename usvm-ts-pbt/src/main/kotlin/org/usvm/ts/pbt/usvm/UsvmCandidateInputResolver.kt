package org.usvm.ts.pbt.usvm

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.isTrue
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.extractInt
import org.usvm.machine.expr.toConcreteBoolValue
import org.usvm.machine.state.TsState
import org.usvm.sizeSort
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
import org.usvm.util.mkArrayLengthLValue

internal class UsvmCandidateInputResolver {
    fun resolve(
        state: TsState,
        declaredInputs: List<PropertyInput>,
        projection: UsvmDeclaredDomainProjection,
    ): List<JsConcreteValue> {
        require(declaredInputs.size == projection.inputs.size)
        val initialState = projection.initialState.clone()
        initialState.models = state.models

        return declaredInputs.zip(projection.inputs).map { (input, projected) ->
            resolveValue(
                state = initialState,
                domain = input.domain,
                etsType = projected.etsType,
                value = projected.value,
            )
        }
    }

    private fun resolveValue(
        state: TsState,
        domain: PropertyDomain,
        etsType: EtsType,
        value: UExpr<out USort>,
    ): JsConcreteValue = with(state.ctx) {
        if (value.isFakeObject()) {
            return@with resolveFakeValue(state, domain, etsType, value)
        }

        when (domain) {
            BooleanDomain -> JsConcreteValue.Boolean(
                state.models.single().eval(value.asExpr(boolSort)).toConcreteBoolValue(),
            )

            is IntegerDomain, is NumberDomain -> JsConcreteValue.number(
                state.models.single().eval(value.asExpr(fp64Sort)).extractDouble(),
            )

            is StringDomain -> resolveString(state, value)
            is ConstantDomain -> domain.value
            is OptionalDomain -> resolveOptional(state, domain, etsType, value)
            is TupleDomain -> resolveTuple(state, domain, etsType, value)
            is ArrayDomain -> resolveArray(state, domain, etsType as EtsArrayType, value)
        }
    }

    private fun resolveFakeValue(
        state: TsState,
        domain: PropertyDomain,
        etsType: EtsType,
        value: UConcreteHeapRef,
    ): JsConcreteValue = with(state.ctx) {
        val model = state.models.single()
        val fakeType = value.getFakeType(state.memory)
        val selected = when {
            model.eval(fakeType.boolTypeExpr).isTrue -> value.extractBool(state.memory)
            model.eval(fakeType.fpTypeExpr).isTrue -> value.extractFp(state.memory)
            model.eval(fakeType.refTypeExpr).isTrue -> value.extractRef(state.memory)
            else -> error("Cannot resolve the selected fake-object type")
        }

        resolveValue(state, domain, etsType, selected)
    }

    private fun resolveOptional(
        state: TsState,
        domain: OptionalDomain,
        etsType: EtsType,
        value: UExpr<out USort>,
    ): JsConcreteValue = with(state.ctx) {
        if (value.sort == addressSort) {
            val ref = value.asExpr(addressSort)
            val nil = when (domain.nil) {
                JsConcreteValue.Null -> mkTsNullValue()
                JsConcreteValue.Undefined -> mkUndefinedValue()
                else -> error("Optional nil must be null or undefined")
            }
            if (state.models.single().eval(mkHeapRefEq(ref, nil)).isTrue) {
                return@with domain.nil
            }
        }

        val nestedType = (etsType as org.jacodb.ets.model.EtsUnionType).types.first { type ->
            UsvmProjectionCapabilityResolver().domainCompatibilityForProjector(domain.value, type)
        }

        resolveValue(state, domain.value, nestedType, value)
    }

    private fun resolveString(state: TsState, value: UExpr<out USort>): JsConcreteValue.String = with(state.ctx) {
        val ref = state.models.single().eval(value.asExpr(addressSort)) as? UConcreteHeapRef
            ?: error("Symbolic string reference did not resolve to a concrete heap reference")
        val concrete = getStringConstantValue(ref)
            ?: error("Symbolic string contents are unavailable")

        JsConcreteValue.String(concrete)
    }

    private fun resolveTuple(
        state: TsState,
        domain: TupleDomain,
        etsType: EtsType,
        value: UExpr<out USort>,
    ): JsConcreteValue.Array = with(state.ctx) {
        val ref = state.models.single().eval(value.asExpr(addressSort)) as UConcreteHeapRef
        val elementTypes = when (etsType) {
            is EtsTupleType -> etsType.types
            is EtsArrayType -> List(domain.elements.size) { etsType.elementType }
            else -> error("Unsupported tuple EtsIR type $etsType")
        }
        val arrayType = EtsArrayType(EtsUnknownType, dimensions = 1)
        val elements = domain.elements.zip(elementTypes).mapIndexed { index, (elementDomain, elementType) ->
            val lValue = mkArrayIndexLValue(addressSort, ref, mkBv(index), arrayType)
            val element = state.memory.read(lValue)

            resolveValue(state, elementDomain, elementType, element)
        }

        JsConcreteValue.Array(elements)
    }

    private fun resolveArray(
        state: TsState,
        domain: ArrayDomain,
        etsType: EtsArrayType,
        value: UExpr<out USort>,
    ): JsConcreteValue.Array = with(state.ctx) {
        val ref = state.models.single().eval(value.asExpr(addressSort)) as UConcreteHeapRef
        val lengthLValue = mkArrayLengthLValue(ref, etsType)
        val length = state.models.single()
            .eval(state.memory.read(lengthLValue).asExpr(sizeSort))
            .extractInt()
        val elementSort = typeToSort(arrayDescriptorOf(etsType).let { it as EtsArrayType }.elementType)
        val elements = (0 until length).map { index ->
            val element = if (elementSort is TsUnresolvedSort) {
                state.memory.read(mkArrayIndexLValue(addressSort, ref, mkBv(index), etsType))
            } else {
                state.memory.read(mkArrayIndexLValue(elementSort, ref, mkBv(index), etsType))
            }

            resolveValue(state, domain.element, etsType.elementType, element)
        }

        JsConcreteValue.Array(elements)
    }
}
