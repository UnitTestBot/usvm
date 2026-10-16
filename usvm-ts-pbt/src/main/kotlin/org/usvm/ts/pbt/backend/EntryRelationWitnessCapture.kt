package org.usvm.ts.pbt.backend

import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.usvm.UsvmEntryRefutationStatus
import org.usvm.ts.pbt.usvm.UsvmEntryRelationRefutation

/** The exact entry transport binding is independent of the still-unverified executable body. */
fun UsvmEntryRelationRefutation.captureWitness(
    sources: List<PropertyObservationSource> = inference.sources,
): ExternalWitnessCandidate {
    val reached = status == UsvmEntryRefutationStatus.REFUTATION_REACHED ||
        status == UsvmEntryRefutationStatus.FAILED_INPUT_RESOLUTION
    require(reached) { "An entry relation witness requires a reached auxiliary target" }

    val selected = binding.targets.single()
    val relation = selected.relation
    val left = relation.pair.left
    val right = relation.pair.right
    val targetId = "${left.assertionId}:${left.id}:${relation.template}:${right.id}"
    val property = inference.property
    val revision = property.witnessManifestSha256()
    val target = WitnessTarget(
        id = targetId,
        role = WitnessRole.HYPOTHESIS,
        binding = WitnessBinding(
            pointId = targetId,
            contextId = WitnessEntryRelation.CONTEXT_ID,
            revision = revision,
        ),
        entryRelation = WitnessEntryRelation(selected.leftInputIndex, selected.rightInputIndex, relation.template),
    )

    return property.captureWitness(
        inputs = inputs,
        target = target,
        sources = sources,
        provenance = "usvm:entry-refutation:${inference.runId}:${inference.buildStatus}:$status",
    )
}
