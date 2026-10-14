package org.usvm.ts.calls

import kotlinx.serialization.decodeFromString
import org.usvm.SolverType
import org.usvm.machine.call.TsBuiltInUnknownCallModels
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal fun runClassCatalog(args: List<String>) {
    require(args.isEmpty()) { "class-catalog takes no arguments" }
    val identity = CallsModelSetIdentity(
        ids = TsBuiltInUnknownCallModels.catalog().modelIds.sorted().toSet(),
        toolRevision = CallsBuildIdentity.toolRevision,
    )
    System.out.appendLine(encodeCallsArtifact(identity))
}

internal fun runClassPrepare(args: List<String>) {
    require(args.size == 2) { "class-prepare requires <manifest.json> <output-directory>" }
    val manifestPath = Path.of(args[0]).toRealPath()
    val manifest = decodeClassArtifact<CallsClassExperimentManifest>(manifestPath)
    manifest.verifyBuild(CallsBuildIdentity.toolRevision, CallsBuildIdentity.nativeFrontendRevision)
    val output = Path.of(args[1]).toAbsolutePath().normalize()
    Files.createDirectories(output)
    require(Files.list(output).use { it.findAny().isEmpty }) { "Preparation output must be empty" }
    writeCallsArtifactAtomically(output.resolve("manifest.json"), encodeCallsArtifact(manifest))
    for (classCase in manifest.classes) {
        val classDirectory = output.resolve(classCase.classId)
        Files.createDirectories(classDirectory)
        val preparationStarted = TimeSource.Monotonic.markNow()
        val preparation = runCatching {
            val originalRoot = classCase.originalRoot(requireNotNull(manifestPath.parent))
            classCase.verifySources(originalRoot, verifyCheckout = true)
            val overlay = classDirectory.resolve("source")
            Files.createDirectories(overlay)
            classCase.sourceSha256.keys.forEach { relative ->
                val copied = overlay.resolve(relative)
                Files.createDirectories(copied.parent)
                Files.copy(originalRoot.resolve(relative), copied)
            }
            val instrumentation = CallsClassSourceInstrumentation.create(
                sourceRoot = originalRoot,
                sourceFile = classCase.surface.sourceFile,
                className = classCase.surface.exportName,
            )
            instrumentation.validateSurface(classCase.surface)
            val project = CallsProjectCase(
                projectId = classCase.projectId,
                revision = classCase.revision,
                sourceRoot = overlay.toString(),
                development = true,
                functions = emptyList(),
            )
            val harness = prepareCallsClassHarness(
                sourceRoot = overlay,
                project = project,
                surface = classCase.surface,
                maxSequenceLength = manifest.maxSequenceLength,
            )
            val harnessFile = harness.functions.first().sourceFile
            classCase.verifySources(originalRoot, verifyCheckout = true)
            CallsClassPreparedRecord(
                classCase = classCase,
                originalSourceRoot = originalRoot.toString(),
                scenarios = harness.scenarios,
                functions = harness.functions,
                observations = harness.observations,
                harnessFile = harnessFile,
                harnessSha256 = callsClassSourceSha256(Files.readAllBytes(overlay.resolve(harnessFile))),
                instrumentation = instrumentation,
                preparationElapsedMillis = preparationStarted.elapsedNow().inWholeMilliseconds,
            )
        }
        val prepared = preparation.getOrNull()
        if (prepared != null) {
            writeCallsArtifactAtomically(classDirectory.resolve("class.json"), encodeCallsArtifact(prepared))
            val statementCount = prepared.instrumentation.universe.statements.size
            System.out.appendLine("Prepared ${classCase.classId}: $statementCount statements")
        } else {
            val error = requireNotNull(preparation.exceptionOrNull())
            val failure = CallsClassPreparationFailure(
                classId = classCase.classId,
                diagnostic = error.message ?: error::class.java.name,
                preparationElapsedMillis = preparationStarted.elapsedNow().inWholeMilliseconds,
            )
            writeCallsArtifactAtomically(
                classDirectory.resolve("preparation-failure.json"),
                encodeCallsArtifact(failure),
            )
            System.out.appendLine(encodeCallsArtifact(failure))
        }
    }
}

