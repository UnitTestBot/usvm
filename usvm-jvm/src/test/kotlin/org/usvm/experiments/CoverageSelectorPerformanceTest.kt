package org.usvm.experiments

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.findClass
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.algorithms.RandomizedPriorityCollection
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcComponents
import org.usvm.machine.JcContext
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcTypeSystem
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcState
import org.usvm.ps.BfsPathSelector
import org.usvm.ps.CoverageGuidedPathSelector
import org.usvm.ps.RandomTreePathSelector
import org.usvm.ps.WeightedPathSelector
import org.usvm.samples.JacoDBContainer
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.distances.CallStackDistanceCalculator
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.MultiTargetDistanceCalculator
import org.usvm.stopstrategies.StopStrategy
import org.usvm.util.originalInst
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/** Manual end-to-end runs over real JVM bytecode; not a microbenchmark or concrete replay. */
@Tag("manual")
class CoverageSelectorPerformanceTest {
    @Test
    fun runProgram() {
        val program = System.getProperty("coverage.perf.case", "codec")
        val mode = System.getProperty("coverage.perf.mode", "128")
        val steps = System.getProperty("coverage.perf.steps", "100000").toLong()
        val timeoutMillis = System.getProperty("coverage.perf.timeoutMillis", "120000").toLong()
        val resultFile = File(System.getProperty("coverage.perf.results"))
        val libraries = System.getProperty("coverage.perf.jars").split(File.pathSeparatorChar).map(::File)
        val samplePaths = System.getenv("usvm.jvm.test.samples").split(File.pathSeparatorChar).map(::File)
        val container = JacoDBContainer(key = "coverage-performance", classpath = samplePaths + libraries)
        try {
            val (className, methodName) = when (program) {
                "codec" -> "org.apache.commons.codec.binary.BinaryCodec" to "toAsciiBytes"
                "gcd" -> "com.google.common.math.IntMath" to "gcd"
                "loan" -> "org.usvm.samples.psbenchmarks.LoanExam" to "getCreditPercent"
                else -> error("Unknown program: $program")
            }
            val cls = container.cp.findClass(className)
            val method = cls.declaredMethods.single { it.name == methodName }
            val goals = cls.declaredMethods.filter { !it.isAbstract && !it.isNative && !it.isConstructor }
            PerformanceMachine(container.cp).use { machine ->
                val result = machine.measure(program, mode, method, goals, steps, timeoutMillis)
                resultFile.parentFile.mkdirs()
                resultFile.appendText(result + "\n")
            }
        } finally {
            runBlocking {
                container.cp.close()
                container.db.close()
            }
        }
    }
}

private class PerformanceMachine(cp: JcClasspath) : UMachine<JcState>() {
    override val options = UMachineOptions(
        randomSeed = System.getProperty("coverage.perf.seed", "0").toLong(),
        solverType = SolverType.YICES,
        solverTimeout = 250.milliseconds,
        typeOperationsTimeout = 250.milliseconds,
        throwExceptionOnStepFailure = true,
        useSoftConstraints = false,
    )
    private val graph = JcApplicationGraph(cp)
    private val components = JcComponents(JcTypeSystem(cp, options.typeOperationsTimeout), options)
    private val interpreter = JcInterpreter(JcContext(cp, components), graph, JcMachineOptions(arrayMaxSize = 1500))

