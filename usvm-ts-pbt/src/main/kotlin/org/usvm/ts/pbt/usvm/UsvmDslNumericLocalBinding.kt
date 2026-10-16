package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberType
import org.usvm.ts.pbt.dsl.CompiledDslProperty
import org.usvm.ts.pbt.dsl.isReproducibleCompilerOutput
import org.usvm.ts.pbt.inference.ScalarRelation
import org.usvm.ts.pbt.inference.ScalarRelationInference
import org.usvm.ts.pbt.inference.hasSampledSupport
import org.usvm.ts.pbt.inference.matches
import org.usvm.ts.pbt.mapping.EtsEntryPointTarget
import org.usvm.ts.pbt.mapping.EtsInputBinding
import org.usvm.ts.pbt.mapping.EtsMappingDiagnostic
import org.usvm.ts.pbt.mapping.EtsMappingResult
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.mapping.SourceLocationNormalizer
import org.usvm.ts.pbt.mapping.inputBindingsFor
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.observation.CompilerNumericLocalBinding
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.security.MessageDigest

/** Exact only for this compiler-owned post-store point; executable body/build provenance stays independent. */
data class UsvmDslNumericLocalBinding(
    val method: EtsMethod,
    val assignment: EtsAssignStmt,
    val companionAssignment: EtsAssignStmt,
    val local: EtsLocal,
    val inputIndex: Int,
    val input: EtsInputBinding,
    val resultIsLeft: Boolean,
    val compiler: CompilerNumericLocalBinding,
    val relation: ScalarRelation,
)

/** Binds the retained compiler program and a complete native receipt to one original post-store point. */
fun bindDslNumericLocalRelation(
    compiled: CompiledDslProperty,
    mapping: PropertyEtsMappingArtifact,
    inference: ScalarRelationInference,
    relation: ScalarRelation,
): EtsMappingResult<UsvmDslNumericLocalBinding> {
    val sameProperty = inference.property == compiled.definition && inference.propertyId == compiled.program.id &&
        mapping.propertyId == compiled.definition.id
    if (!sameProperty) return unbound(reason = "property-mismatch", message = "The original property changed")

    val capture = compiled.numericLocalCapture
    if (capture == null || !compiled.isReproducibleCompilerOutput()) {
        return unbound(
            reason = "compiler-mismatch",
            message = "The retained program must reproduce the compiler output"
        )
    }
    val sampled = relation in inference.relations && relation.hasSampledSupport() && relation.evidence.isNotEmpty()
    if (!sampled) return unbound(reason = "not-sampled", message = "A complete sampled relation is required")

    val resultIsLeft = relation.pair.left == capture.result && relation.pair.right == capture.input
    val resultIsRight = relation.pair.right == capture.result && relation.pair.left == capture.input
    if (!resultIsLeft && !resultIsRight) {
        return unbound(reason = "pair-mismatch", message = "The selected compiler result and original input must match")
    }
    val inputIndex = requireNotNull(capture.input.inputIndex)
    val receiptsMatch = relation.evidence.all { evidence ->
        val original = evidence.invocation.input.value as? JsConcreteValue.Array
        val result = if (resultIsLeft) evidence.left else evidence.right
        val input = if (resultIsLeft) evidence.right else evidence.left
        val resultEvents = evidence.invocation.points.filter { it.pointId == capture.result.id }
        val inputEvents = evidence.invocation.points.filter { it.pointId == capture.input.id }

        evidence.invocation.parentInvocationId == null && evidence.invocation.callSite == capture.result.callSite &&
            resultEvents.singleOrNull() == result && inputEvents.singleOrNull() == input &&
            result.matches(capture.result) && input.matches(capture.input) &&
            result.value.value is JsConcreteValue.Number && input.value.value is JsConcreteValue.Number &&
            input.value.value == original?.elements?.getOrNull(inputIndex) && input.eventOrdinal < result.eventOrdinal
    }
    val pinnedSource = inference.sources.singleOrNull { it.module == compiled.definition.predicate.module }
    if (!receiptsMatch || pinnedSource?.sha256 != capture.binding.sourceSha256) {
        return unbound(
            reason = "receipt-mismatch",
            message = "Compiler receipt, original input or selected source changed"
        )
    }

    val predicateMapping = mapping.predicate
    val unavailableEntry = predicateMapping.status == EtsMappingStatus.UNMAPPED ||
        predicateMapping.status == EtsMappingStatus.UNSUPPORTED
    if (unavailableEntry) {
        return EtsMappingResult(
            predicateMapping.status,
            targets = emptyList(),
            diagnostics = predicateMapping.diagnostics
        )
    }
    val inputBindingsMatch = predicateMapping.targets.all { entry ->
        val expected = entry.method.inputBindingsFor(compiled.definition.inputs)

        expected.size == compiled.definition.inputs.size && entry.bindings.inputs == expected
    }
    if (!inputBindingsMatch) {
        return unbound(reason = "input-mismatch", message = "The original positional input bindings changed")
    }
    val locations = SourceLocationNormalizer(mapping.provenance.sourceRoots.map(Path::of))
    val module = compiled.definition.predicate.module
    val paths = try {
        locations.sourcePathCandidates(module).filter(Files::isRegularFile)
    } catch (_: InvalidPathException) {
        return unbound(reason = "source-unmapped", message = "The generated module path is unavailable")
    }
    if (paths.isEmpty()) {
        return unbound(
            reason = "source-unmapped",
            message = "The generated source file is missing",
            status = EtsMappingStatus.UNMAPPED
        )
    }
    val candidates = predicateMapping.targets.flatMap { entry ->
        entry.bindCompilerStores(compiled, relation, resultIsLeft, paths, locations)
    }
    if (candidates.isEmpty()) {
        return unbound(
            reason = "store-unmapped",
            message = "The original predicate and companion require their declared numeric local stores",
            status = EtsMappingStatus.UNMAPPED,
        )
    }
    val ambiguous = paths.size != 1 || candidates.size != 1 || predicateMapping.status == EtsMappingStatus.AMBIGUOUS
    if (ambiguous) {
        val diagnostic =
            diagnostic(reason = "store-ambiguous", message = "Several generated sources, entries or stores match")

        return EtsMappingResult(EtsMappingStatus.AMBIGUOUS, targets = candidates, diagnostics = listOf(diagnostic))
    }
    val sourceMatches = compiled.matchesPinnedSources(inference, locations, generatedSource = paths.single())
    if (!sourceMatches) {
        return unbound(reason = "source-stale", message = "The generated source no longer matches the original program")
    }

    return EtsMappingResult(EtsMappingStatus.EXACT, targets = candidates)
}