/** Source/native admission is independent of the diagnostic pilot and any treatment completion rate. */
internal fun runClassPreflight(
    args: List<String>,
    searchFunction: (CallsClassSearchRequest) -> CallsClassSearchResult = CurrentTsCallsClassCoverageEngine()::search,
) {
    require(args.size == CLASS_PREFLIGHT_ARGUMENT_COUNT) {
        "class-preflight requires <prepared-directory> <class-id> <preflight-ms>"
    }
    val source = loadPreparedClass(Path.of(args[0]), classId = args[1])
    val budgetMillis = args[2].toLong()
    require(budgetMillis >= source.manifest.solverLimitMillis && budgetMillis >= source.manifest.timeQuantumMillis)
    val output = source.directory.resolve("preflight")
    Files.createDirectories(output)
    require(Files.list(output).use { it.findAny().isEmpty }) { "Class preflight already started" }
    val sourceAdmissionFailures = callsClassSourceAdmissionFailures(
        harness = source.harness,
        expectedNativeFrontendRevision = source.manifest.nativeFrontendRevision,
    )
    val nativeSmoke = if (sourceAdmissionFailures.isEmpty()) {
        callsClassNativeConstructorSmoke(
            harness = source.harness,
            instrumentation = source.record.instrumentation,
            seed = source.manifest.seeds.first(),
            timeoutMillis = CLASS_REPLAY_TIMEOUT_MILLIS,
        )
    } else {
        null
    }
    writeCallsArtifactAtomically(output.resolve("native-smoke.json"), encodeCallsArtifact(nativeSmoke))
    val candidatesPath = output.resolve("candidates.jsonl")
    val search = searchFunction(
        source.searchRequest(
            profile = CallsExperimentProfile.FROZEN_FRESH,
            seed = source.manifest.seeds.first(),
            budgetMillis = budgetMillis,
            requireEntryPointWitness = true,
            onCandidate = { appendCallsJournalDurably(candidatesPath, encodeCallsArtifact(it)) },
        ),
    )
    source.verify()
    require(readCallsJournal<CallsCoverageCandidate>(candidatesPath) == search.search.candidates) {
        "Preflight candidate journal differs"
    }
    writeCallsArtifactAtomically(output.resolve("search.json"), encodeCallsArtifact(search))
    val events = replayClassCandidates(source, search, output)
    val acceptedIds = events.filter { it.oracle.accepted && it.error == null }.mapTo(hashSetOf()) { it.scenarioId }
    val missing = source.record.scenarios.map { it.id }.filter { it !in acceptedIds }
    val pilotDiagnostics = callsClassPilotDiagnostics(search, events, missing)
    val nativeInfrastructureFailures = callsClassPilotNativeFailures(events, source.record.instrumentation.universe)
    val result = callsClassPreflightResult(
        classCase = source.record.classCase,
        preparedSha256 = source.preparedSha256,
        toolRevision = source.manifest.toolRevision,
        search = search,
        replays = events,
        missingScenarios = missing,
        pilotDiagnostics = pilotDiagnostics,
        sourceAdmissionFailures = sourceAdmissionFailures,
        nativeSmoke = nativeSmoke,
        nativeInfrastructureFailures = nativeInfrastructureFailures,
    )
    source.verify()
    writeCallsArtifactAtomically(source.directory.resolve("preflight.json"), encodeCallsArtifact(result))
    System.out.appendLine(encodeCallsArtifact(result))
}

private fun callsClassPilotDiagnostics(
    search: CallsClassSearchResult,
    events: List<CallsClassReplayEvent>,
    missing: List<String>,
): List<String> = buildList {
    if (missing.isNotEmpty()) add("No exact returned-value/state witness for scenarios: $missing")
    if (search.search.engineFailed) add("Symbolic engine failed")
    if (search.search.unsupportedCall) add("Technical preflight stopped an unsupported call")
    if (search.search.runtimeLimited) addAll(search.search.runtimeLimitationReasons)
    if (search.search.extractionFailures.isNotEmpty()) addAll(search.search.extractionFailures)
    if (search.search.candidateCapReached) add("Technical preflight reached candidate cap")
    if (search.search.status == CallsCoverageSearchStatus.TIMEOUT) add("Symbolic pilot reached the time budget")
    if (events.any { it.error != null }) add("Concrete preflight replay failed")
    if (events.any { it.strictOracleApplicable && !it.oracle.accepted }) {
        add("Technical preflight observation oracle rejected a candidate")
    }
    val terminalFailure = search.search.status in setOf(
        CallsCoverageSearchStatus.UNSUPPORTED,
        CallsCoverageSearchStatus.UNMAPPED,
        CallsCoverageSearchStatus.AMBIGUOUS,
        CallsCoverageSearchStatus.UNKNOWN,
        CallsCoverageSearchStatus.TOOL_ERROR,
    )
    if (terminalFailure) add(search.search.diagnostic ?: search.search.status.name)
}

