package org.usvm

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.toType
import org.jacodb.approximation.Approximations
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.jacodb
import org.usvm.api.targets.JcTarget
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.statistics.collectors.StatesCollector
import org.usvm.targets.UTarget
import org.usvm.targets.UTargetController
import org.usvm.util.classpathWithApproximations
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Serializable
data class CrashPackApplicationVersion(
    @SerialName("src_url")
    val srcUrl: String,
    val version: String
)

@Serializable
data class CrashPackApplication(
    val name: String,
    val url: String,
    val versions: Map<String, CrashPackApplicationVersion>
)

@Serializable
data class CrashPackCrash(
    val application: String, // "JFreeChart"
    @SerialName("buggy_frame")
    val buggyFrame: String, // 6
    @SerialName("fixed_commit")
    val fixedCommit: String,
    val id: String, // "ES-14457"
    val issue: String, // "https://github.com/elastic/elasticsearch/issues/14457"
    @SerialName("target_frames")
    val targetFrames: String, // ".*elasticsearch.*"
    val version: String,
    @SerialName("version_fixed")
    val versionFixed: String,
)

@Serializable
data class CrashPack(
    val applications: Map<String, CrashPackApplication>,
    val crashes: Map<String, CrashPackCrash>
)

val crashPackPath = Path("D:") / "JCrashPack"

@OptIn(ExperimentalSerializationApi::class)
fun main() {
    val crashPack = Json.decodeFromStream<CrashPack>((crashPackPath / "jcrashpack.json").inputStream())

    val goodIds = setOf(
        "LANG-1b",
        "LANG-20b",
        "LANG-2b",
        "LANG-33b",
        "LANG-35b",
        "LANG-45b",
        "LANG-47b",
        "LANG-51b",
        "LANG-54b",
        "LANG-57b",
        "LANG-5b",
        "MATH-70b",
        "MATH-89b",
    )
    val badIds = setOf("ES-19891")

    for (crash in crashPack.crashes.values.sortedBy { it.id }.filter { it.id !in badIds }) {
        try {
            analyzeCrash(crash)
        } catch (ex: Throwable) {
            logger.error(ex) { "Failed" }
        }
    }
}

fun analyzeCrash(crash: CrashPackCrash) {
    val crashLog = crashPackPath / "crashes" / crash.application / crash.id / "${crash.id}.log"
    val crashCp = crashPackPath / "applications" / crash.application / crash.version / "bin"

    val cpFiles = crashCp.listDirectoryEntries("*.jar").map { it.toFile() }

    val jcdb = runBlocking {
        jacodb {
//        persistent((crashPackPath / "${crash.id}.db").absolutePathString())
            useProcessJavaRuntime()
            installFeatures(InMemoryHierarchy, Approximations)
            loadByteCode(cpFiles)
        }
    }

    jcdb.use { db ->
        val jccp = runBlocking {
            db.awaitBackgroundJobs()
            db.classpathWithApproximations(cpFiles, listOf(UnknownClasses))
        }

        jccp.use { cp ->
            val trace = crashLog.readText()

            runWithHardTimout(30.minutes) {
                analyzeCrash(cp, trace, crash)
            }
        }
    }
}

data class RawTraceEntry(val cls: JcClassOrInterface, val methodName: String, val line: Int)

private fun analyzeCrash(cp: JcClasspath, trace: String, crash: CrashPackCrash) {
    logger.warn { "#".repeat(50) }
    logger.warn { "Try reproduce crash: ${crash.application} | ${crash.id}" }
    logger.warn { "\n$trace" }

    val allTraceEntries = trace.lines().map { it.trim() }
    val relevantTracePattern = Regex(crash.targetFrames)
    val relevantTrace = allTraceEntries.dropLastWhile { !relevantTracePattern.matches(it) }

    val exceptionName = relevantTrace.firstOrNull()
        ?.substringBefore(':')?.trim()
        ?.substringAfterLast(' ')?.trim()
        ?: return
    val exceptionType = cp.findClassOrNull(exceptionName) ?: return

    val rawTraceEntries = relevantTrace
        .drop(1)
        .map { it.removePrefix("at").trim() }
        .mapNotNull {
            val lineNumber = it.substringAfterLast(':').trim(')').toIntOrNull() ?: return@mapNotNull null
            val classWithMethod = it.substringBefore('(')
            val className = classWithMethod.substringBeforeLast('.')
            val methodName = classWithMethod.substringAfterLast('.')
            val type = cp.findClassOrNull(className) ?: return@mapNotNull null
            if (!type.declaredMethods.any { it.name == methodName }) return@mapNotNull null
            RawTraceEntry(type, methodName, lineNumber)
        }

    if (rawTraceEntries.isEmpty()) return

    val targets = createTargets(exceptionType, rawTraceEntries)

    logger.warn {
        buildString {
            appendLine("Targets:")
            targets?.forEach { printTarget(it) }
        }
    }


    if (targets == null) return

    logger.warn { "-".repeat(50) }

    val states = reproduceCrash(cp, targets)

    logger.warn { "+".repeat(50) }
    logger.warn { "Found states: ${states.size}" }
}

