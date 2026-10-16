package org.usvm.ts.pbt.backend

import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.usvm.UsvmDslNumericLocalRefutation
import org.usvm.ts.pbt.usvm.UsvmRelationRefutationStatus

/** Captures the original input and compiler point; executable build identity remains unverified. */
fun UsvmDslNumericLocalRefutation.captureWitness(
    sources: List<PropertyObservationSource> = inference.sources,
): ExternalWitnessCandidate {
    val reached = status == UsvmRelationRefutationStatus.REFUTATION_REACHED ||
        status == UsvmRelationRefutationStatus.FAILED_INPUT_RESOLUTION
    require(reached) { "A numeric local witness requires a reached auxiliary target" }

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
            contextId = WitnessDslNumericLocalRelation.CONTEXT_ID,
            revision = revision,
        ),
        dslNumericLocalRelation = WitnessDslNumericLocalRelation(
            compiler = selected.compiler,
            inputIndex = selected.inputIndex,
            resultIsLeft = selected.resultIsLeft,
            template = relation.template,
        ),
    )

    return property.captureWitness(
        inputs = inputs,
        target = target,
        sources = sources,
        provenance = "usvm:dsl-local-refutation:${inference.runId}:${inference.buildStatus}:$status",
    )
}