internal fun callsClassPilotNativeFailures(
    events: List<CallsClassReplayEvent>,
    universe: CallsClassSourceUniverse,
): List<String> = buildList {
    for (event in events) {
        val original = event.original
        val instrumented = event.instrumented
        if (original != null && instrumented != null) {
            val failure = callsClassNativeInfrastructureFailure(original, instrumented)
            if (failure != null) add("${event.scenarioId}: $failure")
        }
        if (instrumented != null) {
            val checkedIds = runCatching { universe.checkedCoveredIds(instrumented.coveredStatementKeys) }
            checkedIds.exceptionOrNull()?.let { add(it.message ?: it::class.java.name) }
        }
    }
}

internal fun runClassCell(
    args: List<String>,
    searchFunction: (CallsClassSearchRequest) -> CallsClassSearchResult = CurrentTsCallsClassCoverageEngine()::search,
) {
    require(args.size == CLASS_CELL_ARGUMENT_COUNT) {
        "class-cell requires <prepared-directory> <class-id> <profile> <seed> <budget-ms> <output-directory>"
    }
    val cellStarted = TimeSource.Monotonic.markNow()
    val arguments = args.iterator()
    val preparedDirectory = Path.of(arguments.next())
    val classId = arguments.next()
    val source = loadPreparedClass(preparedDirectory, classId = classId)
    val profile = CallsExperimentProfile.valueOf(arguments.next())
    val seed = arguments.next().toLong()
    val budgetMillis = arguments.next().toLong()
    require(seed in source.manifest.seeds && budgetMillis in source.manifest.budgetsMillis) {
        "Cell is outside the frozen paired seed/budget schedule"
    }
    val preflightPath = source.directory.resolve("preflight.json")
    val preflight = decodeClassArtifact<CallsClassPreflightResult>(preflightPath)
    source.validatePreflightAdmission(preflight)
    val output = Path.of(arguments.next()).toAbsolutePath().normalize()
    Files.createDirectories(output)
    val identity = CallsClassCellIdentity(
        experimentId = source.manifest.experimentId,
        toolRevision = source.manifest.toolRevision,
        nativeFrontendRevision = source.manifest.nativeFrontendRevision,
        manifestSha256 = source.manifestSha256,
        preparedSha256 = source.preparedSha256,
        preflightSha256 = callsArtifactSha256(Files.readAllBytes(preflightPath)),
        universeSha256 = source.record.instrumentation.universe.universeSha256,
        classId = source.record.classCase.classId,
        profile = profile,
        seed = seed,
        budgetMillis = budgetMillis,
    )
    val identityPath = output.resolve("identity.json")
    if (Files.exists(identityPath)) {
        require(decodeClassArtifact<CallsClassCellIdentity>(identityPath) == identity) { "Class cell identity changed" }
    } else {
        require(Files.list(output).use { it.findAny().isEmpty }) { "Cell output is not empty" }
        writeCallsArtifactAtomically(identityPath, encodeCallsArtifact(identity))
    }
    require(!Files.exists(output.resolve("result.json"))) { "Class cell is already complete" }
    val candidatesPath = output.resolve("candidates.jsonl")
    val searchPath = output.resolve("search.json")
    val search = if (Files.exists(searchPath)) {
        decodeClassArtifact<CallsClassSearchResult>(searchPath)
    } else {
        require(!Files.exists(candidatesPath)) { "Interrupted class search requires investigation before restart" }
        val result = searchFunction(
            source.searchRequest(
                profile = profile,
                seed = seed,
                budgetMillis = budgetMillis,
                requireEntryPointWitness = false,
                onCandidate = { appendCallsJournalDurably(candidatesPath, encodeCallsArtifact(it)) },
            ),
        )
        source.verify()
        require(readCallsJournal<CallsCoverageCandidate>(candidatesPath) == result.search.candidates) {
            "Class candidate journal differs from search result"
        }
        writeCallsArtifactAtomically(searchPath, encodeCallsArtifact(result))
        result
    }
    require(readCallsJournal<CallsCoverageCandidate>(candidatesPath) == search.search.candidates) {
        "Persisted class search and candidate journal differ"
    }
    val events = replayClassCandidates(source, search, output)
    source.verify()
    val result = CallsClassCellResult(
        identity = identity,
        search = search,
        checkpoints = callsClassCoverageCheckpoints(
            search = search,
            events = events,
            universe = source.record.instrumentation.universe,
            budgetMillis = budgetMillis,
        ),
        replayErrors = events.count { it.error != null },
        oracleRejections = events.count { it.strictOracleApplicable && !it.oracle.accepted },
        replayElapsedMillis = events.sumOf { it.replayElapsedMillis },
        cellElapsedMillis = cellStarted.elapsedNow().inWholeMilliseconds,
        primaryInferenceEligible = preflight.primaryInferenceEligible,
        correctnessFrontierFailures = preflight.correctnessFrontierFailures,
        nativeInfrastructureFailures = callsClassPilotNativeFailures(events, source.record.instrumentation.universe),
        nativeCoverageRejections = events.count { !it.nativeCoverage.accepted },
        strictOracleNotApplicable = events.count { !it.strictOracleApplicable },
    )
    writeCallsArtifactAtomically(output.resolve("result.json"), encodeCallsArtifact(result))
    System.out.appendLine(encodeCallsArtifact(result))
}

