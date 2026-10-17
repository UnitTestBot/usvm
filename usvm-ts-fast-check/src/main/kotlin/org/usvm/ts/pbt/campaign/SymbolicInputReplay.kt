package org.usvm.ts.pbt.campaign

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.usvm.ts.pbt.FastCheckDiagnosticCode
import org.usvm.ts.pbt.backend.ExternalWitnessCandidate
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.WitnessAdmission
import org.usvm.ts.pbt.backend.WitnessNativeReplay
import org.usvm.ts.pbt.backend.WitnessOracleOutcome
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.backend.WitnessTarget
import org.usvm.ts.pbt.backend.captureWitness
import org.usvm.ts.pbt.coverage.captureCoverageIdentity
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.PbtBackendException
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.usvm.UsvmCompletedInput
import java.io.IOException
import java.nio.file.Path
import kotlin.time.TimeSource

/** One actual fixed-input operation; a native false/exception remains separate from fault confirmation. */
@Serializable
data class SymbolicInputReplay(
    val candidate: ExternalWitnessCandidate,
    val replay: WitnessNativeReplay,
    val elapsedMillis: Long,
    val completedWithinDeadline: Boolean,
    val predictedOutcomes: Set<WitnessOracleOutcome> = emptySet(),
) {
    /** Only an admitted, repeatable, fresh and timely native receipt contributes coverage. */
    val admittedCoverage: PropertyCoverageArtifact?
        get() {
            val coverage = replay.coverage ?: return null
            val matchesPrediction = predictedOutcomes == setOf(WitnessOracleOutcome.HOLDS) &&
                replay.outcome == WitnessOracleOutcome.HOLDS
            val exact = coverage.identity != null && coverage.provenance.request.admittedOnly &&
                coverage.admittedInvocations == 1 && coverage.propertyId == candidate.propertyId
            val admitted = completedWithinDeadline && replay.admission == WitnessAdmission.ADMITTED &&
                matchesPrediction && exact && replay.diagnostic == null
            return if (admitted) coverage else null
        }
}