private fun StringBuilder.printTarget(target: JcTarget, indent: Int = 0) {
    appendLine("\t".repeat(indent) + target)
    target.children.forEach { printTarget(it, indent + 1) }
}

private fun JcInst.printInst() = "${location.method.enclosingClass.name}#${location.method.name} | $this"

sealed class CrashReproductionTarget(location: JcInst? = null) : JcTarget(location)

class CrashReproductionLocationTarget(location: JcInst) : CrashReproductionTarget(location) {
    override fun toString(): String = location?.printInst() ?: ""
}

class CrashReproductionExceptionTarget(val exception: JcClassOrInterface) : CrashReproductionTarget() {
    override fun toString(): String = "Exception: $exception"
}

private fun createTargets(
    exception: JcClassOrInterface,
    trace: List<RawTraceEntry>
): List<CrashReproductionTarget>? {
    var initialTargets: List<CrashReproductionTarget>? = null
    var currentTargets: List<CrashReproductionTarget> = emptyList()
    for (entry in trace.asReversed()) {
        val possibleMethods = entry.cls.declaredMethods.filter { it.name == entry.methodName }
        var possibleLocations = possibleMethods.flatMap { it.instList.filter { it.lineNumber == entry.line } }

        if (possibleLocations.isEmpty()) {
            // todo: no locations
            continue
        }

//        val preferredInstructions = possibleLocations.filter { it.callExpr != null || it is JcThrowInst }
//        if (preferredInstructions.isNotEmpty()) {
//            possibleLocations = preferredInstructions
//        }
        // take first instruction
        possibleLocations = possibleLocations.take(1)

        if (initialTargets == null) {
            val targets = possibleLocations.map { CrashReproductionLocationTarget(it) }
            initialTargets = targets
            currentTargets = targets
            continue
        }

        val targets = mutableListOf<CrashReproductionTarget>()

        currentTargets.forEach { parent ->
            possibleLocations.forEach { location ->
                val target = CrashReproductionLocationTarget(location)
                targets.add(target)
                parent.addChild(target)
            }
        }

        currentTargets = targets
    }

    currentTargets.forEach { parent ->
        parent.addChild(CrashReproductionExceptionTarget(exception))
    }

    return initialTargets
}