private fun PreparedClassSource.validatePreflightAdmission(preflight: CallsClassPreflightResult) {
    require(preflight.admissionVersion == 1) { "Class requires a new profile-independent admission check" }
    require(preflight.eligible) { "Class source/native admission failed: ${preflight.reasons}" }
    require(preflight.correctnessFrontierFailures == record.classCase.correctnessFrontierFailures) {
        "Frozen correctness-frontier evidence differs from the prepared class"
    }
    val expectedPrimaryEligibility = preflight.eligible &&
        preflight.nativeSmoke?.status == CallsClassNativeSmokeStatus.VERIFIED &&
        preflight.correctnessFrontierFailures.isEmpty()
    require(preflight.primaryInferenceEligible == expectedPrimaryEligibility) { "Primary admission evidence differs" }
    require(preflight.classId == record.classCase.classId)
    val preflightIdentityMatches = preflight.preparedSha256 == preparedSha256 &&
        preflight.toolRevision == manifest.toolRevision
    require(preflightIdentityMatches) { "Class preflight identity differs" }
}

/** Parsing, domain binding and source coverage selection run without a symbolic treatment or machine. */
internal fun callsClassSourceAdmissionFailures(
    harness: CallsClassPreparedHarness,
    expectedNativeFrontendRevision: String,
): List<String> = runCatching {
    val engine = CurrentTsCallsSymbolicEngine()
    val preparations = harness.functions.map { function ->
        engine.prepareFunction(
            CallsFunctionPreflightRequest(
                sourceRoot = harness.sourceRoot,
                project = harness.project,
                function = function,
                expectedNativeFrontendRevision = expectedNativeFrontendRevision,
                verifySourceCheckout = false,
            ),
        )
    }
    val failures = preparations.filterIsInstance<CallsFunctionPreparation.Rejected>().map {
        "${it.reasonCode}: ${it.diagnostic}"
    }
    if (failures.isNotEmpty()) {
        failures
    } else {
        val first = preparations.first() as CallsFunctionPreparation.Eligible
        val scope = resolveCallsSymbolicCoverageScope(
            preparation = first,
            target = CallsSymbolicCoverageClass(harness.surface.sourceFile, harness.surface.exportName),
        )
        if (scope is CallsSymbolicCoverageScope.Rejected) listOf(scope.diagnostic) else emptyList()
    }
}.getOrElse { listOf("Source preparation failed: ${it.message ?: it::class.java.name}") }

