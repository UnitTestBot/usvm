package org.usvm.ts.calls

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsScene
import org.usvm.machine.state.TsState
import org.usvm.ts.pbt.fastcheck.TypeScriptSourceCapture
import org.usvm.ts.pbt.mapping.EtsInputBinding
import org.usvm.ts.pbt.mapping.EtsLexicalEnvironmentBinding
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.usvm.PlainObjectInputShapes
import org.usvm.util.mkRegisterStackLValue

internal fun PropertyDomain.isSupportedCallsSymbolicDomain(): Boolean = when (this) {
    BooleanDomain, is IntegerDomain, is NumberDomain, is StringDomain -> true
    is ConstantDomain -> value !is JsConcreteValue.Array && value !is JsConcreteValue.Object
    is OptionalDomain -> value.isSupportedCallsSymbolicDomain() &&
        (nil == JsConcreteValue.Null || nil == JsConcreteValue.Undefined)
    is ArrayDomain -> element.isSupportedCallsSymbolicDomain()
    is ObjectDomain -> fields.values.all { it.isSupportedCallsSymbolicDomain() }
    else -> false
}

internal fun PropertyDomain.callsInputCapacity(): Int = when (this) {
    is StringDomain -> maxLength
    is ConstantDomain -> (value as? JsConcreteValue.String)?.value?.length ?: 0
    is OptionalDomain -> value.callsInputCapacity()
    is ArrayDomain -> maxOf(maxLength, element.callsInputCapacity())
    is ObjectDomain -> fields.values.maxOfOrNull { it.callsInputCapacity() } ?: 0
    else -> 0
}

internal fun callsSymbolicInputPreflight(inputs: List<PropertyInput>): String? {
    val unsupportedInput = inputs.firstOrNull { input -> !input.domain.isSupportedCallsSymbolicDomain() }
        ?: return null

    return "Unsupported symbolic input domain for ${unsupportedInput.name}: ${unsupportedInput.domain}"
}

internal class CallsSymbolicInputs(
    inputs: List<PropertyInput>,
    bindings: List<EtsInputBinding>,
    private val lexicalEnvironment: EtsLexicalEnvironmentBinding?,
    private val captures: List<TypeScriptSourceCapture>,
    sourceScene: EtsScene,
    private val objectShapes: PlainObjectInputShapes = PlainObjectInputShapes(
        sourceScene = sourceScene,
        domains = inputs.map(PropertyInput::domain),
    ),
) {
    val scene: EtsScene = objectShapes.scene

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
        lexicalEnvironment?.let { binding -> state.initializeCallsCaptures(binding, captures) }
        val initializedSnapshots = boundInputs.map { (input, binding) ->
            state.initializeInput(
                stackSlot = binding.stackSlot,
                domain = input.domain,
                objectShapes = objectShapes,
            )
        }
        initializedSnapshots.forEach { snapshot -> snapshot.markDenseInputs(state) }
        snapshots = initializedSnapshots
    }

    fun resolve(state: TsState): Sequence<List<JsConcreteValue>> {
        val initializedSnapshots = checkNotNull(snapshots) { "Calls symbolic inputs were not initialized" }
        return state.models.asSequence().map { model ->
            initializedSnapshots.map { snapshot -> snapshot.resolve(model) }
        }
    }
}

private fun TsState.initializeInput(
    stackSlot: Int,
    domain: PropertyDomain,
    objectShapes: PlainObjectInputShapes,
): CallsInputSnapshot = with(ctx) {
    val input = materializeCallsInput(domain = domain, objectShapes = objectShapes)
    when (input.value.sort) {
        boolSort -> memory.write(
            mkRegisterStackLValue(boolSort, stackSlot),
            input.value.asExpr(boolSort),
            guard = trueExpr,
        )
        fp64Sort -> memory.write(
            mkRegisterStackLValue(fp64Sort, stackSlot),
            input.value.asExpr(fp64Sort),
            guard = trueExpr,
        )
        addressSort -> memory.write(
            mkRegisterStackLValue(addressSort, stackSlot),
            input.value.asExpr(addressSort),
            guard = trueExpr,
        )
        else -> error("Unsupported Calls input sort: ${input.value.sort}")
    }
    saveSortForLocal(stackSlot, input.value.sort)
    input.snapshot
}
