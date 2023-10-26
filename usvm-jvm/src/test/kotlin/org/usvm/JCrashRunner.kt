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
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcThrowInst
import org.jacodb.api.ext.cfg.arrayRef
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.cfg.fieldRef
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.findMethodOrNull
import org.jacodb.api.ext.isAssignable
import org.jacodb.api.ext.isSubClassOf
import org.jacodb.api.ext.toType
import org.jacodb.approximation.Approximations
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.jacodb
import org.usvm.api.crash.JcCrashReproduction
import org.usvm.util.canBeOverridden
import org.usvm.util.classpathWithApproximations
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    val entries: List<List<TraceEntry>>,
    val exception: TraceException
)

@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    val crashPackPath = Path(args.first())

    val crashPackDescriptionPath = crashPackPath / "jcrashpack.json"
    val crashPack = Json.decodeFromStream<CrashPack>(crashPackDescriptionPath.inputStream())

//    parseCrashTraces(crashPackPath, crashPack)
    val traces = loadCrashTraces(crashPackPath) ?: error("No traces")

    analyzeCrashes(crashPackPath, crashPack, traces)
}

private val json = Json {
    prettyPrint = true
}

const val traceFileName = "traces_new.json"

@OptIn(ExperimentalSerializationApi::class)
fun parseCrashTraces(crashPackPath: Path, crashPack: CrashPack) {
    val current = loadCrashTraces(crashPackPath) ?: emptyMap()

    val parsed = runBlocking {
        crashPack.crashes.values.mapIndexedNotNull { index, it ->
            println("PARSE ${it.id} | $index / ${crashPack.crashes.size}")
            try {
                val trace = current[it.id]
                    ?: parseTrace(crashPackPath, it)
                    ?: return@mapIndexedNotNull null
                it to trace
            } catch (ex: Throwable) {
                System.err.println(ex)
                null
            }
        }
    }

    println("PARSED ${parsed.size} (${parsed.count { it.second.entries.size == 1 }}) TOTAL ${crashPack.crashes.size}")

    val traces = parsed.associate { it.first.id to it.second }

    val crashPackTracesPath = crashPackPath / traceFileName
    json.encodeToStream(traces, crashPackTracesPath.outputStream())
}

@OptIn(ExperimentalSerializationApi::class)
fun loadCrashTraces(crashPackPath: Path): Map<String, CrashTrace>? {
    val crashPackTracesPath = crashPackPath / traceFileName
    if (!crashPackTracesPath.exists()) return null
    return json.decodeFromStream(crashPackTracesPath.inputStream())
}

private suspend fun parseTrace(crashPackPath: Path, crash: CrashPackCrash): CrashTrace? {
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

data class UnresolvedTraceEntry(val clsName: String, val methodName: String, val line: Int)
data class ClassTraceEntry(val cls: JcClassOrInterface, val unresolved: UnresolvedTraceEntry)
data class MethodTraceEntry(val cls: JcClassOrInterface, val method: JcMethod, val line: Int)
data class ResolvedTraceEntry(val cls: JcClassOrInterface, val method: JcMethod, val inst: JcInst)

private fun parseTrace(cp: JcClasspath, trace: String, crash: CrashPackCrash): CrashTrace? {
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
            UnresolvedTraceEntry(className, methodName, lineNumber)
        }
        .asReversed()

    val classTrace = resolveTraceClasses(cp, rawTraceEntries) ?: return null
    val methodTrace = resolveTraceMethods(classTrace) ?: return null
    val resolvedTraces = resolveTraceInstructions(exceptionType, methodTrace) ?: return null

    val serializableTraces = serializeTraces(resolvedTraces)
    return CrashTrace(trace, serializableTraces, TraceException(exceptionType.name))
}

private fun serializeTraces(traces: List<List<ResolvedTraceEntry>>): List<List<TraceEntry>> =
    traces.map { resolvedTrace ->
        resolvedTrace.map {
            TraceEntry(it.cls.name, it.method.name, it.method.description, it.inst.location.index)
        }
    }