private class PreparedClassSource(
    val manifest: CallsClassExperimentManifest,
    val manifestSha256: String,
    val directory: Path,
    val record: CallsClassPreparedRecord,
    val preparedSha256: String,
    val harness: CallsClassPreparedHarness,
) {
    fun verify() {
        record.verifyOriginal()
        record.harness(directory)
    }

    fun searchRequest(
        profile: CallsExperimentProfile,
        seed: Long,
        budgetMillis: Long,
        requireEntryPointWitness: Boolean,
        onCandidate: (CallsCoverageCandidate) -> Unit,
    ): CallsClassSearchRequest = CallsClassSearchRequest(
        harness = harness,
        profile = profile,
        frozenModelIds = manifest.modelSet.ids,
        expectedNativeFrontendRevision = manifest.nativeFrontendRevision,
        seed = seed,
        budget = budgetMillis.milliseconds,
        timeQuantum = manifest.timeQuantumMillis.milliseconds,
        solverQueryLimit = manifest.solverLimitMillis.milliseconds,
        solverType = SolverType.valueOf(manifest.solver),
        candidateCap = manifest.candidateCap,
        requireEntryPointWitness = requireEntryPointWitness,
        onCandidate = onCandidate,
    )
}

private fun loadPreparedClass(preparedDirectory: Path, classId: String): PreparedClassSource {
    val root = preparedDirectory.toRealPath()
    val manifestPath = root.resolve("manifest.json")
    val manifest = decodeClassArtifact<CallsClassExperimentManifest>(manifestPath)
    manifest.verifyBuild(CallsBuildIdentity.toolRevision, CallsBuildIdentity.nativeFrontendRevision)
    val classCase = manifest.classes.single { it.classId == classId }
    val directory = root.resolve(classId).toRealPath()
    require(directory.startsWith(root))
    val recordPath = directory.resolve("class.json")
    val record = decodeClassArtifact<CallsClassPreparedRecord>(recordPath)
    require(record.classCase == classCase) { "Prepared class differs from manifest" }
    record.verifyOriginal()

    return PreparedClassSource(
        manifest = manifest,
        manifestSha256 = callsArtifactSha256(Files.readAllBytes(manifestPath)),
        directory = directory,
        record = record,
        preparedSha256 = callsArtifactSha256(Files.readAllBytes(recordPath)),
        harness = record.harness(directory),
    )
}

private fun replayClassCandidates(
    source: PreparedClassSource,
    search: CallsClassSearchResult,
    output: Path,
): List<CallsClassReplayEvent> {
    val path = output.resolve("replays.jsonl")
    val events = readCallsJournal<CallsClassReplayEvent>(path).toMutableList()
    require(events.map { it.index } == events.indices.toList() && events.size <= search.search.candidates.size) {
        "Class replay journal has missing, duplicate, or excess indices"
    }
    events.forEachIndexed { index, event ->
        validateCallsClassReplayEvent(
            candidate = search.search.candidates[index],
            index = index,
            event = event,
            universe = source.record.instrumentation.universe,
        )
    }
    val scenarios = source.record.scenarios.withIndex().associate { it.value.id to it.index }
    for (index in search.search.candidates.indices.drop(events.size)) {
        source.verify()
        val candidate = search.search.candidates[index]
        val scenarioId = requireNotNull(candidate.entryPointId)
        val scenarioIndex = scenarios.getValue(scenarioId)
        val function = source.record.functions[scenarioIndex]
        val observations = source.record.observations[scenarioIndex]
        val replayStarted = TimeSource.Monotonic.markNow()
        val original = runCatching {
            OriginalTypeScriptClassReplayer(
                sourceRoot = source.harness.sourceRoot,
                function = function,
                observationFields = observations,
            ).use { it.replay(inputs = candidate.inputs, timeoutMillis = CLASS_REPLAY_TIMEOUT_MILLIS) }
        }
        val instrumentation = source.record.instrumentation
        val instrumented = runCatching {
            OriginalTypeScriptClassReplayer(
                sourceRoot = source.harness.sourceRoot,
                function = function,
                observationFields = observations,
                sourceOverride = instrumentation.instrumentedSource,
                overrideSourceFile = source.record.classCase.surface.sourceFile,
                counterGlobalName = instrumentation.hitGlobalName,
            ).use { it.replay(inputs = candidate.inputs, timeoutMillis = CLASS_REPLAY_TIMEOUT_MILLIS) }
        }
        source.verify()
        val originalValue = original.getOrNull()
        val instrumentedValue = instrumented.getOrNull()
        val validation = runCatching {
            require(originalValue != null && instrumentedValue != null) { "Concrete replay failed" }
            require(originalValue.inputs == candidate.inputs && instrumentedValue.inputs == candidate.inputs) {
                "Class replay input snapshot differs from candidate"
            }
            instrumentation.universe.checkedCoveredIds(instrumentedValue.coveredStatementKeys)
            classCandidateStrictOracle(candidate, originalValue, instrumentedValue)
        }
        val failures = listOfNotNull(
            original.exceptionOrNull()?.let { "original: ${it.message ?: it::class.java.name}" },
            instrumented.exceptionOrNull()?.let { "instrumented: ${it.message ?: it::class.java.name}" },
            validation.exceptionOrNull()?.let { "validation: ${it.message ?: it::class.java.name}" },
        )
        val replayError = failures.takeIf { it.isNotEmpty() }?.joinToString(separator = "; ")
        val event = CallsClassReplayEvent(
            index = index,
            scenarioId = scenarioId,
            original = originalValue,
            instrumented = instrumentedValue,
            oracle = validation.getOrNull() ?: CallsClassOracleResult(
                accepted = false,
                diagnostic = "Concrete replay failed",
            ),
            error = replayError,
            replayElapsedMillis = replayStarted.elapsedNow().inWholeMilliseconds,
            nativeCoverage = callsClassNativeCoverageAcceptance(
                original = originalValue,
                instrumented = instrumentedValue,
                universe = instrumentation.universe,
                error = replayError,
            ),
            origin = candidate.origin,
            strictOracleApplicable = candidate.origin == CallsCoverageCandidateOrigin.TERMINATED,
        )
        appendCallsJournalDurably(path, encodeCallsArtifact(event))
        events += event
    }

    return events
}