private fun EtsEntryPointTarget.bindCompilerStores(
    compiled: CompiledDslProperty,
    relation: ScalarRelation,
    resultIsLeft: Boolean,
    paths: List<Path>,
    locations: SourceLocationNormalizer,
): List<UsvmDslNumericLocalBinding> {
    val capture = requireNotNull(compiled.numericLocalCapture)
    val inputIndex = requireNotNull(capture.input.inputIndex)
    val input = bindings.inputs.getOrNull(inputIndex)
    val validEntry = method.name == capture.binding.predicateExportName &&
        bindings.inputs.size == compiled.definition.inputs.size && input?.parameter?.type == EtsNumberType
    if (!validEntry) return emptyList()

    val owner = method.enclosingClass
    val companionMethods = owner?.methods.orEmpty().filter { companion ->
        companion.name == capture.binding.companionExportName &&
            companion.parameters.lastOrNull()?.name == "__usvmObserve" &&
            companion.parameters.size == method.parameters.size + 1
    }
    val originals = method.storesAt(capture.binding.predicateAssignment, capture.binding.localName, paths, locations)
    val companions = companionMethods.flatMap { companion ->
        companion.storesAt(capture.binding.companionAssignment, capture.binding.localName, paths, locations)
    }

    return originals.flatMap { assignment ->
        companions.map { companion ->
            UsvmDslNumericLocalBinding(
                method = method,
                assignment = assignment,
                companionAssignment = companion,
                local = assignment.lhv as EtsLocal,
                inputIndex = inputIndex,
                input = requireNotNull(input),
                resultIsLeft = resultIsLeft,
                compiler = capture.binding,
                relation = relation,
            )
        }
    }
}

private fun CompiledDslProperty.matchesPinnedSources(
    inference: ScalarRelationInference,
    locations: SourceLocationNormalizer,
    generatedSource: Path,
): Boolean = try {
    val selectedSourcesMatch = inference.sources.all { source ->
        val paths = locations.sourcePathCandidates(source.module).filter(Files::isRegularFile)
        val path = paths.singleOrNull()
        val hash = path?.let { file ->
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        }

        hash == source.sha256
    }

    selectedSourcesMatch && Files.readString(generatedSource) == typeScript
} catch (_: IOException) {
    false
} catch (_: InvalidPathException) {
    false
}

private fun EtsMethod.storesAt(
    point: PropertySourcePoint,
    localName: String,
    paths: List<Path>,
    locations: SourceLocationNormalizer,
): List<EtsAssignStmt> = cfg.instructions.filterIsInstance<EtsAssignStmt>().filter { assignment ->
    val local = assignment.lhv as? EtsLocal
    val origin = assignment.location.origin
    val pathsMatch = origin != null && locations.sourcePathCandidates(origin.fileName).any { it in paths }

    local != null && local.name == localName && local.type == EtsNumberType && origin != null && pathsMatch &&
        origin.startLine == point.line - 1 && origin.startColumn == point.column - 1
}

private fun unbound(
    reason: String,
    message: String,
    status: EtsMappingStatus = EtsMappingStatus.UNSUPPORTED,
): EtsMappingResult<UsvmDslNumericLocalBinding> {
    val diagnostic = diagnostic(reason, message)

    return EtsMappingResult(status = status, targets = emptyList(), diagnostics = listOf(diagnostic))
}

private fun diagnostic(reason: String, message: String): EtsMappingDiagnostic =
    EtsMappingDiagnostic(code = "usvm.dsl-local.$reason", message = message)