    fun measure(
        program: String,
        mode: String,
        method: JcMethod,
        methodsToCover: List<JcMethod>,
        stepLimit: Long,
        timeoutMillis: Long,
    ): String {
        val coverage = CoverageStatistics<JcMethod, JcInst, JcState>(methodsToCover.toHashSet(), graph)
        val goals = coverage.getUncoveredStatements()
        val metrics = Metrics()
        val initial = interpreter.getInitialState(method)
        val (base, markCovered) = createGuidance(mode, goals, metrics, initial)
        val selector = MeasuredSelector(base, metrics)
        coverage.addOnCoveredObserver { _, _, statement ->
            val started = System.nanoTime()
            markCovered(statement)
            metrics.selectorNanos += System.nanoTime() - started
            metrics.coverageReports++
        }
        val observer = metrics.observer()
        val gcCountBefore = gcCount()
        val gcMillisBefore = gcMillis()
        val allocatedBefore = allocatedBytes()
        val started = System.nanoTime()
        var stop = "exhausted"
        selector.add(states = listOf(initial))
        run(
            interpreter = interpreter,
            pathSelector = selector,
            observer = CompositeUMachineObserver(coverage, observer),
            isStateTerminated = { state -> state.callStack.isEmpty() },
            stopStrategy = StopStrategy {
                val elapsedMillis = (System.nanoTime() - started) / 1_000_000
                when {
                    metrics.steps >= stepLimit -> {
                        stop = "steps"
                        true
                    }
                    elapsedMillis >= timeoutMillis -> {
                        stop = "timeout"
                        true
                    }
                    else -> {
                        false
                    }
                }
            },
        )
        val elapsedNanos = System.nanoTime() - started
        val allocated = allocatedBytes() - allocatedBefore
        val totals = AnalysisTotals(
            elapsedNanos = elapsedNanos,
            allocatedBytes = allocated,
            gcCount = gcCount() - gcCountBefore,
            gcMillis = gcMillis() - gcMillisBefore,
        )
        return metrics.report(program, mode, goals.size, coverage.getTotalCoveredStatements(), stop, totals)
    }

    private fun createGuidance(
        mode: String,
        goals: Collection<JcInst>,
        metrics: Metrics,
        initial: JcState,
    ): Pair<UPathSelector<JcState>, (JcInst) -> Unit> {
        val cfg = CfgStatisticsImpl(graph)
        val calculators = MultiTargetDistanceCalculator<JcMethod, JcInst, UInt> { target ->
            CallStackDistanceCalculator(targets = listOf(target), cfgStatistics = cfg, applicationGraph = graph)
        }
        val distance: (JcState, JcInst) -> UInt = { state, target ->
            metrics.distanceCalls++
            calculators.calculateDistance(state.currentStatement.originalInst(), state.callStack, target)
        }
        val markCovered: (JcInst) -> Unit
        val base: UPathSelector<JcState>
        if (mode == "eager") {
            val eager = EagerCoverageSelector(goals, distance)
            base = eager
            markCovered = { eager.markCovered(it) }
        } else if (mode == "existing") {
            val aggregate = CallStackDistanceCalculator(targets = goals, cfgStatistics = cfg, applicationGraph = graph)
            base = WeightedPathSelector(
                priorityCollectionFactory = { DeterministicPriorityCollection(naturalOrder()) },
                weighter = { state: JcState ->
                    metrics.distanceCalls++
                    aggregate.calculateDistance(state.currentStatement.originalInst(), state.callStack)
                },
            )
            markCovered = { statement -> aggregate.removeTarget(graph.methodOf(statement), statement) }
        } else if (mode == "random-path" || mode == "uniform") {
            base = createRandomSelector(mode, initial)
            markCovered = {}
        } else if (mode == "bfs") {
            base = BfsPathSelector()
            markCovered = {}
        } else {
            val periodic = CoverageGuidedPathSelector(
                targets = goals,
                distance = distance,
                priorityRefreshInterval = mode.toInt(),
            )
            base = periodic
            markCovered = { periodic.markCovered(targets = listOf(it)) }
        }
        return base to markCovered
    }

    private fun createRandomSelector(mode: String, initial: JcState): UPathSelector<JcState> {
        val random = Random(seed = options.randomSeed)
        if (mode == "uniform") {
            return WeightedPathSelector(
                priorityCollectionFactory = {
                    RandomizedPriorityCollection(
                        comparator = compareBy<JcState> { it.id },
                        unitIntervalRandom = { random.nextDouble() },
                    )
                },
                weighter = { 1.0 },
            )
        }

        return RandomTreePathSelector.fromRoot(
            root = initial.pathNode,
            randomNonNegativeInt = { bound -> random.nextInt(from = 0, until = bound) },
        )
    }

    override fun close() = components.close()
}

private class Metrics {
    var steps = 0L
    var terminated = 0L
    var selectorNanos = 0L
    var refreshNanos = 0L
    var refreshes = 0L
    var distanceCalls = 0L
    var coverageReports = 0L
    var queueSize = 0
    var peakQueue = 0
}

