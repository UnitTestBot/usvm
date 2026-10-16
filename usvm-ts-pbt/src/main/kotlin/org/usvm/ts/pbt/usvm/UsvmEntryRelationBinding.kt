package org.usvm.ts.pbt.usvm

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsNumberType
import org.usvm.UBoolExpr
import org.usvm.machine.state.TsState
import org.usvm.ts.pbt.inference.ScalarRelation
import org.usvm.ts.pbt.inference.ScalarRelationInference
import org.usvm.ts.pbt.inference.ScalarRelationStatus
import org.usvm.ts.pbt.inference.ScalarRelationTemplate
import org.usvm.ts.pbt.inference.matches
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.EtsInputBinding
import org.usvm.ts.pbt.mapping.EtsMappingDiagnostic
import org.usvm.ts.pbt.mapping.EtsMappingResult
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.observation.ObservationPointKind
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.security.MessageDigest

/** Exact only for adapter-owned root input snapshots, independently of the executable body build. */
data class UsvmEntryRelationBinding(
    val leftInputIndex: Int,
    val rightInputIndex: Int,
    val left: EtsInputBinding,
    val right: EtsInputBinding,
    val relation: ScalarRelation,
)

internal fun UsvmEntryRelationBinding.negatedRelation(
    state: TsState,
    projection: UsvmDeclaredDomainProjection,
): UBoolExpr = with(state.ctx) {
    val leftValue = projection.inputs[leftInputIndex].value.asExpr(fp64Sort)
    val rightValue = projection.inputs[rightInputIndex].value.asExpr(fp64Sort)
    val hypothesis = when (relation.template) {
        ScalarRelationTemplate.STRICT_EQUAL -> mkFpEqualExpr(leftValue, rightValue)
        ScalarRelationTemplate.LESS_OR_EQUAL -> mkFpLessOrEqualExpr(leftValue, rightValue)
    }

    hypothesis.not()
}

/** An explicitly focused original-property search; no-finding does not exhaust the original domain. */
data class UsvmEntryRelationSearchResult(
    val binding: EtsMappingResult<UsvmEntryRelationBinding>,
    val inference: ScalarRelationInference,
    val search: UsvmPropertySearchResult?,
)

internal fun bindEntryRelation(
    manifest: PropertyManifest,
    mapping: PropertyEtsMappingArtifact,
    inference: ScalarRelationInference,
    relation: ScalarRelation,
): EtsMappingResult<UsvmEntryRelationBinding> {
    val sameProperty = inference.property.toManifest() == manifest &&
        inference.propertyId == mapping.propertyId && inference.propertyId.value == manifest.propertyId
    if (!sameProperty) {
        return unsupported(reason = "property-mismatch", message = "Property definition or declared support changed")
    }

    val sampled = relation in inference.relations && relation.status == ScalarRelationStatus.SAMPLED &&
        relation.limitations.isEmpty()
    if (!sampled) {
        return unsupported(reason = "not-sampled", message = "A sampled relation from this inference is required")
    }

    val points = listOf(relation.pair.left, relation.pair.right)
    val entrySnapshots = points.all { point ->
        point.kind == ObservationPointKind.ENTRY_ARGUMENT && point.inputIndex in manifest.inputs.indices &&
            point.source.module == manifest.predicate.module && point.callSite.module == manifest.predicate.module
    }
    if (!entrySnapshots) {
        return unsupported(reason = "point-unsupported", message = "Only adapter-owned root entry arguments are bound")
    }

    val snapshotsMatch = relation.evidence.all { evidence ->
        val input = evidence.invocation.input.value as? JsConcreteValue.Array
        val events = listOf(evidence.left, evidence.right)
        val eventsMatch = events.zip(points).all { (event, point) ->
            val index = event.inputIndex
            event.matches(point) && index != null && event.value.value is JsConcreteValue.Number &&
                event.value.value == input?.elements?.getOrNull(index)
        }

        eventsMatch && evidence.invocation.parentInvocationId == null &&
            evidence.invocation.callSite == relation.pair.left.callSite
    }
    if (!snapshotsMatch) {
        return unsupported(reason = "snapshot-mismatch", message = "Entry values do not match original input snapshots")
    }

    val sourceMatches = try {
        sourceMatches(manifest, mapping, inference)
    } catch (_: IOException) {
        false
    } catch (_: InvalidPathException) {
        false
    }
    if (!sourceMatches) {
        return unsupported(
            reason = "source-mismatch",
            message = "The selected predicate source is missing, ambiguous or stale",
        )
    }

    val predicate = mapping.predicate
    if (predicate.status != EtsMappingStatus.EXACT) {
        return unsupported(
            reason = "entry-${predicate.status.name.lowercase()}",
            message = "The original entry point must map exactly",
        )
    }
    val target = predicate.targets.singleOrNull()
        ?: return unsupported(reason = "entry-ambiguous", message = "Exactly one original entry point is required")
    if (target.bindings.inputs.size != manifest.inputs.size) {
        return unsupported(reason = "slots-mismatch", message = "Original inputs do not match the entry slots")
    }
    val leftIndex = requireNotNull(points[0].inputIndex)
    val rightIndex = requireNotNull(points[1].inputIndex)
    val left = target.bindings.inputs[leftIndex]
    val right = target.bindings.inputs[rightIndex]
    if (left.parameter.type != EtsNumberType || right.parameter.type != EtsNumberType) {
        return unsupported(reason = "type-unsupported", message = "Both entry slots must have the numeric EtsIR type")
    }

    val binding = UsvmEntryRelationBinding(leftIndex, rightIndex, left, right, relation)

    return EtsMappingResult(
        status = EtsMappingStatus.EXACT,
        targets = listOf(binding),
    )
}

private fun sourceMatches(
    manifest: PropertyManifest,
    mapping: PropertyEtsMappingArtifact,
    inference: ScalarRelationInference,
): Boolean {
    val module = manifest.predicate.module
    val source = inference.sources.singleOrNull { it.module == module } ?: return false
    val paths = mapping.provenance.sourceRoots.map { root -> Path.of(root).resolve(module).normalize() }
    val existing = paths.filter(Files::isRegularFile).map(Path::toRealPath).distinct()
    val path = existing.singleOrNull() ?: return false
    val bytes = Files.readAllBytes(path)
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    return source.sha256 == hash
}

private fun unsupported(reason: String, message: String): EtsMappingResult<UsvmEntryRelationBinding> {
    val diagnostic = EtsMappingDiagnostic(code = "usvm.entry-relation.$reason", message = message)

    return EtsMappingResult(
        status = EtsMappingStatus.UNSUPPORTED,
        targets = emptyList(),
        diagnostics = listOf(diagnostic),
    )
}
