package org.usvm.ts.pbt.calls

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.toConcreteBoolValue
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.contains
import org.usvm.util.mkRegisterStackLValue
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.time.TimeSource

private const val BYTE_MASK = 0xff

internal class CurrentTsCallsSymbolicEngine : CallsSymbolicEngine {
    private val verifiedProjects = mutableMapOf<Path, String>()
    private var verifiedNativeFrontend: Pair<Path, String>? = null

    override fun search(request: CallsSymbolicSearchRequest): CallsSymbolicSearchResult {
        val startedAt = TimeSource.Monotonic.markNow()
        val unsupportedInput = request.function.inputs.firstOrNull { input ->
            input.domain != BooleanDomain && input.domain !is NumberDomain
        }
        if (unsupportedInput != null) {
            return result(
                status = CallsSymbolicStatus.UNSUPPORTED,
                startedAt = startedAt,
                diagnostic = "Only boolean and number input domains are supported; found ${unsupportedInput.domain}",
            )
        }

        return runCatching {
            searchSupported(request = request, startedAt = startedAt)
        }.getOrElse { error ->
            result(
                status = CallsSymbolicStatus.TOOL_ERROR,
                startedAt = startedAt,
                diagnostic = error.message ?: error::class.java.name,
            )
        }
    }

    @Suppress("LongMethod")
    private fun searchSupported(
        request: CallsSymbolicSearchRequest,
        startedAt: TimeSource.Monotonic.ValueTimeMark,
    ): CallsSymbolicSearchResult {
        verifyGitCheckoutOnce(
            checkout = request.sourceRoot,
            expectedRevision = request.project.revision,
            cache = verifiedProjects,
        )
        verifyNativeFrontendOnce(
            expectedRevision = request.expectedNativeFrontendRevision,
            expectedSha256 = request.expectedNativeFrontendSha256,
        )

        val source = request.sourceRoot.resolve(request.function.sourceFile).normalize()
        require(source.startsWith(request.sourceRoot)) { "Function source escapes its frozen source root" }
        val actualSourceHash = Files.readAllBytes(source).sha256()
        if (actualSourceHash != request.target.sourceSha256) {
            return result(
                status = CallsSymbolicStatus.TOOL_ERROR,
                startedAt = startedAt,
                diagnostic = "Source hash $actualSourceHash does not match frozen hash ${request.target.sourceSha256}",
            )
        }

        val sourceFile = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        if (sourceFile.importInfos.isNotEmpty()) {
            return result(
                status = CallsSymbolicStatus.UNSUPPORTED,
                startedAt = startedAt,
                diagnostic = "Single-file symbolic replay does not support imported project callees",
            )
        }
        val scene = EtsScene(projectFiles = listOf(sourceFile))
        val frontendEntryPoint = request.function.entryPoint.copy(
            module = requireNotNull(source.fileName).toString(),
        )
        val propertyManifest = PropertyManifest(
            propertyId = "calls.mapping",
            inputs = request.function.inputs,
            predicate = frontendEntryPoint,
        )
        val mapping = PropertyEtsMapper(scene = scene, sourceRoots = listOf(request.sourceRoot)).map(propertyManifest)
        if (mapping.predicate.status != EtsMappingStatus.EXACT) {
            return result(
                status = mapping.predicate.status.toSymbolicStatus(),
                startedAt = startedAt,
                diagnostic = mapping.predicate.diagnostics.joinToString { diagnostic -> diagnostic.message },
            )
        }

        val method = mapping.predicate.targets.single().method
        val targetCandidates = exactTargetCandidates(method, request.target)
        if (targetCandidates.isEmpty()) {
            return result(
                status = CallsSymbolicStatus.UNMAPPED,
                startedAt = startedAt,
                diagnostic = "No EtsIR statement has the exact frozen source range",
            )
        }

        val modelSelection = if (request.profile.usesFrozenModels) {
            TsUnknownCallModelSelection.Only(request.frozenModelIds)
        } else {
            TsUnknownCallModelSelection.Only(emptySet())
        }
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.REACHED_TARGET,
            randomSeed = request.seed,
            timeout = request.budget,
            solverType = SolverType.Z3,
            stopOnCoverage = 0,
            stopOnTargetsReached = false,
            throwExceptionOnStepFailure = true,
        )
        val tsOptions = TsOptions(
            unknownCallModelSelection = modelSelection,
            unknownCallFallback = request.profile.fallback,
        )
        // One source statement may lower to consecutive EtsIR instructions with the same exact source span.
        // Observe the first instruction before it executes, matching the source replay marker
        // inserted before the statement.
        val entryObserver = SourceStatementEntryObserver(targetCandidates.first())
        val analysis = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = tsOptions,
            machineObserver = entryObserver,
        ).use { machine ->
            val outcome = machine.analyzeWithOutcome(methods = listOf(method))
            MachineResult(
                states = entryObserver.reachedStates,
                stopReason = outcome.stopReason,
            )
        }
        val states = analysis.states
        if (states.isEmpty()) {
            val status = when (analysis.stopReason) {
                TsAnalysisStopReason.EXHAUSTED -> CallsSymbolicStatus.UNREACHED
                TsAnalysisStopReason.TIMEOUT -> CallsSymbolicStatus.TIMEOUT
                TsAnalysisStopReason.OTHER_LIMIT -> CallsSymbolicStatus.TOOL_ERROR
            }

            return result(
                status = status,
                startedAt = startedAt,
                diagnostic = if (analysis.stopReason == TsAnalysisStopReason.OTHER_LIMIT) {
                    "Symbolic execution stopped for an unexpected non-timeout limit"
                } else {
                    null
                },
            )
        }

        val inputs = states.asSequence()
            .map { state -> resolveScalarInputs(state, method) }
            .firstOrNull { candidate ->
                candidate.zip(request.function.inputs).all { (value, input) -> value in input.domain }
            }
        if (inputs == null) {
            return result(
                status = CallsSymbolicStatus.UNREPRESENTABLE,
                solverReached = true,
                startedAt = startedAt,
                diagnostic = "No reached state has inputs inside every frozen domain",
            )
        }

        return result(
            status = CallsSymbolicStatus.REACHED,
            inputs = inputs,
            startedAt = startedAt,
            diagnostic = "exact-source-lowering-size=${targetCandidates.size}",
        )
    }

    private fun exactTargetCandidates(method: EtsMethod, target: CallsSourceTarget): List<EtsStmt> =
        method.cfg.stmts.filter { statement ->
            val origin = statement.location.origin ?: return@filter false
            origin.startOffset == target.startOffset &&
                origin.endOffset == target.endOffset &&
                origin.startLine == target.start.line &&
                origin.startColumn == target.start.column &&
                origin.endLine == target.end.line &&
                origin.endColumn == target.end.column
        }

    private fun resolveScalarInputs(state: TsState, method: EtsMethod): List<JsConcreteValue> = with(state.ctx) {
        val model = state.models.single()

        method.parameters.mapIndexed { index, parameter ->
            val stackIndex = index + 1
            when (parameter.type) {
                EtsNumberType -> {
                    val lValue = mkRegisterStackLValue(fp64Sort, stackIndex)
                    val value = model.eval(state.memory.read(lValue).asExpr(fp64Sort)).extractDouble()
                    JsConcreteValue.number(value)
                }

                EtsBooleanType -> {
                    val lValue = mkRegisterStackLValue(boolSort, stackIndex)
                    val value = model.eval(state.memory.read(lValue).asExpr(boolSort)).toConcreteBoolValue()
                    JsConcreteValue.Boolean(value)
                }

                else -> error("Unsupported scalar parameter type: ${parameter.type}")
            }
        }
    }

    private fun result(
        status: CallsSymbolicStatus,
        startedAt: TimeSource.Monotonic.ValueTimeMark,
        solverReached: Boolean = status == CallsSymbolicStatus.REACHED,
        inputs: List<JsConcreteValue>? = null,
        diagnostic: String? = null,
    ) = CallsSymbolicSearchResult(
        status = status,
        solverReached = solverReached,
        inputs = inputs,
        elapsedMillis = startedAt.elapsedNow().inWholeMilliseconds,
        diagnostic = diagnostic,
    )

    private fun verifyNativeFrontendOnce(expectedRevision: String, expectedSha256: String) {
        require(System.getenv("ETS_FRONTEND_SCRIPT") == null) {
            "ETS_FRONTEND_SCRIPT must be unset so the frozen native frontend runtime is used"
        }
        val configuredFrontend = requireNotNull(System.getenv("ETS_FRONTEND_DIR")) {
            "ETS_FRONTEND_DIR is required to verify the frozen native frontend revision"
        }
        val frontendDirectory = Path.of(configuredFrontend).toRealPath()
        val cached = verifiedNativeFrontend
        val expectedIdentity = "$expectedRevision:$expectedSha256"
        if (cached == Pair(frontendDirectory, expectedIdentity)) {
            return
        }

        verifyCallsGitCheckout(frontendDirectory, expectedRevision)
        val runtimeScript = frontendDirectory.resolve("dist/index.js")
        val actualSha256 = Files.readAllBytes(runtimeScript).sha256()
        require(actualSha256 == expectedSha256) {
            "Native frontend runtime hash $actualSha256 does not match frozen hash $expectedSha256"
        }
        verifiedNativeFrontend = frontendDirectory to expectedIdentity
    }

    private fun verifyGitCheckoutOnce(
        checkout: Path,
        expectedRevision: String,
        cache: MutableMap<Path, String>,
    ) {
        if (cache[checkout] == expectedRevision) {
            return
        }

        verifyCallsGitCheckout(checkout, expectedRevision)
        cache[checkout] = expectedRevision
    }

    private class SourceStatementEntryObserver(
        private val target: EtsStmt,
    ) : UMachineObserver<TsState> {
        val reachedStates = mutableListOf<TsState>()

        override fun onStatePeeked(state: TsState) {
            if (state.currentStatement == target) {
                reachedStates += state.clone()
            }
        }
    }

    private data class MachineResult(
        val states: List<TsState>,
        val stopReason: TsAnalysisStopReason,
    )
}