private class CrashReproductionAnalysis(
    override val targets: MutableCollection<out UTarget<*, *>>
) : UTargetController, JcInterpreterObserver, StatesCollector<JcState> {
    override val collectedStates = arrayListOf<JcState>()

//    override fun onAssignStatement(
//        simpleValueResolver: JcSimpleValueResolver,
//        stmt: JcAssignInst,
//        stepScope: JcStepScope
//    ) {
//        stepScope.doWithState { propagateLocationTarget(this) }
//    }
//
//    override fun onEntryPoint(
//        simpleValueResolver: JcSimpleValueResolver,
//        stmt: JcMethodEntrypointInst,
//        stepScope: JcStepScope
//    ) {
//        stepScope.doWithState { propagateLocationTarget(this) }
//    }
//
//    override fun onIfStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcIfInst, stepScope: JcStepScope) {
//        stepScope.doWithState { propagateLocationTarget(this) }
//    }
//
//    override fun onReturnStatement(
//        simpleValueResolver: JcSimpleValueResolver,
//        stmt: JcReturnInst,
//        stepScope: JcStepScope
//    ) {
//        stepScope.doWithState { propagateLocationTarget(this) }
//    }
//
//    override fun onGotoStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcGotoInst, stepScope: JcStepScope) {
//        stepScope.doWithState { propagateLocationTarget(this) }
//    }
//
//    override fun onCatchStatement(
//        simpleValueResolver: JcSimpleValueResolver,
//        stmt: JcCatchInst,
//        stepScope: JcStepScope
//    ) {
//        stepScope.doWithState { propagateLocationTarget(this) }
//    }
//
//    override fun onSwitchStatement(
//        simpleValueResolver: JcSimpleValueResolver,
//        stmt: JcSwitchInst,
//        stepScope: JcStepScope
//    ) {
//        stepScope.doWithState { propagateLocationTarget(this) }
//    }
//
//    override fun onThrowStatement(
//        simpleValueResolver: JcSimpleValueResolver,
//        stmt: JcThrowInst,
//        stepScope: JcStepScope
//    ) {
//        stepScope.doWithState { propagateLocationTarget(this) }
//    }
//
//    override fun onCallStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcCallInst, stepScope: JcStepScope) {
//        stepScope.doWithState { propagateLocationTarget(this) }
//    }
//
//    override fun onEnterMonitorStatement(
//        simpleValueResolver: JcSimpleValueResolver,
//        stmt: JcEnterMonitorInst,
//        stepScope: JcStepScope
//    ) {
//        stepScope.doWithState { propagateLocationTarget(this) }
//    }
//
//    override fun onExitMonitorStatement(
//        simpleValueResolver: JcSimpleValueResolver,
//        stmt: JcExitMonitorInst,
//        stepScope: JcStepScope
//    ) {
//        stepScope.doWithState { propagateLocationTarget(this) }
//    }

    override fun onState(parent: JcState, forks: Sequence<JcState>) {
        propagateExceptionTarget(parent)
        propagatePrevLocationTarget(parent)

        forks.forEach {
            propagateExceptionTarget(it)
            propagatePrevLocationTarget(it)
        }

        logger.info {
            parent.currentStatement.printInst().padEnd(120) + "@@@  " + "${parent.targets.toList()}"
        }
    }

    private fun propagateCurrentLocationTarget(state: JcState) = propagateLocationTarget(state) { it.currentStatement }
    private fun propagatePrevLocationTarget(state: JcState) = propagateLocationTarget(state) {
        it.pathLocation.parent?.statement ?: error("This is impossible by construction")
    }

    private inline fun propagateLocationTarget(state: JcState, stmt: (JcState) -> JcInst) {
        val stateLocation = stmt(state)
        val targets = state.targets
            .filterIsInstance<CrashReproductionLocationTarget>()
            .filter { it.location == stateLocation }

        targets.forEach { it.propagate(state) }
    }

    private fun propagateExceptionTarget(state: JcState) {
        val mr = state.methodResult
        if (mr is JcMethodResult.JcException) {
            val exTargets = state.targets
                .filterIsInstance<CrashReproductionExceptionTarget>()
                .filter { mr.type == it.exception.toType() }
            exTargets.forEach {
                collectedStates += state.clone()
                it.propagate(state)
            }
        }
    }
}

private fun reproduceCrash(cp: JcClasspath, targets: List<CrashReproductionTarget>): List<JcState> {
    val options = UMachineOptions(
        targetSearchDepth = 3u, // high values (e.g. 10) significantly degrade performance
        pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED_RANDOM),
        stopOnTargetsReached = true,
        stopOnCoverage = -1,
        timeoutMs = 1_000_000,
        solverUseSoftConstraints = false,
        solverQueryTimeoutMs = 10_000,
    )
    val crashReproduction = CrashReproductionAnalysis(targets.toMutableList())

    val entrypoint = targets.mapNotNull { it.location?.location?.method }.distinct().single()

    JcMachine(cp, options, crashReproduction).use { machine ->
        machine.analyze(entrypoint, targets)
    }

    return crashReproduction.collectedStates
}

private fun runWithHardTimout(timeout: Duration, body: () -> Unit) {
    val completion = CompletableFuture<Unit>()
    val t = thread(start = false) {
        try {
            body()
            completion.complete(Unit)
        } catch (ex: Throwable) {
            completion.completeExceptionally(ex)
        }
    }
    try {
        t.start()
        completion.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    } catch (ex: Throwable) {
        while (t.isAlive) {
            @Suppress("DEPRECATION")
            t.stop()
        }
        throw ex
    }
}
