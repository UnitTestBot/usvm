package org.usvm.ts.pbt.inference

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.dsl.CompiledDslProperty
import org.usvm.ts.pbt.dsl.DslNumericLocalCapture
import org.usvm.ts.pbt.dsl.PropertyExpressionTerm
import org.usvm.ts.pbt.dsl.isReproducibleCompilerOutput
import org.usvm.ts.pbt.dsl.selectNumericLocalCapture
import org.usvm.ts.pbt.observation.ObservationPointKind

/** KNOWN_DSL_CHECK identifies a direct original check, not a proved relation or executable body. */
@Serializable
enum class ScalarGuidanceRelevance { KNOWN_DSL_CHECK, UNKNOWN }

/** Selection decisions preserve each excluded relation and its original evidence. */
@Serializable
enum class ScalarGuidanceExclusionReason {
    NOT_SAMPLED,
    DOMAIN_IMPLIED,
    TAUTOLOGY,
    DUPLICATE,
    ORIGINAL_CHECK,
    CANDIDATE_LIMIT,
}

/** An empirical candidate whose exact symbolic support still needs a consumer's binding checks. */
@Serializable
data class ScalarGuidanceCandidate(val relation: ScalarRelation, val relevance: ScalarGuidanceRelevance)

/** An unselected relation; exclusion never removes it from the original inference. */
@Serializable
data class ScalarGuidanceExclusion(
    val relation: ScalarRelation,
    val reason: ScalarGuidanceExclusionReason,
    val relevance: ScalarGuidanceRelevance,
)

/** Retains the complete original inference, including contradictory, insufficient and excluded evidence. */
@Serializable
data class ScalarGuidanceSelection(
    val inference: ScalarRelationInference,
    val candidates: List<ScalarGuidanceCandidate>,
    val excluded: List<ScalarGuidanceExclusion>,
)

/** One bounded selection before USVM. The campaign decides whether UNKNOWN relevance may guide its search. */
fun selectScalarGuidance(
    inference: ScalarRelationInference,
    compiled: CompiledDslProperty? = null,
    maxCandidates: Int = 16,
): ScalarGuidanceSelection {
    require(maxCandidates > 0) { "maxCandidates must be positive" }

    val original = originalCheck(inference, compiled)
    val candidates = mutableListOf<ScalarGuidanceCandidate>()
    val excluded = mutableListOf<ScalarGuidanceExclusion>()
    for (relation in inference.relations) {
        val known = original?.contains(relation.pair) == true
        val relevance = if (known) ScalarGuidanceRelevance.KNOWN_DSL_CHECK else ScalarGuidanceRelevance.UNKNOWN
        val duplicate = candidates.any { candidate -> candidate.relation.sameComparison(relation) }
        val sampled = relation.status == ScalarRelationStatus.SAMPLED && relation.limitations.isEmpty()
        val restatesOriginal = known && original?.implies(relation) == true
        val reason = when {
            !sampled -> ScalarGuidanceExclusionReason.NOT_SAMPLED
            restatesOriginal -> ScalarGuidanceExclusionReason.ORIGINAL_CHECK
            duplicate -> ScalarGuidanceExclusionReason.DUPLICATE
            candidates.size >= maxCandidates -> ScalarGuidanceExclusionReason.CANDIDATE_LIMIT
            else -> null
        }

        if (reason == null) {
            candidates += ScalarGuidanceCandidate(relation, relevance)
        } else {
            excluded += ScalarGuidanceExclusion(relation, reason, relevance)
        }
    }
    for (relation in inference.domainImpliedRelations) {
        val sameOriginalInput = relation.pair.left.kind == ObservationPointKind.ENTRY_ARGUMENT &&
            relation.pair.right.kind == ObservationPointKind.ENTRY_ARGUMENT &&
            relation.pair.left.inputIndex != null && relation.pair.left.inputIndex == relation.pair.right.inputIndex
        val reason = if (sameOriginalInput) {
            ScalarGuidanceExclusionReason.TAUTOLOGY
        } else {
            ScalarGuidanceExclusionReason.DOMAIN_IMPLIED
        }
        excluded += ScalarGuidanceExclusion(relation, reason, relevance = ScalarGuidanceRelevance.UNKNOWN)
    }

    return ScalarGuidanceSelection(inference, candidates, excluded)
}

private data class OriginalScalarCheck(val pair: ScalarObservationPair, val operator: String) {
    fun contains(candidate: ScalarObservationPair): Boolean = candidate == pair ||
        candidate.left == pair.right && candidate.right == pair.left

    fun implies(relation: ScalarRelation): Boolean = operator == "===" ||
        operator == "<=" && relation.template == ScalarRelationTemplate.LESS_OR_EQUAL && relation.pair == pair
}

private fun originalCheck(
    inference: ScalarRelationInference,
    compiled: CompiledDslProperty?,
): OriginalScalarCheck? {
    if (compiled == null) return null

    val sameProperty = compiled.definition == inference.property && compiled.definition.id == inference.propertyId
    if (!sameProperty) return null
    if (!compiled.isReproducibleCompilerOutput()) return null

    val capture = compiled.numericLocalCapture ?: return null
    val source = inference.sources.singleOrNull { it.module == compiled.definition.predicate.module }
    if (source?.sha256 != capture.binding.sourceSha256) return null

    val request = DslNumericLocalCapture(capture.binding.localName, capture.result.assertionId)
    val selected = compiled.program.selectNumericLocalCapture(request)
    val comparison = selected.check.expression.term as PropertyExpressionTerm.Operation
    val pair = if (selected.resultOperandIndex == 0) {
        ScalarObservationPair(capture.result, capture.input)
    } else {
        ScalarObservationPair(capture.input, capture.result)
    }

    return OriginalScalarCheck(pair, comparison.operator)
}

private fun ScalarRelation.sameComparison(other: ScalarRelation): Boolean {
    if (template != other.template) return false

    val reversed = template == ScalarRelationTemplate.STRICT_EQUAL &&
        pair.left == other.pair.right && pair.right == other.pair.left

    return pair == other.pair || reversed
}
