package org.usvm.ts.pbt.hybrid

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.usvm.ts.pbt.coverage.CoverageTracker
import org.usvm.ts.pbt.external.ConcreteInputProvider
import org.usvm.ts.pbt.gen.InputGenerator
import org.usvm.ts.pbt.gen.Shrinker
import org.usvm.ts.pbt.interpreter.EtsConcreteInterpreter
import org.usvm.ts.pbt.interpreter.ExecutionListener
import org.usvm.ts.pbt.interpreter.ExecutionResult
import org.usvm.ts.pbt.interpreter.VValue
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

/** After this many executions that are *all* [ExecutionResult.Unsupported], give up on the method. */
private const val UNSUPPORTED_BAILOUT_THRESHOLD = 25

/**
 * The property (oracle) checked for every concrete execution.
 * The default property is "the method does not throw".
 */
fun interface PbtProperty {
    /** @return `null` if the property holds, otherwise a failure description. */
    fun check(args: List<VValue>, result: ExecutionResult): String?

    companion object {
        val NO_CRASH = PbtProperty { _, result ->
            if (result is ExecutionResult.Threw) "unexpected throw: ${result.value}" else null
        }
    }
}

data class PbtFailure(
    val args: List<VValue>,
    val shrunkArgs: List<VValue>,
    val description: String,
)

data class PbtStats(
    val executions: Int,
    val returned: Int,
    val threw: Int,
    val diverged: Int,
    val unsupported: Int,
    val elapsedMs: Long,
    /** Truncated reason -> count, for prioritizing interpreter/engine gaps. */
    val unsupportedReasons: Map<String, Int> = emptyMap(),
    val generatedExecutions: Int = executions,
    val externalImported: Int = 0,
    val externalExecuted: Int = 0,
    val externalRejected: Int = 0,
    val externalDeduplicated: Int = 0,
    val externalProviders: Map<String, ExternalProviderStats> = emptyMap(),
)

data class ExternalProviderStats(
    val imported: Int,
    val executed: Int,
    val rejected: Int,
    val deduplicated: Int,
    val rejectionReasons: Map<String, Int> = emptyMap(),
)

class PbtResult(
    val failures: List<PbtFailure>,
    val stats: PbtStats,
    val typeProfiler: TypeProfiler,
)

/**
 * Phase 1 of the hybrid analysis: random (property-based) testing of a method
 * on the EtsIR level via the concrete interpreter, with coverage feedback.
 */
