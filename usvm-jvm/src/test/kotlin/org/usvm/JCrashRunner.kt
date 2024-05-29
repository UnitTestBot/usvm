@file:Suppress("unused")

package org.usvm

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcThrowInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.findMethodOrNull
import org.jacodb.approximation.Approximations
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.jacodb
import org.usvm.api.targets.CrashReproductionExceptionTarget
import org.usvm.api.targets.CrashReproductionLocationTarget
import org.usvm.api.targets.CrashReproductionTarget
import org.usvm.api.targets.printTarget
import org.usvm.api.targets.reproduceCrash
import org.usvm.util.classpathWithApproximations
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
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

data class RawTraceEntry(val cls: JcClassOrInterface, val methodName: String, val line: Int)

@Serializable
data class TraceEntry(
    val className: String,
    val methodName: String,
    val methodDesc: String,
    val instructionIdx: Int
)

@Serializable
data class TraceException(
    val className: String
)

@Serializable
data class CrashTrace(
    val original: String,
    val entries: List<TraceEntry>,
    val exception: TraceException
)

@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    val crashPackPath = Path(args.first())

    val crashPackDescriptionPath = crashPackPath / "jcrashpack.json"
    val crashPack = Json.decodeFromStream<CrashPack>(crashPackDescriptionPath.inputStream())

//    parseCrashTraces(crashPackPath, crashPack)
    val traces = loadCrashTraces(crashPackPath)

    analyzeCrashes(crashPackPath, crashPack, traces)
}

@OptIn(ExperimentalSerializationApi::class)
fun parseCrashTraces(crashPackPath: Path, crashPack: CrashPack) {
    val parsed = runBlocking {
        crashPack.crashes.values.mapNotNull {
            try {
                it to parseTrace(crashPackPath, it)
            } catch (ex: Throwable) {
                System.err.println(ex)
                null
            }
        }
    }

    println("PARSED ${parsed.size} TOTAL ${crashPack.crashes.size}")

    val traces = parsed.associate { it.first.id to it.second }

    val crashPackTracesPath = crashPackPath / "traces.json"
    Json.encodeToStream(traces, crashPackTracesPath.outputStream())
}

@OptIn(ExperimentalSerializationApi::class)
fun loadCrashTraces(crashPackPath: Path): Map<String, CrashTrace> {
    val crashPackTracesPath = crashPackPath / "traces.json"
    return Json.decodeFromStream(crashPackTracesPath.inputStream())
}

private suspend fun parseTrace(crashPackPath: Path, crash: CrashPackCrash): CrashTrace {
    val crashLog = crashPackPath / "crashes" / crash.application / crash.id / "${crash.id}.log"
    val crashCp = crashPackPath / "applications" / crash.application / crash.version / "bin"

    val cpFiles = crashCp.listDirectoryEntries("*.jar").map { it.toFile() }

    jacodb {
        useProcessJavaRuntime()
        installFeatures(InMemoryHierarchy)
        loadByteCode(cpFiles)
    }.use { db ->
        db.classpath(cpFiles).use { cp ->
            val trace = crashLog.readText()
            return parseTrace(cp, trace, crash)
        }
    }
}

private fun parseTrace(cp: JcClasspath, trace: String, crash: CrashPackCrash): CrashTrace {
    val allTraceEntries = trace.lines().map { it.trim() }
    val relevantTracePattern = Regex(crash.targetFrames)
    val relevantTrace = allTraceEntries.dropLastWhile { !relevantTracePattern.matches(it) }

    val exceptionName = relevantTrace.first()
        .substringBefore(':').trim()
        .substringAfterLast(' ').trim()

    val exceptionType = cp.findClass(exceptionName)

    val rawTraceEntries = relevantTrace
        .drop(1)
        .map { it.removePrefix("at").trim() }
        .map {
            val lineNumber = it.substringAfterLast(':').trim(')').toInt()
            val classWithMethod = it.substringBefore('(')
            val className = classWithMethod.substringBeforeLast('.')
            val methodName = classWithMethod.substringAfterLast('.')
            val type = cp.findClass(className)
            RawTraceEntry(type, methodName, lineNumber)
        }

    val traceEntries = resolveTraceEntries(rawTraceEntries.asReversed())
    return CrashTrace(trace, traceEntries, TraceException(exceptionType.name))
}