private class MeasuredSelector(
    private val delegate: UPathSelector<JcState>,
    private val metrics: Metrics,
) : UPathSelector<JcState> {
    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun peek(): JcState {
        val beforeCalls = metrics.distanceCalls
        val started = System.nanoTime()
        val result = delegate.peek()
        val elapsed = System.nanoTime() - started
        metrics.selectorNanos += elapsed
        if (metrics.distanceCalls > beforeCalls) {
            metrics.refreshes++
            metrics.refreshNanos += elapsed
        }
        return result
    }

    override fun add(states: Collection<JcState>) {
        val started = System.nanoTime()
        delegate.add(states)
        metrics.selectorNanos += System.nanoTime() - started
        metrics.queueSize += states.size
        metrics.peakQueue = maxOf(metrics.peakQueue, metrics.queueSize)
    }

    override fun update(state: JcState) {
        val started = System.nanoTime()
        delegate.update(state)
        metrics.selectorNanos += System.nanoTime() - started
    }

    override fun remove(state: JcState) {
        val started = System.nanoTime()
        delegate.remove(state)
        metrics.selectorNanos += System.nanoTime() - started
        metrics.queueSize--
    }
}

/** Original PR algorithm, using the same queue ordering fix as the periodic implementation. */
private class EagerCoverageSelector(
    targets: Collection<JcInst>,
    distance: (JcState, JcInst) -> UInt,
) : UPathSelector<JcState> {
    private val pending = targets.toMutableSet()
    private var dirty = false
    private val delegate = WeightedPathSelector<JcState, UInt>(
        priorityCollectionFactory = { DeterministicPriorityCollection(naturalOrder()) },
        weighter = { state -> pending.minOfOrNull { distance(state, it) } ?: UInt.MAX_VALUE },
    )

    fun markCovered(target: JcInst) {
        if (pending.remove(target)) dirty = true
    }

    override fun isEmpty(): Boolean = delegate.isEmpty()
    override fun peek(): JcState {
        if (dirty) {
            val states = delegate.drain().toList()
            delegate.add(states)
            dirty = false
        }
        return delegate.peek()
    }
    override fun add(states: Collection<JcState>) = delegate.add(states)
    override fun update(state: JcState) = delegate.update(state)
    override fun remove(state: JcState) = delegate.remove(state)
}

private fun gcCount(): Long = ManagementFactory.getGarbageCollectorMXBeans().sumOf {
    it.collectionCount.coerceAtLeast(minimumValue = 0)
}

private fun gcMillis(): Long = ManagementFactory.getGarbageCollectorMXBeans().sumOf {
    it.collectionTime.coerceAtLeast(minimumValue = 0)
}
private fun allocatedBytes(): Long {
    val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    return bean.getThreadAllocatedBytes(Thread.currentThread().id)
}

private data class AnalysisTotals(
    val elapsedNanos: Long,
    val allocatedBytes: Long,
    val gcCount: Long,
    val gcMillis: Long,
)

private fun Metrics.observer(): UMachineObserver<JcState> = object : UMachineObserver<JcState> {
    override fun onStatePeeked(state: JcState) {
        steps++
    }

    override fun onStateTerminated(state: JcState, stateReachable: Boolean) {
        terminated++
    }
}

private fun Metrics.report(
    program: String,
    mode: String,
    goals: Int,
    covered: Int,
    stop: String,
    totals: AnalysisTotals,
): String {
    val repetition = System.getProperty("coverage.perf.repetition", "1").toInt()
    val randomSeed = System.getProperty("coverage.perf.seed", "0").toLong()
    return """
        {"program":"$program",
        "mode":"$mode",
        "repetition":$repetition,
        "randomSeed":$randomSeed,
        "steps":$steps,
        "stop":"$stop",
        "elapsedMs":${totals.elapsedNanos / 1_000_000.0},
        "selectorMs":${selectorNanos / 1_000_000.0},
        "refreshMs":${refreshNanos / 1_000_000.0},
        "refreshes":$refreshes,
        "distanceCalls":$distanceCalls,
        "peakQueue":$peakQueue,
        "pending":$queueSize,
        "goals":$goals,
        "covered":$covered,
        "coverageReports":$coverageReports,
        "terminated":$terminated,
        "allocatedBytes":${totals.allocatedBytes},
        "gcCount":${totals.gcCount},
        "gcMs":${totals.gcMillis}}
    """.trimIndent().replace("\n", "")
}