private fun deserializeTraces(cp: JcClasspath, traces: List<List<TraceEntry>>): List<List<ResolvedTraceEntry>>? {
    return traces.map { trace ->
        trace.map { entry ->
            val cls = cp.findClassOrNull(entry.className) ?: return null
            val method = cls.findMethodOrNull(entry.methodName, entry.methodDesc) ?: return null
            val instruction = method.instList.singleOrNull { it.location.index == entry.instructionIdx } ?: return null
            ResolvedTraceEntry(cls, method, instruction)
        }
    }
}

private fun resolveTraceClasses(cp: JcClasspath, trace: List<UnresolvedTraceEntry>): List<ClassTraceEntry>? {
    return trace.map {
        ClassTraceEntry(
            cls = cp.findClassOrNull(it.clsName) ?: return null,
            unresolved = it
        )
    }
}

private fun resolveTraceMethods(trace: List<ClassTraceEntry>): List<MethodTraceEntry>? {
    val result = mutableListOf<MethodTraceEntry>()

    for (entry in trace) {
        val methodsWithSameName = entry.cls.declaredMethods.filter { it.name == entry.unresolved.methodName }
        if (methodsWithSameName.size == 1) {
            result += MethodTraceEntry(entry.cls, methodsWithSameName.single(), entry.unresolved.line)
            continue
        }

        val methodsWithLine = methodsWithSameName.filter {
            val lines = it.instList.mapTo(hashSetOf()) { it.lineNumber }
            entry.unresolved.line in lines
        }

        if (methodsWithLine.size == 1) {
            result += MethodTraceEntry(entry.cls, methodsWithLine.single(), entry.unresolved.line)
            continue
        }

        logger.warn { "Can't identify method" }
        return null
    }

    return result
}

private fun resolveTraceInstructions(
    crashException: JcClassOrInterface,
    trace: List<MethodTraceEntry>
): List<List<ResolvedTraceEntry>>? {
    val resolved = mutableListOf<List<ResolvedTraceEntry>>()

    for ((entry, nextEntry) in trace.zipWithNext()) {
        val correctCallInstructions = entry.method.instList.filter {
            it.hasMethodCall(nextEntry.method)
        }

        if (correctCallInstructions.size == 1) {
            resolved += listOf(ResolvedTraceEntry(entry.cls, entry.method, correctCallInstructions.single()))
            continue
        }

        val sameLineInstructions = correctCallInstructions.filter { it.lineNumber == entry.line }
        if (sameLineInstructions.isNotEmpty()) {
            resolved += sameLineInstructions.map { ResolvedTraceEntry(entry.cls, entry.method, it) }
            continue
        }

        val nextLineInstructions = correctCallInstructions.filter { it.lineNumber == entry.line + 1 }
        if (nextLineInstructions.size == 1) {
            resolved += listOf(ResolvedTraceEntry(entry.cls, entry.method, nextLineInstructions.single()))
            continue
        }

        logger.warn { "Can't identify intermediate instruction" }
        return null
    }

    resolved += resolveTraceCrashInstruction(crashException, trace.last()) ?: return null

    var result = resolved.first().map { listOf(it) }
    for (entry in resolved.drop(1)) {
        val current = result
        result = entry.flatMap { resolvedEntry -> current.map { it + resolvedEntry } }
    }

    return result
}

private fun resolveTraceCrashInstruction(
    crashException: JcClassOrInterface,
    entry: MethodTraceEntry
): List<ResolvedTraceEntry>? {

    if (crashException == crashException.classpath.findClassOrNull("java.lang.ArrayIndexOutOfBoundsException")) {
        val iobInstructions = entry.method.instList.filter { it.canProduceIob() }
        if (iobInstructions.isEmpty()) {
            logger.warn { "Can't identify crash source instruction: ${crashException.name}" }
            return null
        }

        val instructions = iobInstructions
            .filter { it.lineNumber == entry.line }
            .takeIf { it.isNotEmpty() }
            ?: iobInstructions

        return instructions.map { ResolvedTraceEntry(entry.cls, entry.method, it) }
    }

    if (crashException == crashException.classpath.findClassOrNull("java.lang.NullPointerException")) {
        val npeInstructions = entry.method.instList.filter { it.canProduceNpe() }

        if (npeInstructions.isEmpty()) {
            logger.warn { "Can't identify crash source instruction: ${crashException.name}" }
            return null
        }

        val instructions = npeInstructions
            .filter { it.lineNumber == entry.line }
            .takeIf { it.isNotEmpty() }
            ?: npeInstructions

        return instructions.map { ResolvedTraceEntry(entry.cls, entry.method, it) }
    }

    val throwInstructions = entry.method.instList.filterIsInstance<JcThrowInst>()
    val sameLineThrow = throwInstructions.filter { it.lineNumber == entry.line }

    if (sameLineThrow.isNotEmpty()) {
        return sameLineThrow.map { ResolvedTraceEntry(entry.cls, entry.method, it) }
    }

    val sameException = throwInstructions.filter { it.throwable.type == crashException.toType() }
    if (sameException.isNotEmpty()) {
        return sameException.map { ResolvedTraceEntry(entry.cls, entry.method, it) }
    }

    logger.warn { "Can't identify crash source instruction: ${crashException.name}" }
    return null
}

