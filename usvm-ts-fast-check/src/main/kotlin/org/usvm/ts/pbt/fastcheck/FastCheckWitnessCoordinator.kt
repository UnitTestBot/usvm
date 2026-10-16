package org.usvm.ts.pbt.fastcheck

import org.usvm.ts.pbt.FastCheckDiagnosticCode
import org.usvm.ts.pbt.backend.ExternalWitnessCandidate
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.WitnessAdmission
import org.usvm.ts.pbt.backend.WitnessCoverageEdge
import org.usvm.ts.pbt.backend.WitnessEvidenceDelta
import org.usvm.ts.pbt.backend.WitnessNativeReplay
import org.usvm.ts.pbt.backend.WitnessOracleOutcome
import org.usvm.ts.pbt.backend.WitnessReduction
import org.usvm.ts.pbt.backend.WitnessReductionStatus
import org.usvm.ts.pbt.backend.WitnessReplayConfiguration
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.backend.WitnessRole
import org.usvm.ts.pbt.backend.witnessManifestSha256
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.accepts
import org.usvm.ts.pbt.model.contains
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.validation.validatePropertyDefinition
import java.nio.file.Path

/** Owns admission, repeatability, final validation and evidence publication; Node owns native shrinking. */
internal class FastCheckWitnessCoordinator(
    private val client: FastCheckProcessClient,
    private val sourceRoots: List<Path>,
    private val tsconfig: String? = null,
) {
    /** Observes the complete oracle once before fixing the named target for confirmation and reduction. */
    fun confirmOriginal(
        property: PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        configuration: WitnessReplayConfiguration,
        deadline: WitnessReplayDeadline,
    ): WitnessEvidenceDelta {
        val originalTarget = candidate.target.role == WitnessRole.PROPERTY && candidate.target.binding == null &&
            candidate.target.id == property.predicate.exportName
        require(originalTarget) { "Original check discovery requires the complete property target" }

        val earlyStatus = validateCandidate(property, candidate, allowOriginalCheck = true)
        if (earlyStatus != null) return delta(candidate = candidate, status = earlyStatus)

        val observed = try {
            when (val replay = replay(property, candidate, requireNotNull(candidate.inputs), deadline)) {
                is FastCheckOperationResult.Completed -> replay.value
                is FastCheckOperationResult.TimedOut -> return delta(
                    candidate = candidate,
                    status = WitnessReplayStatus.TIMEOUT,
                    diagnostic = FastCheckDiagnosticCode.BACKEND_PROCESS_TIMEOUT,
                )
            }
        } catch (error: PbtBackendException) {
            return delta(candidate = candidate, status = error.replayStatus(), diagnostic = error.code)
        }

        val checkId = observed.failure?.checkId
        val originalOutcome = observed.outcome == WitnessOracleOutcome.FALSE ||
            observed.outcome == WitnessOracleOutcome.THREW
        val declaredCheck = property.assertions.any { assertion -> assertion.id == checkId }
        val expectedOutcome = candidate.target.expectedOutcome
        val matchingOutcome = expectedOutcome == null || expectedOutcome == observed.outcome
        val namedFailure = observed.admission == WitnessAdmission.ADMITTED && originalOutcome &&
            declaredCheck && matchingOutcome
        if (!namedFailure) {
            val observedStatus = classify(observed, candidate)
            val status = if (observedStatus == WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION) {
                WitnessReplayStatus.TARGET_MISMATCH
            } else {
                observedStatus
            }
            return delta(candidate = candidate, status = status, original = observed)
        }

        val namedCandidate = candidate.copy(
            target = candidate.target.copy(id = requireNotNull(checkId), expectedOutcome = observed.outcome),
        )
        return confirm(
            property = property,
            candidate = namedCandidate,
            configuration = configuration,
            deadline = deadline,
            expectedOriginal = observed.copy(targetMatched = true),
        )
    }

    fun confirm(
        property: PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        configuration: WitnessReplayConfiguration,
        deadline: WitnessReplayDeadline,
        expectedOriginal: WitnessNativeReplay? = null,
    ): WitnessEvidenceDelta {
        val validation = validatePropertyDefinition(property)
        if (!validation.isValid) {
            return delta(
                candidate = candidate,
                status = WitnessReplayStatus.PROPERTY_ERROR,
                diagnostic = validation.diagnostics.joinToString { diagnostic -> diagnostic.code },
            )
        }
        val earlyStatus = validateCandidate(property, candidate)
        if (earlyStatus != null) return delta(candidate = candidate, status = earlyStatus)

        val inputs = requireNotNull(candidate.inputs)
        val initial = verifyOriginal(property, candidate, inputs, deadline)
        val replayConfirmed = when (initial.status) {
            WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION,
            WitnessReplayStatus.CONFIRMED_HYPOTHESIS_REFUTATION,
            WitnessReplayStatus.CONFIRMED_COVERAGE,
            -> true
            else -> false
        }
        if (!replayConfirmed) return initial

        val original = requireNotNull(initial.originalReplay)
        val changedReplay = expectedOriginal != null && !repeatable(expectedOriginal, original)
        if (changedReplay) {
            return delta(
                candidate = candidate,
                status = WitnessReplayStatus.NONDETERMINISTIC,
                original = expectedOriginal,
            )
        }
        val status = initial.status

        var confirmedOriginal = original
        var coverageDiagnostic: String? = null
        val coverageRequest = configuration.coverageRequest
        if (coverageRequest != null) {
            try {
                val coverageResult = replay(property, candidate, inputs, deadline, coverageRequest)
                val covered = when (coverageResult) {
                    is FastCheckOperationResult.Completed -> coverageResult.value
                    is FastCheckOperationResult.TimedOut -> null
                }
                if (covered == null) {
                    coverageDiagnostic = FastCheckDiagnosticCode.BACKEND_PROCESS_TIMEOUT
                } else if (!repeatable(original, covered)) {
                    return delta(
                        candidate = candidate,
                        status = WitnessReplayStatus.NONDETERMINISTIC,
                        original = original,
                    )
                } else {
                    confirmedOriginal = covered
                }
            } catch (error: PbtBackendException) {
                coverageDiagnostic = error.code
            }
        }

        val reduction = reduce(property, candidate, confirmedOriginal, configuration, deadline)
        val finalReduction = validateReduction(property, candidate, original, reduction, deadline)

        val additions = confirmedEdges(candidate, confirmedOriginal) - configuration.knownEdges
        val refutations = if (candidate.target.role == WitnessRole.HYPOTHESIS) {
            original.events.filter { event ->
                event.binding == candidate.target.binding && event.guard && !event.holds
            }
        } else {
            emptyList()
        }

        return delta(
            candidate = candidate,
            status = status,
            original = confirmedOriginal,
            reduced = finalReduction.replay,
            reduction = finalReduction.reduction,
            edges = additions,
            diagnostic = coverageDiagnostic,
        ).copy(contextualRefutations = refutations)
    }

    private fun verifyOriginal(
        property: PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        inputs: List<JsConcreteValue>,
        deadline: WitnessReplayDeadline,
    ): WitnessEvidenceDelta {
        var firstReplay: WitnessNativeReplay? = null
        try {
            val first = replay(property, candidate, inputs, deadline)
            val original = when (first) {
                is FastCheckOperationResult.Completed -> first.value
                is FastCheckOperationResult.TimedOut -> return delta(
                    candidate = candidate,
                    status = WitnessReplayStatus.TIMEOUT,
                    diagnostic = FastCheckDiagnosticCode.BACKEND_PROCESS_TIMEOUT,
                )
            }
            firstReplay = original

            val second = replay(property, candidate, inputs, deadline)
            val confirmation = when (second) {
                is FastCheckOperationResult.Completed -> second.value
                is FastCheckOperationResult.TimedOut -> return delta(
                    candidate = candidate,
                    status = WitnessReplayStatus.TIMEOUT,
                    original = original,
                    diagnostic = FastCheckDiagnosticCode.BACKEND_PROCESS_TIMEOUT,
                )
            }
            val status = if (repeatable(original, confirmation)) {
                classify(original, candidate)
            } else {
                WitnessReplayStatus.NONDETERMINISTIC
            }

            return delta(candidate = candidate, status = status, original = original)
        } catch (error: PbtBackendException) {
            return delta(
                candidate = candidate,
                status = error.replayStatus(),
                original = firstReplay,
                diagnostic = error.code,
            )
        }
    }

    private fun validateReduction(
        property: PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        original: WitnessNativeReplay,
        reduction: WitnessReduction,
        deadline: WitnessReplayDeadline,
    ): ValidatedReduction {
        val inputs = reduction.inputs
        if (reduction.status != WitnessReductionStatus.EXHAUSTED || inputs == null) {
            return ValidatedReduction(reduction = reduction)
        }

        if (!inDeclaredSupport(property, inputs)) {
            return ValidatedReduction(
                reduction = reduction.copy(
                    status = WitnessReductionStatus.FAILED,
                    inputs = null,
                    diagnostic = "Reduced input left declared support",
                ),
            )
        }

        return try {
            val first = replay(property, candidate, inputs, deadline)
            val finalReplay = when (first) {
                is FastCheckOperationResult.Completed -> first.value
                is FastCheckOperationResult.TimedOut -> return reducedTimeout(reduction)
            }
            val second = replay(property, candidate, inputs, deadline)
            val finalConfirmation = when (second) {
                is FastCheckOperationResult.Completed -> second.value
                is FastCheckOperationResult.TimedOut -> return reducedTimeout(reduction)
            }
            val validFinal = repeatable(finalReplay, finalConfirmation) && sameTarget(finalReplay, original)
            if (validFinal) {
                ValidatedReduction(
                    reduction = reduction.copy(reducedEncodedBytes = encodedBytes(inputs)),
                    replay = finalReplay,
                )
            } else {
                ValidatedReduction(
                    reduction = reduction.copy(
                        status = WitnessReductionStatus.FAILED,
                        inputs = null,
                        diagnostic = "Final original oracle did not reproduce the same target",
                    ),
                )
            }
        } catch (error: PbtBackendException) {
            ValidatedReduction(
                reduction = reduction.copy(
                    status = WitnessReductionStatus.FAILED,
                    inputs = null,
                    diagnostic = error.code,
                ),
            )
        }
    }

    private fun reducedTimeout(reduction: WitnessReduction): ValidatedReduction = ValidatedReduction(
        reduction = reduction.copy(
            status = WitnessReductionStatus.TIMEOUT,
            inputs = null,
            diagnostic = FastCheckDiagnosticCode.BACKEND_PROCESS_TIMEOUT,
        ),
    )

    private fun validateCandidate(
        property: PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        allowOriginalCheck: Boolean = false,
    ): WitnessReplayStatus? {
        val sameRegistration = property.id == candidate.propertyId &&
            property.witnessManifestSha256() == candidate.manifestSha256 && property.toManifest() == candidate.manifest
        if (!sameRegistration) return WitnessReplayStatus.SOURCE_MISMATCH

        val inputs = candidate.inputs ?: return WitnessReplayStatus.UNREPRESENTABLE_INPUT
        if (!inputs.hasWitnessEncoding()) return WitnessReplayStatus.UNREPRESENTABLE_INPUT
        if (!inDeclaredSupport(property, inputs)) return WitnessReplayStatus.DOMAIN_REJECTED

        val entryRelation = candidate.target.entryRelation
        if (entryRelation != null) {
            val numericInputs = inputs.getOrNull(entryRelation.leftInputIndex) is JsConcreteValue.Number &&
                inputs.getOrNull(entryRelation.rightInputIndex) is JsConcreteValue.Number
            if (!numericInputs) return WitnessReplayStatus.UNSUPPORTED_REPLAY
        }

        val localRelation = candidate.target.dslNumericLocalRelation
        if (localRelation != null && inputs.getOrNull(localRelation.inputIndex) !is JsConcreteValue.Number) {
            return WitnessReplayStatus.UNSUPPORTED_REPLAY
        }

        val sourceModules = candidate.sources.mapTo(hashSetOf()) { source -> source.module }
        val requiredModules = listOfNotNull(property.predicate.module, property.precondition?.module)
        val validSources = candidate.sources.all { source -> source.sha256.matches(SHA256_REGEX) } &&
            sourceModules.size == candidate.sources.size && requiredModules.all { module -> module in sourceModules }
        if (!validSources) return WitnessReplayStatus.UNSUPPORTED_REPLAY

        val target = candidate.target
        val binding = target.binding
        val declaredCheck = property.assertions.any { assertion -> assertion.id == target.id }
        val diagnosticCheck = property.predicate.checkResultExportName != null && declaredCheck
        val unselectedOriginalCheck = allowOriginalCheck && property.predicate.checkResultExportName != null &&
            target.id == property.predicate.exportName
        val validCheck = target.role != WitnessRole.PROPERTY || if (binding == null) {
            unselectedOriginalCheck || diagnosticCheck ||
                property.assertions.isEmpty() && target.id == property.predicate.exportName
        } else {
            binding.pointId == target.id && declaredCheck
        }
        if (!validCheck) return WitnessReplayStatus.UNSUPPORTED_REPLAY

        return null
    }

    private fun replay(
        property: PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        inputs: List<JsConcreteValue>,
        deadline: WitnessReplayDeadline,
        coverageRequest: PropertyCoverageRequest? = null,
    ): FastCheckOperationResult<WitnessNativeReplay> {
        val result = client.witness(
            request = request(
                property = property,
                candidate = candidate,
                operation = FastCheckWitnessOperation(
                    mode = "replay",
                    inputs = inputs,
                    target = candidate.target,
                    sources = candidate.sources,
                ),
                timeoutMillis = deadline.remainingMillis().coerceAtLeast(1),
                coverageRequest = coverageRequest,
            ),
            remainingMillis = deadline::remainingMillis,
        )

        return when (result) {
            is FastCheckOperationResult.Completed -> FastCheckOperationResult.Completed(
                value = requireNotNull(result.value.witnessReplay),
            )
            is FastCheckOperationResult.TimedOut -> result
        }
    }

    private fun reduce(
        property: PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        original: WitnessNativeReplay,
        configuration: WitnessReplayConfiguration,
        deadline: WitnessReplayDeadline,
    ): WitnessReduction {
        val originalBytes = encodedBytes(requireNotNull(candidate.inputs))
        if (!configuration.shrink) {
            return WitnessReduction(status = WitnessReductionStatus.DISABLED, originalEncodedBytes = originalBytes)
        }

        val budget = deadline.remainingMillis() - configuration.validationReserveMillis
        if (budget <= 0) {
            return WitnessReduction(status = WitnessReductionStatus.TIMEOUT, originalEncodedBytes = originalBytes)
        }

        val shrinkDeadline = WitnessReplayDeadline(timeoutMillis = budget)
        return try {
            val result = client.witness(
                request = request(
                    property = property,
                    candidate = candidate,
                    operation = FastCheckWitnessOperation(
                        mode = "reduce",
                        inputs = requireNotNull(candidate.inputs),
                        target = candidate.target,
                        sources = candidate.sources,
                        expected = original.copy(coverage = null),
                    ),
                    timeoutMillis = budget,
                ),
                remainingMillis = { minOf(deadline.remainingMillis(), shrinkDeadline.remainingMillis()) },
            )
            when (result) {
                is FastCheckOperationResult.Completed -> requireNotNull(result.value.witnessReduction)
                    .copy(originalEncodedBytes = originalBytes)
                is FastCheckOperationResult.TimedOut -> WitnessReduction(
                    status = WitnessReductionStatus.TIMEOUT,
                    originalEncodedBytes = originalBytes,
                    diagnostic = FastCheckDiagnosticCode.BACKEND_PROCESS_TIMEOUT,
                )
            }
        } catch (error: PbtBackendException) {
            WitnessReduction(
                status = WitnessReductionStatus.FAILED,
                originalEncodedBytes = originalBytes,
                diagnostic = error.code,
            )
        }
    }

    private fun request(
        property: PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        operation: FastCheckWitnessOperation,
        timeoutMillis: Long,
        coverageRequest: PropertyCoverageRequest? = null,
    ): FastCheckExecutionRequest {
        check(candidate.propertyId == property.id)
        return FastCheckExecutionRequest(
            manifest = property.toManifest(),
            sourceRoots = sourceRoots.map(Path::toString),
            tsconfig = tsconfig,
            numRuns = 1,
            timeoutMillis = timeoutMillis,
            witness = operation,
            coverageRequest = coverageRequest,
        )
    }

    private fun confirmedEdges(
        candidate: ExternalWitnessCandidate,
        original: WitnessNativeReplay,
    ): Set<WitnessCoverageEdge> {
        val coverage = original.coverage ?: return emptySet()
        val sourcesByPath = candidate.sources.flatMap { source ->
            sourceRoots.map { root -> root.resolve(source.module).normalize().toString() to source }
        }.toMap()

        return buildSet {
            for (file in coverage.files) {
                val source = sourcesByPath[file.path] ?: continue
                val fileHasDiagnostic = coverage.diagnostics.any { diagnostic ->
                    diagnostic.path == null || diagnostic.path == file.path
                }
                if (fileHasDiagnostic) continue

                for (branch in file.branches) {
                    if (branch.type != "if" || branch.arms.size != 2) continue

                    branch.arms.forEachIndexed { index, arm ->
                        if (arm.hits > 0) {
                            add(WitnessCoverageEdge(source = source, location = branch.location, outcome = index == 0))
                        }
                    }
                }
            }
        }
    }

    private fun delta(
        candidate: ExternalWitnessCandidate,
        status: WitnessReplayStatus,
        original: WitnessNativeReplay? = null,
        reduced: WitnessNativeReplay? = null,
        reduction: WitnessReduction = WitnessReduction(status = WitnessReductionStatus.NOT_ELIGIBLE),
        edges: Set<WitnessCoverageEdge> = emptySet(),
        diagnostic: String? = null,
    ) = WitnessEvidenceDelta(
        candidate = candidate,
        status = status,
        originalReplay = original,
        reducedReplay = reduced,
        reduction = reduction,
        confirmedEdgeAdditions = edges,
        backendId = FastCheckBackend.FAST_CHECK_BACKEND_ID,
        backendVersion = FastCheckRuntimeMetadata.fastCheckVersion,
        diagnostic = diagnostic,
    )

    private companion object {
        val SHA256_REGEX = Regex("[0-9a-f]{64}")

        fun inDeclaredSupport(property: PropertyDefinition, inputs: List<JsConcreteValue>): Boolean =
            inputs.size == property.inputs.size &&
                property.inputs.zip(inputs).all { (input, value) -> value in input.domain } &&
                property.generator?.accepts(inputs) != false

        fun encodedBytes(inputs: List<JsConcreteValue>): Int =
            PropertyManifestJson.json.encodeToUtf8SafeString(inputs).toByteArray(Charsets.UTF_8).size

        fun repeatable(first: WitnessNativeReplay, second: WitnessNativeReplay): Boolean =
            first.diagnostic != "nondeterministic" && second.diagnostic != "nondeterministic" &&
                first.copy(coverage = null) == second.copy(coverage = null)

        fun sameTarget(actual: WitnessNativeReplay, expected: WitnessNativeReplay): Boolean =
            actual.admission == WitnessAdmission.ADMITTED && actual.targetMatched &&
                actual.outcome == expected.outcome && actual.failure?.checkId == expected.failure?.checkId &&
                actual.failure?.errorName == expected.failure?.errorName &&
                actual.failure?.message == expected.failure?.message

        fun classify(replay: WitnessNativeReplay, candidate: ExternalWitnessCandidate): WitnessReplayStatus = when {
            replay.diagnostic == FastCheckDiagnosticCode.ENTRY_POINT_SOURCE_HASH_MISMATCH ->
                WitnessReplayStatus.SOURCE_MISMATCH
            replay.outcome == WitnessOracleOutcome.ERROR -> WitnessReplayStatus.PROPERTY_ERROR
            replay.admission == WitnessAdmission.DOMAIN_REJECTED -> WitnessReplayStatus.DOMAIN_REJECTED
            replay.admission == WitnessAdmission.PRECONDITION_REJECTED -> WitnessReplayStatus.PRECONDITION_REJECTED
            replay.diagnostic == "nondeterministic" -> WitnessReplayStatus.NONDETERMINISTIC
            !replay.targetMatched && replay.outcome == WitnessOracleOutcome.HOLDS &&
                candidate.target.role == WitnessRole.PROPERTY -> WitnessReplayStatus.PROPERTY_HOLDS
            !replay.targetMatched -> WitnessReplayStatus.TARGET_MISMATCH
            replay.outcome == WitnessOracleOutcome.FALSE || replay.outcome == WitnessOracleOutcome.THREW ->
                WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION
            candidate.target.role == WitnessRole.HYPOTHESIS -> WitnessReplayStatus.CONFIRMED_HYPOTHESIS_REFUTATION
            else -> WitnessReplayStatus.CONFIRMED_COVERAGE
        }

        fun PbtBackendException.replayStatus(): WitnessReplayStatus = when {
            code.startsWith("js-value.") || code.startsWith("js-number.") -> WitnessReplayStatus.UNREPRESENTABLE_INPUT
            else -> WitnessReplayStatus.REPLAY_ERROR
        }
    }
}

private data class ValidatedReduction(
    val reduction: WitnessReduction,
    val replay: WitnessNativeReplay? = null,
)