private fun resolveTraceEntries(trace: List<RawTraceEntry>): List<TraceEntry> {
    val result = mutableListOf<TraceEntry>()
    for (entryIdx in trace.indices) {
        val instruction = resolveTraceEntryInstruction(trace, entryIdx)
        with(instruction.location) {
            result += TraceEntry(method.enclosingClass.name, method.name, method.description, index)
        }
    }
    return result
}

private fun resolveTraceEntryInstruction(
    trace: List<RawTraceEntry>,
    entryIdx: Int
): JcInst {
    val entry = trace[entryIdx]
    val nextEntry = trace.getOrNull(entryIdx + 1)

    val possibleMethods = entry.cls.declaredMethods
        .filter { it.name == entry.methodName }

    val results = possibleMethods.mapNotNull {
        resolveMethodInstruction(entry, nextEntry, it, strictLineNumber = true)
    }
    if (results.isNotEmpty()) {
        return results.singleOrNull() ?: error("FAIL")
    }

    val fuzzyResults = possibleMethods.mapNotNull {
        resolveMethodInstruction(entry, nextEntry, it, strictLineNumber = false)
    }
    return fuzzyResults.singleOrNull() ?: error("FAIL")
}

private fun resolveMethodInstruction(
    entry: RawTraceEntry,
    nextEntry: RawTraceEntry?,
    method: JcMethod,
    strictLineNumber: Boolean
): JcInst? {
    val possibleInstructions = method.instList.filter { it.lineNumber == entry.line }

    if (nextEntry != null) {
        possibleInstructions
            .firstOrNull {
                val call = it.callExpr
                call != null && call.method.name == nextEntry.methodName
            }?.let { return it }

        if (strictLineNumber) return null

        method.instList
            .filter { it.lineNumber >= entry.line }
            .mapNotNull { inst -> inst.callExpr?.let { inst to it } }
            .firstOrNull { it.second.method.name == nextEntry.methodName }
            ?.let { return it.first }

        return null
    } else {
        possibleInstructions.firstOrNull { it is JcThrowInst }?.let { return it }
        possibleInstructions.firstOrNull { it.callExpr != null }?.let { return it }
        return possibleInstructions.firstOrNull()
    }
}

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

val idToCheck = "CHART-13b"

fun analyzeCrashes(crashPackPath: Path, crashPack: CrashPack, traces: Map<String, CrashTrace>) {
    val crashes = crashPack.crashes.values
        .sortedBy { it.id }
        .filter { it.id !in badIds }
//        .filter { it.id in goodIds }
//        .filter { it.id == idToCheck }
//        .take(3)

    for ((idx, crash) in crashes.withIndex()) {
        val trace = traces[crash.id] ?: continue
        try {
            println("Start ${crash.id}: $idx / ${crashes.size}")
            analyzeCrash(crashPackPath, crash, trace)
        } catch (ex: Throwable) {
            logger.error(ex) { "Failed" }
        }
    }

    logger.warn {
        buildString {
            appendLine("$".repeat(50))
            appendLine("OVERALL USED METHODS")
            reportUsedInstructions(usageStats, this)
        }
    }
}

fun analyzeCrash(crashPackPath: Path, crash: CrashPackCrash, trace: CrashTrace) {
    val crashCp = crashPackPath / "applications" / crash.application / crash.version / "bin"

    val cpFiles = crashCp.listDirectoryEntries("*.jar").map { it.toFile() }

    val jcdb = runBlocking {
        jacodb {
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
            runWithHardTimout(2.minutes) {
                analyzeCrash(cp, trace, crash)
            }
        }
    }
}

