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
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

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

    for (crash in crashPack.crashes.values.sortedBy { it.id }) {
        analyzeCrash(crash)
    }
}

fun analyzeCrash(crash: CrashPackCrash) {
    val crashLog = crashPackPath / "crashes" / crash.application / crash.id / "${crash.id}.log"
    val crashCp = crashPackPath / "applications" / crash.application / crash.version / "bin"

    val cpFiles = crashCp.listDirectoryEntries("*.jar").map { it.toFile() }

    val cp = runBlocking {
        val db = jacodb {
//        persistent((crashPackPath / "${crash.id}.db").absolutePathString())
            useProcessJavaRuntime()
            installFeatures(InMemoryHierarchy, Approximations)
            loadByteCode(cpFiles)
        }

        db.awaitBackgroundJobs()

        db.classpathWithApproximations(cpFiles)
    }
    val trace = crashLog.readText()

    try {
        analyzeCrash(cp, trace, crash)
    } catch (ex: Throwable) {
        System.err.println(ex)
    }
}

data class RawTraceEntry(val cls: JcClassOrInterface, val methodName: String, val line: Int)

private fun analyzeCrash(cp: JcClasspath, trace: String, crash: CrashPackCrash) {
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

    println("-".repeat(50))
    println("Try reproduce crash: ${crash.application} | ${crash.id}")
    println(trace)

    val targets = createTargets(exceptionType, rawTraceEntries)

    reproduceCrash(cp, targets)
}

sealed class CrashReproductionTarget(location: JcInst? = null) : JcTarget(location)

class CrashReproductionLocationTarget(location: JcInst) : CrashReproductionTarget(location) {
    override fun toString(): String = "$location"
}

class CrashReproductionExceptionTarget(val exception: JcClassOrInterface) : CrashReproductionTarget() {
    override fun toString(): String = "Exception: $exception"
}

private fun createTargets(
    exception: JcClassOrInterface,
    trace: List<RawTraceEntry>
): List<CrashReproductionTarget> {
    var initialTargets: List<CrashReproductionTarget>? = null
    var currentTargets: List<CrashReproductionTarget> = emptyList()
    for (entry in trace.asReversed()) {
        val possibleMethods = entry.cls.declaredMethods.filter { it.name == entry.methodName }
        var possibleLocations = possibleMethods.flatMap { it.instList.filter { it.lineNumber == entry.line } }

        if (possibleLocations.isEmpty()) {
            TODO("No locations")
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

    return requireNotNull(initialTargets)
}

private class CrashReproductionAnalysis(
    override val targets: MutableCollection<out UTarget<*, *>>
) : UTargetController, JcInterpreterObserver, StatesCollector<JcState> {
    override val collectedStates = arrayListOf<JcState>()

    override fun onState(parent: JcState, forks: Sequence<JcState>) {
        propagateLocationTarget(parent)

        forks.forEach { propagateLocationTarget(it) }
    }

    private fun propagateLocationTarget(state: JcState) {
        val targets = state.targets
            .filterIsInstance<CrashReproductionLocationTarget>()
            .filter { it.location == state.currentStatement }

        targets.forEach { it.propagate(state) }

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

        logger.debug { state.targets.toList() }
    }
}

private fun reproduceCrash(cp: JcClasspath, targets: List<CrashReproductionTarget>) {
    val options = UMachineOptions(
        targetSearchDepth = 2u, // high values (e.g. 10) significantly degrade performance
        pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED_RANDOM),
        stopOnTargetsReached = true,
        stopOnCoverage = -1,
        timeoutMs = 120_000
    )
    val crashReproduction = CrashReproductionAnalysis(targets.toMutableList())

    val entrypoint = targets.mapNotNull { it.location?.location?.method }.distinct().single()

    JcMachine(cp, options, crashReproduction).use { machine ->
        machine.analyze(entrypoint, targets)
    }

    println("Found states: ${crashReproduction.collectedStates.size}")
}