/** Owns immutable input snapshots and the bounded same-phase cache shared by capture and edge guidance. */
internal class SymbolicInputReplayCollector(
    private val property: PropertyDefinition,
    private val backend: FastCheckBackend,
    root: Path,
    sources: List<PropertyObservationSource>,
    private val coverageRequest: PropertyCoverageRequest,
    private val deadline: WitnessReplayDeadline,
    private val validationReserveMillis: Long,
    private val limit: Int,
) {
    private val identity = if (limit > 0) captureCoverageIdentity(property.toManifest(), listOf(root)) else null
    val sources = identity?.sourceSha256?.map { (path, hash) ->
        val source = Path.of(path)
        require(source.startsWith(root)) { "Native capture source escapes its source root: $path" }
        PropertyObservationSource(module = root.relativize(source).toString().replace('\\', '/'), sha256 = hash)
    } ?: sources
    private val cache = linkedMapOf<String, SymbolicInputReplay>()
    private val completed = mutableListOf<SymbolicInputReplay>()

    val replays: List<SymbolicInputReplay>
        get() = completed.toList()

    var diagnostic: String? = null
        private set

    var timedOut: Boolean = false
        private set

    fun captureForValidation(input: UsvmCompletedInput) {
        // A fault must reach canonical confirmation before optional input coverage consumes its budget.
        if (input.expectedOutcome != WitnessOracleOutcome.HOLDS) return
        if (deadline.remainingMillis() <= validationReserveMillis) return

        // The search cutoff already reserves validation time; do not subtract it from this operation again.
        replay(input.inputs, reserveMillis = 0)
    }

    fun replayForGuidance(inputs: List<JsConcreteValue>): WitnessNativeReplay? {
        val record = replay(inputs, reserveMillis = validationReserveMillis) ?: return null
        return if (record.completedWithinDeadline) {
            record.replay
        } else {
            record.replay.copy(coverage = null, diagnostic = "campaign.native-replay.deadline")
        }
    }

    fun collect(inputs: List<UsvmCompletedInput>) {
        val groups = inputs.groupBy { key(it.inputs) }.values.take(limit)
        val selectedKeys = groups.mapTo(hashSetOf()) { key(it.first().inputs) }
        cache.keys.retainAll(selectedKeys)
        for ((index, group) in groups.withIndex()) {
            val record = replay(group.first().inputs, reserveMillis = 0)
            if (record == null) {
                diagnostic = "Native verification deadline reached; ${groups.size - index} inputs remain unchecked"
                timedOut = true
                break
            }
            val predictions = group.mapTo(linkedSetOf()) { it.expectedOutcome }
            val native = record.replay
            val outcomeMatches = native.outcome in predictions && predictions.size == 1
            val replay = if (native.admission == WitnessAdmission.ADMITTED && !outcomeMatches) {
                native.copy(diagnostic = native.diagnostic ?: "symbolic.native.oracle-mismatch")
            } else {
                native
            }
            completed += record.copy(replay = replay, predictedOutcomes = predictions)
            val operationTimedOut = native.diagnostic?.contains(FastCheckDiagnosticCode.BACKEND_PROCESS_TIMEOUT) == true
            if (!record.completedWithinDeadline || operationTimedOut) {
                diagnostic = "Native verification deadline reached; ${groups.size - index - 1} inputs remain unchecked"
                timedOut = true
                break
            }
            val admitted = replay.admission == WitnessAdmission.ADMITTED && replay.diagnostic == null
            if (!admitted) {
                diagnostic = "Native input verification incomplete: " +
                    (replay.diagnostic ?: "${replay.admission}/${replay.outcome}")
            }
        }
    }

    private fun replay(inputs: List<JsConcreteValue>, reserveMillis: Long): SymbolicInputReplay? {
        val key = key(inputs)
        cache[key]?.let { return it }
        val remaining = deadline.remainingMillis() - reserveMillis
        if (remaining <= 0) return null

        val snapshot = PropertyManifestJson.json.decodeFromString<List<JsConcreteValue>>(key)
        val candidate = property.captureWitness(
            inputs = snapshot,
            target = WitnessTarget(id = property.predicate.exportName),
            sources = sources,
            provenance = "usvm:completed-input",
        )
        val started = TimeSource.Monotonic.markNow()
        val operationDeadline = WitnessReplayDeadline(timeoutMillis = remaining)
        val native = invoke(candidate, operationDeadline)
        val nativeCoverage = native.coverage
        val fresh = identity == null || nativeCoverage == null || nativeCoverage.identity == identity
        val record = SymbolicInputReplay(
            candidate = candidate,
            replay = if (fresh) {
                native
            } else {
                native.copy(diagnostic = listOfNotNull(native.diagnostic, "coverage.source.stale").joinToString("; "))
            },
            elapsedMillis = started.elapsedNow().inWholeMilliseconds,
            completedWithinDeadline = deadline.remainingMillis() > 0,
        )
        if (cache.size < limit) cache[key] = record
        return record
    }

    private fun invoke(
        candidate: ExternalWitnessCandidate,
        operationDeadline: WitnessReplayDeadline,
    ): WitnessNativeReplay = try {
        backend.replayInput(property, candidate, coverageRequest, operationDeadline)
    } catch (error: PbtBackendException) {
        unavailable("${error.code}: ${error.message}")
    } catch (error: IOException) {
        unavailable("Native source snapshot failed: ${error.message}")
    } catch (error: IllegalArgumentException) {
        unavailable("Native input is unsupported: ${error.message}")
    }

    private fun unavailable(diagnostic: String) = WitnessNativeReplay(
        admission = WitnessAdmission.UNKNOWN,
        outcome = WitnessOracleOutcome.UNKNOWN,
        targetMatched = false,
        diagnostic = diagnostic,
    )

    private fun key(inputs: List<JsConcreteValue>): String =
        PropertyManifestJson.json.encodeToUtf8SafeString(inputs)
}