internal fun verifyCallsGitCheckout(checkout: Path, expectedRevision: String) {
    val actualRevision = runCallsGit(checkout, "rev-parse", "HEAD").trim()
    require(actualRevision == expectedRevision) {
        "Checkout $checkout is at $actualRevision, expected frozen revision $expectedRevision"
    }
    runCallsGit(checkout, "diff", "--quiet", "HEAD", "--")
}

private fun runCallsGit(checkout: Path, vararg arguments: String): String {
    val process = ProcessBuilder(listOf("git", "-C", checkout.toString()) + arguments)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }
    val exitCode = process.waitFor()
    require(exitCode == 0) {
        val command = arguments.joinToString(separator = " ")
        "Git $command failed for $checkout with exit $exitCode: ${output.trim()}"
    }

    return output
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

private fun EtsMappingStatus.toSymbolicStatus(): CallsSymbolicStatus = when (this) {
    EtsMappingStatus.EXACT -> error("Exact mapping has no failure status")
    EtsMappingStatus.AMBIGUOUS -> CallsSymbolicStatus.AMBIGUOUS
    EtsMappingStatus.UNMAPPED -> CallsSymbolicStatus.UNMAPPED
    EtsMappingStatus.UNSUPPORTED -> CallsSymbolicStatus.UNSUPPORTED
}