private fun JcInst.hasMethodCall(method: JcMethod): Boolean {
    val currentMethod = callExpr?.method?.method ?: return false
    if (currentMethod == method) return true
    if (!currentMethod.canBeOverridden()) return false
    if (currentMethod.name != method.name) return false
    if (currentMethod.parameters.size != method.parameters.size) return false
    if (!method.enclosingClass.isSubClassOf(currentMethod.enclosingClass)) return false
    if (currentMethod.description == method.description) return true
    val cp = currentMethod.enclosingClass.classpath
    for ((cur, tgt) in currentMethod.parameters.zip(method.parameters)) {
        if (cur.type == tgt.type) continue
        val curType = cp.findTypeOrNull(cur.type.typeName) ?: return false
        val tgtType = cp.findTypeOrNull(tgt.type.typeName) ?: return false
        if (!tgtType.isAssignable(curType)) return false
    }
    return true
}

private fun JcInst.canProduceNpe(): Boolean {
    val nullSources = listOfNotNull(
        fieldRef?.instance,
        arrayRef?.array,
        (callExpr as? JcInstanceCallExpr)?.instance
    )
    return nullSources.any { it is JcLocal && it !is JcThis }
}

private fun JcInst.canProduceIob(): Boolean = arrayRef != null

const val idToCheck = "CHART-13b"
val timeout = 10.seconds

fun analyzeCrashes(crashPackPath: Path, crashPack: CrashPack, traces: Map<String, CrashTrace>) {
    val crashes = crashPack.crashes.values
        .sortedBy { it.id }
        .filter { traces[it.id]?.entries?.size == 1 } // todo: ???
        .filter { it.id == idToCheck }
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
}

fun analyzeCrash(crashPackPath: Path, crash: CrashPackCrash, trace: CrashTrace) {
    val crashCp = crashPackPath / "applications" / crash.application / crash.version / "bin"

    val cpFiles = crashCp.listDirectoryEntries("*.jar").map { it.toFile() }

    val jcDb = runBlocking {
        jacodb {
            useProcessJavaRuntime()
            installFeatures(InMemoryHierarchy, Approximations)
            loadByteCode(cpFiles)
        }
    }

    jcDb.use { db ->
        val jcCp = runBlocking {
            db.awaitBackgroundJobs()
            db.classpathWithApproximations(cpFiles, listOf(UnknownClasses))
        }

        jcCp.use { cp ->
            runWithHardTimout(timeout * 2) {
                analyzeCrash(cp, trace, crash)
            }
        }
    }
}

private fun analyzeCrash(cp: JcClasspath, trace: CrashTrace, crash: CrashPackCrash) {
    logger.warn { "#".repeat(50) }
    logger.warn { "Try reproduce crash: ${crash.application} | ${crash.id}" }
    logger.warn { "\n${trace.original}" }

    val exceptionType = cp.findClassOrNull(trace.exception.className) ?: return
    val traces = deserializeTraces(cp, trace.entries) ?: return

    val traceToAnalyze = traces.singleOrNull() ?: TODO("Many traces")

    val traceFrames = traceToAnalyze.map { JcCrashReproduction.CrashStackTraceFrame(it.method, it.inst) }

    val reproduction = JcCrashReproduction(cp, timeout)

    logger.warn { "-".repeat(50) }

    val result = reproduction.reproduceCrash(exceptionType, traceFrames)

    logger.warn { "+".repeat(50) }
    logger.warn { "Trace reproduction result: $result" }
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