internal fun validateCallsClassReplayEvent(
    candidate: CallsCoverageCandidate,
    index: Int,
    event: CallsClassReplayEvent,
    universe: CallsClassSourceUniverse,
) {
    require(event.index == index && event.scenarioId == candidate.entryPointId) {
        "Persisted replay index or scenario differs from candidate"
    }
    require(event.origin == candidate.origin) { "Persisted replay candidate origin differs" }
    require(event.strictOracleApplicable == (candidate.origin == CallsCoverageCandidateOrigin.TERMINATED)) {
        "Persisted symbolic oracle applicability differs from candidate origin"
    }
    if (event.error == null) {
        val original = requireNotNull(event.original)
        val instrumented = requireNotNull(event.instrumented)
        require(original.inputs == candidate.inputs && instrumented.inputs == candidate.inputs) {
            "Persisted replay input snapshot differs"
        }
        universe.checkedCoveredIds(instrumented.coveredStatementKeys)
        val expectedOracle = classCandidateStrictOracle(candidate, original, instrumented)
        require(event.oracle == expectedOracle) { "Persisted replay oracle decision differs" }
    } else {
        require(!event.oracle.accepted) { "Failed persisted replay is marked accepted" }
    }

    val expectedNative = callsClassNativeCoverageAcceptance(
        original = event.original,
        instrumented = event.instrumented,
        universe = universe,
        error = event.error,
    )
    require(event.nativeCoverage == expectedNative) { "Persisted native coverage decision differs" }
}

private fun classCandidateStrictOracle(
    candidate: CallsCoverageCandidate,
    original: CallsClassReplayObservation,
    instrumented: CallsClassReplayObservation,
): CallsClassOracleResult = when (candidate.origin) {
    CallsCoverageCandidateOrigin.TERMINATED -> compareCallsClassObservations(
        symbolic = candidate.observation,
        original = original.observation,
        instrumented = instrumented.observation,
    )
    CallsCoverageCandidateOrigin.SUSPENDED -> CallsClassOracleResult(
        accepted = false,
        diagnostic = "Not applicable: suspended input has no symbolic completion or observation",
    )
}

private inline fun <reified T> decodeClassArtifact(path: Path): T =
    CallsExperimentJson.json.decodeFromString(Files.readString(path))

private const val CLASS_REPLAY_TIMEOUT_MILLIS = 20_000L

private const val CLASS_PREFLIGHT_ARGUMENT_COUNT = 3
private const val CLASS_CELL_ARGUMENT_COUNT = 6