private val usageStats = hashMapOf<JcClassOrInterface, MutableMap<JcMethod, Int>>()

private fun analyzeCrash(cp: JcClasspath, trace: CrashTrace, crash: CrashPackCrash) {
    logger.warn { "#".repeat(50) }
    logger.warn { "Try reproduce crash: ${crash.application} | ${crash.id}" }
    logger.warn { "\n${trace.original}" }

    val exceptionType = cp.findClassOrNull(trace.exception.className) ?: return
    val target = createTargets(cp, exceptionType, trace.entries) ?: return

    logger.warn {
        buildString {
            appendLine("Targets:")
            printTarget(target)
        }
    }

    logger.warn { "-".repeat(50) }

    val (states, instructions) = reproduceCrash(cp, target)
    val aggregatedUsage = aggregateUsedInstructions(instructions)
    mergeClassInstructionUsage(usageStats, aggregatedUsage.filterKeys { it.declaration.location.isRuntime })

    logger.warn { "+".repeat(50) }
    logger.warn { "Found states: ${states.size}" }


    logger.warn {
        buildString {
            appendLine("USED METHODS")
            reportUsedInstructions(aggregatedUsage, this)
        }
    }
}

fun aggregateUsedInstructions(
    instructions: List<Map.Entry<JcInst, Int>>
): Map<JcClassOrInterface, Map<JcMethod, Int>> {
    val methods = instructions.groupBy(
        { it.key.callExpr?.method?.method ?: it.key.location.method },
        { it.value }
    ).mapValues { it.value.sum() }

    return methods.entries
        .groupBy { it.key.enclosingClass }
        .mapValues { (_, m) -> m.associate { it.key to it.value } }
}

fun mergeClassInstructionUsage(
    left: MutableMap<JcClassOrInterface, MutableMap<JcMethod, Int>>,
    right: Map<JcClassOrInterface, Map<JcMethod, Int>>
) {
    for ((cls, stats) in right) {
        val current = left.getOrPut(cls) { hashMapOf() }
        mergeMethodInstructionUsage(current, stats)
    }
}

fun mergeMethodInstructionUsage(left: MutableMap<JcMethod, Int>, right: Map<JcMethod, Int>) {
    for ((method, stats) in right) {
        val current = left[method] ?: 0
        left[method] = current + stats
    }
}

fun reportUsedInstructions(
    classes: Map<JcClassOrInterface, Map<JcMethod, Int>>,
    builder: StringBuilder
) {
    for ((cls, usedMethods) in classes.entries.sortedBy { it.value.values.sum() }) {
        builder.appendLine("-".repeat(20))
        builder.appendLine(cls.name)
        usedMethods.forEach {
            builder.append(it.key.name)
            builder.append(" | ")
            builder.appendLine(it.value)
        }
    }
}

private fun createTargets(
    cp: JcClasspath,
    exception: JcClassOrInterface,
    trace: List<TraceEntry>
): CrashReproductionTarget? {
    var initialTarget: CrashReproductionTarget? = null
    var currentTarget: CrashReproductionTarget? = null

    for (entry in trace) {
        val cls = cp.findClassOrNull(entry.className) ?: return null
        val method = cls.findMethodOrNull(entry.methodName, entry.methodDesc) ?: return null
        val instruction = method.instList.singleOrNull { it.location.index == entry.instructionIdx } ?: return null

        if (initialTarget == null) {
            val target = CrashReproductionLocationTarget(instruction)
            initialTarget = target
            currentTarget = target
            continue
        }

        val target = CrashReproductionLocationTarget(instruction)
        currentTarget?.addChild(target)
        currentTarget = target
    }

    val exceptionTarget = CrashReproductionExceptionTarget(exception)
    currentTarget?.addChild(exceptionTarget)

    return initialTarget
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
