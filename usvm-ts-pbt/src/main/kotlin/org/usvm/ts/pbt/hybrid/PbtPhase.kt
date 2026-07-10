package org.usvm.ts.pbt.hybrid

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.usvm.ts.pbt.coverage.CoverageTracker
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
) {
    fun run(): PbtResult {
        val random = Random(seed)
        val generator = InputGenerator(scene, method, random)
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
        val unsupportedReasons = mutableMapOf<String, Int>()

        val start = TimeSource.Monotonic.markNow()
        coverage.phase = "pbt"

        while (executions < maxIterations && start.elapsedNow() < timeBudget) {
            // Stop early once everything is covered — the symbolic phase has nothing to add
            if (coverage.branchCoverage() == 1.0 && coverage.stmtCoverage() == 1.0) break

            // Bail out if the method is dominated by unmodeled constructs:
            // burning the whole budget on Unsupported executions is pointless.
            if (executions >= UNSUPPORTED_BAILOUT_THRESHOLD && unsupported == executions) break

            val thisValue = generator.generateThis()
            val args = generator.generateArgs()
            executions++

            val result = interpreter.execute(method, thisValue, args, listener)
            when (result) {
                is ExecutionResult.Returned -> returned++
                is ExecutionResult.Threw -> threw++
                is ExecutionResult.Diverged -> diverged++
                is ExecutionResult.Unsupported -> {
                    unsupported++
                    val key = result.reason.take(60)
                    unsupportedReasons[key] = (unsupportedReasons[key] ?: 0) + 1
                    logger.debug { "unsupported: ${result.reason}" }
                }
            }

            if (result is ExecutionResult.Unsupported || result is ExecutionResult.Diverged) continue

            val violation = property.check(args, result) ?: continue

            // Deduplicate failures by their description (e.g. throw site + kind)
            if (!seenFailureKeys.add(violation)) continue

            val shrunk = if (shrink) {
                Shrinker().shrink(args) { candidate ->
                    val r = interpreter.execute(method, thisValue, candidate)
                    r !is ExecutionResult.Unsupported && r !is ExecutionResult.Diverged &&
                        property.check(candidate, r) != null
                }
            } else args

            failures += PbtFailure(args, shrunk, violation)
        }

        val stats = PbtStats(
            executions = executions,
            returned = returned,
            threw = threw,
            diverged = diverged,
            unsupported = unsupported,
            elapsedMs = start.elapsedNow().inWholeMilliseconds,
            unsupportedReasons = unsupportedReasons,
        )
        logger.info {
            "PBT phase for ${method.name}: $stats, " +
                "stmt=${"%.1f".format(coverage.stmtCoverage() * 100)}%, " +
                "branch=${"%.1f".format(coverage.branchCoverage() * 100)}%"
        }
        return PbtResult(failures, stats, profiler)
    }
}