class PbtPhase(
    private val scene: EtsScene,
    private val method: EtsMethod,
    private val coverage: CoverageTracker,
    private val seed: Long = 0L,
    private val maxIterations: Int = 2_000,
    private val timeBudget: Duration = Duration.INFINITE,
    private val property: PbtProperty = PbtProperty.NO_CRASH,
    private val shrink: Boolean = true,
    private val inputProviders: List<ConcreteInputProvider> = emptyList(),
    /** When false, replay the external corpus and go directly to the symbolic phase. */
    private val internalGeneration: Boolean = true,
) {
    fun run(): PbtResult {
        val random = Random(seed)
        val generator = if (internalGeneration) InputGenerator(scene, method, random) else null
        val interpreter = EtsConcreteInterpreter(scene)
        val profiler = TypeProfiler()
        val listener = ExecutionListener.composite(listOf(coverage, profiler))

        val failures = mutableListOf<PbtFailure>()
        val seenFailureKeys = mutableSetOf<String>()

        var returned = 0
        var threw = 0
        var diverged = 0
        var unsupported = 0
        var executions = 0
        var generatedExecutions = 0
        var generatedUnsupported = 0
        var externalImported = 0
        var externalExecuted = 0
        var externalRejected = 0
        var externalDeduplicated = 0
        val unsupportedReasons = mutableMapOf<String, Int>()
        val providerStats = linkedMapOf<String, MutableExternalProviderStats>()

        val start = TimeSource.Monotonic.markNow()
        fun executeInput(thisValue: VValue, args: List<VValue>, generated: Boolean) {
            executions++
            if (generated) generatedExecutions++ else externalExecuted++

            val result = interpreter.execute(method, thisValue, args, listener)
            when (result) {
                is ExecutionResult.Returned -> returned++
                is ExecutionResult.Threw -> threw++
                is ExecutionResult.Diverged -> diverged++
                is ExecutionResult.Unsupported -> {
                    unsupported++
                    if (generated) generatedUnsupported++
                    val key = result.reason.take(60)
                    unsupportedReasons[key] = (unsupportedReasons[key] ?: 0) + 1
                    logger.debug { "unsupported: ${result.reason}" }
                }
            }

            if (result is ExecutionResult.Unsupported || result is ExecutionResult.Diverged) return

            val violation = property.check(args, result) ?: return

            // Deduplicate failures by their description (e.g. throw site + kind)
            if (!seenFailureKeys.add(violation)) return

            val shrunk = if (shrink) {
                Shrinker().shrink(args) { candidate ->
                    val r = interpreter.execute(method, thisValue, candidate)
                    r !is ExecutionResult.Unsupported && r !is ExecutionResult.Diverged &&
                        property.check(candidate, r) != null
                }
            } else args

            failures += PbtFailure(args, shrunk, violation)
        }

        // External tools are input producers. Their claims are not trusted as
        // coverage until the cases have executed through this interpreter.
        val seenExternalInputs = mutableSetOf<String>()
        externalReplay@ for (provider in inputProviders) {
            val batch = try {
                provider.inputsFor(method)
            } catch (cause: Throwable) {
                val stats = providerStats.getOrPut(provider.name, ::MutableExternalProviderStats)
                stats.rejected++
                stats.recordRejection("provider failure: ${cause.message ?: cause::class.simpleName}")
                externalRejected++
                continue@externalReplay
            }
            val stats = providerStats.getOrPut(batch.producer, ::MutableExternalProviderStats)
            stats.imported += batch.imported
            externalImported += batch.imported
            for (rejection in batch.rejections) {
                stats.rejected++
                stats.recordRejection(rejection.reason)
                externalRejected++
            }

            for (case in batch.cases) {
                if (start.elapsedNow() >= timeBudget) break@externalReplay
                if (coverage.branchCoverage() == 1.0 && coverage.stmtCoverage() == 1.0) break@externalReplay
                if (!seenExternalInputs.add(case.fingerprint)) {
                    stats.deduplicated++
                    externalDeduplicated++
                    continue
                }
                coverage.phase = "external:${case.producer}"
                executeInput(case.receiver, case.arguments, generated = false)
                stats.executed++
            }
        }

        coverage.phase = "pbt"
        while (internalGeneration && generatedExecutions < maxIterations && start.elapsedNow() < timeBudget) {
            // Stop early once everything is covered — the symbolic phase has nothing to add
            if (coverage.branchCoverage() == 1.0 && coverage.stmtCoverage() == 1.0) break

            // Bail out if the method is dominated by unmodeled constructs:
            // burning the whole budget on Unsupported executions is pointless.
            if (generatedExecutions >= UNSUPPORTED_BAILOUT_THRESHOLD && generatedUnsupported == generatedExecutions) break

            val thisValue = checkNotNull(generator).generateThis()
            val args = generator.generateArgs()
            executeInput(thisValue, args, generated = true)
        }

        val stats = PbtStats(
            executions = executions,
            returned = returned,
            threw = threw,
            diverged = diverged,
            unsupported = unsupported,
            elapsedMs = start.elapsedNow().inWholeMilliseconds,
            unsupportedReasons = unsupportedReasons,
            generatedExecutions = generatedExecutions,
            externalImported = externalImported,
            externalExecuted = externalExecuted,
            externalRejected = externalRejected,
            externalDeduplicated = externalDeduplicated,
            externalProviders = providerStats.mapValues { (_, stats) -> stats.freeze() },
        )
        logger.info {
            "PBT phase for ${method.name}: $stats, " +
                "stmt=${"%.1f".format(coverage.stmtCoverage() * 100)}%, " +
                "branch=${"%.1f".format(coverage.branchCoverage() * 100)}%"
        }
        return PbtResult(failures, stats, profiler)
    }

    private class MutableExternalProviderStats {
        var imported: Int = 0
        var executed: Int = 0
        var rejected: Int = 0
        var deduplicated: Int = 0
        val rejectionReasons: MutableMap<String, Int> = linkedMapOf()

        fun recordRejection(reason: String) {
            val key = reason.take(120)
            rejectionReasons[key] = (rejectionReasons[key] ?: 0) + 1
        }

        fun freeze(): ExternalProviderStats = ExternalProviderStats(
            imported = imported,
            executed = executed,
            rejected = rejected,
            deduplicated = deduplicated,
            rejectionReasons = rejectionReasons.toMap(),
        )
    }
}
