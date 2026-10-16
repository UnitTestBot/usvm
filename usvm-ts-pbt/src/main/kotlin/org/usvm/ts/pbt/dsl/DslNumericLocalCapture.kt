package org.usvm.ts.pbt.dsl

import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.observation.CompilerNumericLocalBinding
import org.usvm.ts.pbt.observation.ObservationPointKind
import org.usvm.ts.pbt.observation.PropertyObservationPoint
import org.usvm.ts.pbt.observation.PropertyObservationRequest
import org.usvm.ts.pbt.observation.PropertyObservationSource
import java.security.MessageDigest

/** Opt-in result of one numeric direct call, compared with one original input by a named check. */
data class DslNumericLocalCapture(val localName: String, val checkName: String)

/** Retains compiler-owned point identity and both selected original assertion operands without running the property. */
data class CompiledDslNumericLocalCapture(
    val binding: CompilerNumericLocalBinding,
    val result: PropertyObservationPoint,
    val input: PropertyObservationPoint,
) {
    fun observationRequest(): PropertyObservationRequest {
        val source = PropertyObservationSource(
            module = binding.predicateAssignment.module,
            sha256 = binding.sourceSha256
        )

        return PropertyObservationRequest(points = listOf(result, input), sources = listOf(source))
    }
}

internal data class SelectedDslNumericLocalCapture(
    val local: PropertyLocal,
    val check: NamedPropertyCheck,
    val input: PropertyExpressionTerm.Input,
    val resultOperandIndex: Int,
    val inputOperandIndex: Int,
)

internal fun DslProperty.selectNumericLocalCapture(request: DslNumericLocalCapture): SelectedDslNumericLocalCapture {
    val local = locals.singleOrNull { it.name == request.localName }
    requireNotNull(local) { "Unknown shared local: ${request.localName}" }
    require(local.expression.type == PropertyValueType.NUMBER && local.expression.term is PropertyExpressionTerm.Call) {
        "Numeric local capture supports only a direct function call"
    }
    val check = checks.singleOrNull { it.name == request.checkName }
    requireNotNull(check) { "Unknown named check: ${request.checkName}" }
    val comparison = check.expression.term as? PropertyExpressionTerm.Operation
    require(comparison != null && comparison.operator in setOf("===", "<=")) {
        "Numeric local capture requires a direct === or <= named check"
    }
    val localOperands = comparison.arguments.withIndex().filter { (_, node) ->
        (node.term as? PropertyExpressionTerm.Local)?.name == local.name
    }
    require(localOperands.size == 1) { "The selected local must be exactly one direct check operand" }
    val resultIndex = localOperands.single().index
    val inputIndex = 1 - resultIndex
    val inputNode = comparison.arguments[inputIndex]
    val input = inputNode.term as? PropertyExpressionTerm.Input
    require(input != null && inputNode.type == PropertyValueType.NUMBER) {
        "The other check operand must be one original numeric input"
    }

    return SelectedDslNumericLocalCapture(local, check, input, resultIndex, inputIndex)
}

internal fun SelectedDslNumericLocalCapture.compileCapture(
    definition: PropertyDefinition,
    typeScript: String,
    predicateAssignment: PropertySourcePoint,
    companionAssignment: PropertySourcePoint,
): CompiledDslNumericLocalCapture {
    val hash = MessageDigest.getInstance("SHA-256").digest(typeScript.toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
    val binding = CompilerNumericLocalBinding(
        producer = CompilerNumericLocalBinding.PRODUCER,
        abi = CompilerNumericLocalBinding.ABI,
        programId = definition.id.value,
        nodeId = local.expression.id,
        localName = local.name,
        predicateExportName = definition.predicate.exportName,
        companionExportName = requireNotNull(definition.predicate.checkResultExportName),
        predicateAssignment = predicateAssignment,
        companionAssignment = companionAssignment,
        sourceSha256 = hash,
        occurrence = 0,
    )
    val assertion = definition.assertions.single { it.id == check.name }
    val callSite = requireNotNull(assertion.testedCall)
    val result = PropertyObservationPoint(
        id = "dsl-result:${local.name}:${check.name}",
        assertionId = assertion.id,
        operandId = "operand-$resultOperandIndex",
        source = assertion.operands[resultOperandIndex].source,
        callSite = callSite,
        kind = ObservationPointKind.RETURN,
        compilerLocal = binding,
    )
    val inputPoint = PropertyObservationPoint(
        id = "dsl-input:${input.name}:${check.name}",
        assertionId = assertion.id,
        operandId = "operand-$inputOperandIndex",
        source = assertion.operands[inputOperandIndex].source,
        callSite = callSite,
        kind = ObservationPointKind.ENTRY_ARGUMENT,
        inputIndex = definition.inputs.indexOfFirst { it.name == input.name },
    )

    return CompiledDslNumericLocalCapture(binding, result, inputPoint)
}
