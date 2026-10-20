package org.usvm.experiments

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.toType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcMethodCall
import org.usvm.machine.JcMethodEntrypointInst
import org.usvm.machine.JcResidualCallPolicy
import org.usvm.machine.JcTransparentInstruction
import org.usvm.machine.interpreter.JcSimpleValueResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.lastStmt
import org.usvm.solver.USolverQueryEvent
import org.usvm.statistics.UMachineObserver
import org.usvm.util.isUsvmInternalClass
import org.usvm.util.originalInst
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** One isolated class/mode cell; the Python supervisor retains timeouts and process failures. */
fun main(arguments: Array<String>) {
    require(arguments.size == 5) {
        "JavaModelPilot <manifest> <class-id> <OFF_STOP|OFF_FRESH|ON_STOP|ON_FRESH> <budget-ms> <output>"
    }
    val manifestPath = Path.of(arguments[0])
    val manifestText = manifestPath.readText()
    val manifest = Json.parseToJsonElement(manifestText).jsonObject
    val target = manifest.getValue("classes").jsonArray.map { it.jsonObject }
        .single { it.getValue("classId").jsonPrimitive.content == arguments[1] }
    val className = target.getValue("binaryName").jsonPrimitive.content
    val mode = arguments[2]
    require(mode in setOf("OFF_STOP", "OFF_FRESH", "ON_STOP", "ON_FRESH"))
    val approximationsEnabled = mode.startsWith("ON_")
    val enforceStringBound = manifest["enforceModelStringLengthBound"]?.let { configured ->
        requireNotNull(configured.jsonPrimitive.booleanOrNull) { "String model bound policy must be boolean" }
    } ?: true
    val residualPolicy = JcResidualCallPolicy.valueOf(mode.substringAfter('_'))
    val budgetMillis = arguments[3].toLong()
    require(budgetMillis > 0)
    val machineOptions = pilotMachineOptions(manifest = manifest, budgetMillis = budgetMillis)
    val output = Path.of(arguments[4]).toAbsolutePath()
    require(!Files.exists(output)) { "Java pilot cells require a fresh output path" }
    output.parent.createDirectories()
    val libraries = manifest.getValue("runtimeClasspath").jsonArray.map { File(it.jsonPrimitive.content) }
    require(libraries.all(File::isFile)) { "A frozen runtime dependency is missing" }
    val artifactFiles = manifest.getValue("artifacts").jsonArray.map { artifact ->
        artifact.jsonObject.getValue("files").jsonObject.getValue("binary").jsonObject
    }
    for (library in libraries) {
        val expected = artifactFiles.single { it.getValue("path").jsonPrimitive.content == library.absolutePath }
        require(sha256(library.readBytes()) == expected.getValue("sha256").jsonPrimitive.content) {
            "Frozen Java runtime dependency changed: $library"
        }
    }
    val loggingConfiguration = File(requireNotNull(System.getProperty("logback.configurationFile")))
    val loggingContext = LoggerFactory.getILoggerFactory() as LoggerContext
    require(loggingContext.getLogger(Logger.ROOT_LOGGER_NAME).level == Level.OFF) {
        "Java pilot measurements require an explicit Logback root OFF configuration"
    }

    val report = linkedMapOf<String, JsonElement>(
        "schema" to JsonPrimitive(1),
        "classId" to JsonPrimitive(arguments[1]),
        "className" to JsonPrimitive(className),
        "mode" to JsonPrimitive(mode),
        "methodApproximationsEnabled" to JsonPrimitive(approximationsEnabled),
        "jacodbApproximationsEnabled" to JsonPrimitive(approximationsEnabled),
        "enforceModelStringLengthBound" to JsonPrimitive(enforceStringBound),
        "residualCallPolicy" to JsonPrimitive(residualPolicy.name),
        "nativeReplayEnabled" to JsonPrimitive(false),
        "coverageEligible" to JsonPrimitive(false),
        "analysisCompleted" to JsonPrimitive(false),
        "status" to JsonPrimitive("PREPARING"),
        "budgetMillis" to JsonPrimitive(budgetMillis),
        "seed" to JsonPrimitive(machineOptions.randomSeed),
        "pathSelectionStrategy" to JsonPrimitive(machineOptions.pathSelectionStrategies.single().name),
        "solverLimitMillis" to JsonPrimitive(machineOptions.solverTimeout.inWholeMilliseconds),
        "timeQuantumMillis" to JsonPrimitive(machineOptions.maxEntryPointTimeQuantum.inWholeMilliseconds),
        "manifestSha256" to JsonPrimitive(sha256(manifestText.toByteArray())),
        "javaVersion" to JsonPrimitive(System.getProperty("java.version")),
        "logbackRootLevel" to JsonPrimitive("OFF"),
        "logbackConfiguration" to JsonPrimitive(loggingConfiguration.absolutePath),
        "logbackConfigurationSha256" to JsonPrimitive(sha256(loggingConfiguration.readBytes())),
        "nativeCoverageAvailable" to JsonPrimitive(false),
        "nativeCoverageLimitation" to JsonPrimitive(
            "Native replay and native coverage are disabled for this raw class-IR experiment"
        ),
        "instanceInputContract" to JsonPrimitive(
            "Independent symbolic receiver per entrypoint, no constructor lifecycle"
        ),
    )
    fun save() {
        val temporary = output.resolveSibling("${output.fileName}.tmp")
        temporary.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(report)))
        Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    save()
    val started = TimeSource.Monotonic.markNow()

    try {
        runBlocking {
            withPilotClasspath(libraries = libraries, approximationsEnabled = approximationsEnabled) { classpath ->
                report["classpathPreparedMillis"] = JsonPrimitive(started.elapsedNow().inWholeMilliseconds)
                analyzeClass(
                    classpath = classpath,
                    target = target,
                    approximationsEnabled = approximationsEnabled,
                    enforceStringBound = enforceStringBound,
                    options = machineOptions,
                    residualPolicy = residualPolicy,
                    report = report,
                    save = ::save,
                )
            }
        }
    } catch (failure: Throwable) {
        report["preparationFailure"] = failureJson(failure)
        report["status"] = JsonPrimitive("PREPARATION_ERROR")
        report["coverageEligible"] = JsonPrimitive(false)
    } finally {
        report["totalMillis"] = JsonPrimitive(started.elapsedNow().inWholeMilliseconds)
        save()
    }
}

internal fun pilotMachineOptions(manifest: JsonObject, budgetMillis: Long): UMachineOptions {
    val strategy = manifest["pathSelectionStrategy"]?.jsonPrimitive?.content?.let(PathSelectionStrategy::valueOf)
        ?: PathSelectionStrategy.BFS
    val solverLimitMillis = manifest["solverLimitMillis"]?.jsonPrimitive?.long ?: 1_000L
    val timeQuantumMillis = manifest["timeQuantumMillis"]?.jsonPrimitive?.long ?: 250L
    val seed = manifest["seed"]?.jsonPrimitive?.long ?: 17L
    require(solverLimitMillis in 1..budgetMillis) { "Solver timeout must fit the class budget" }
    require(timeQuantumMillis in 1..budgetMillis) { "Entry point quantum must fit the class budget" }

    return UMachineOptions(
        pathSelectionStrategies = listOf(strategy),
        stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
        randomSeed = seed,
        stopOnCoverage = 0,
        timeout = budgetMillis.milliseconds,
        coverageZone = CoverageZone.CLASS,
        solverType = SolverType.YICES,
        solverTimeout = solverLimitMillis.milliseconds,
        throwExceptionOnStepFailure = true,
        maxEntryPointTimeQuantum = timeQuantumMillis.milliseconds,
        removeCoveredEntryPoints = false,
    )
}

private fun analyzeClass(
    classpath: JcClasspath,
    target: JsonObject,
    approximationsEnabled: Boolean,
    enforceStringBound: Boolean,
    options: UMachineOptions,
    residualPolicy: JcResidualCallPolicy,
    report: MutableMap<String, JsonElement>,
    save: () -> Unit,
) {
    val className = target.getValue("binaryName").jsonPrimitive.content
    val owner = requireNotNull(classpath.findClassOrNull(className)) { "Class not found: $className" }
    val bytecodeSha256 = sha256(owner.bytecode())
    require(bytecodeSha256 == target.getValue("classSha256").jsonPrimitive.content) {
        "Symbolic class bytecode differs from the frozen cohort: $className"
    }
    report["classBytecodeSha256"] = JsonPrimitive(bytecodeSha256)
    val methods = target.getValue("entryPoints").jsonArray.map { entry ->
        val metadata = entry.jsonObject
        val name = metadata.getValue("name").jsonPrimitive.content
        val descriptor = metadata.getValue("descriptor").jsonPrimitive.content
        owner.declaredMethods.single { it.name == name && it.description == descriptor }
    }
    require(methods.isNotEmpty()) { "Frozen class has no selected entrypoints" }

    val universeFailures = linkedMapOf<String, String>()
    val universe = owner.declaredMethods.flatMap { method ->
        try {
            method.instList.map { instruction -> instruction.pointId() }
        } catch (failure: Throwable) {
            universeFailures[method.methodId()] = failure.stackTraceToString()
            emptyList()
        }
    }.toSet()
    report["denominatorFailures"] = textMap(universeFailures)
    report["classUniverse"] = strings(universe.sorted())
    report["classUniverseSize"] = JsonPrimitive(universe.size)
    report["entryPoints"] = strings(methods.map(JcMethod::methodId))
    runMachine(
        classpath = classpath,
        methods = methods,
        universe = universe,
        approximationsEnabled = approximationsEnabled,
        enforceStringBound = enforceStringBound,
        options = options,
        residualPolicy = residualPolicy,
        report = report,
        save = save,
    )
}

private fun runMachine(
    classpath: JcClasspath,
    methods: List<JcMethod>,
    universe: Set<String>,
    approximationsEnabled: Boolean,
    enforceStringBound: Boolean,
    options: UMachineOptions,
    residualPolicy: JcResidualCallPolicy,
    report: MutableMap<String, JsonElement>,
    save: () -> Unit,
) {
    val searchStarted = TimeSource.Monotonic.markNow()
    report["status"] = JsonPrimitive("ANALYZING")
    val className = methods.first().enclosingClass.name
    val observer = PilotObserver(className, universe) { snapshot ->
        report["rawAnalysis"] = snapshot
        report["searchMillis"] = JsonPrimitive(searchStarted.elapsedNow().inWholeMilliseconds)
        save()
    }
    val globalArrayLengthLimit = Int.MAX_VALUE
    report["arrayMaxSize"] = JsonPrimitive(globalArrayLengthLimit)
    report["arrayLengthLimitScope"] = JsonPrimitive(
        "Global Java array length, including program and library allocations"
    )
    save()
    JcMachine(
        cp = classpath,
        options = options,
        jcMachineOptions = JcMachineOptions(
            arrayMaxSize = globalArrayLengthLimit,
            enableMethodApproximations = approximationsEnabled,
            enforceModelStringLengthBound = enforceStringBound,
            residualCallPolicy = residualPolicy,
        ),
        interpreterObserver = observer,
    ).use { machine ->
        report["analysisSetupMillis"] = JsonPrimitive(searchStarted.elapsedNow().inWholeMilliseconds)

        try {
            machine.analyze(methods)
            report["analysisCompleted"] = JsonPrimitive(true)
            val snapshot = observer.json()
            val denominatorValid = report.getValue("denominatorFailures").jsonObject.isEmpty()
            val mappingValid = snapshot.getValue("outsideClassUniverse").jsonArray.isEmpty()
            report["coverageEligible"] = JsonPrimitive(denominatorValid && mappingValid)
            val hasPendingStates = snapshot["hasPendingStates"]?.jsonPrimitive?.booleanOrNull == true
            val status = when {
                !denominatorValid -> "DENOMINATOR_ERROR"
                !mappingValid -> "IR_MAPPING_ERROR"
                hasPendingStates -> "BUDGET_TIMEOUT"
                else -> "COMPLETED"
            }
            report["status"] = JsonPrimitive(status)
        } catch (failure: Throwable) {
            report["analysisCompleted"] = JsonPrimitive(false)
            report["analysisFailure"] = failureJson(failure)
            report["status"] = JsonPrimitive("ENGINE_ERROR")
            report["coverageEligible"] = JsonPrimitive(false)
        } finally {
            report["searchMillis"] = JsonPrimitive(searchStarted.elapsedNow().inWholeMilliseconds)
            report["rawAnalysis"] = observer.json()
            save()
        }
    }
}

private class PilotObserver(
    private val className: String,
    private val universe: Set<String>,
    private val checkpoint: (JsonObject) -> Unit,
) : JcInterpreterObserver, UMachineObserver<JcState> {
    private val visited = hashSetOf<String>()
    private val outsideUniverse = hashSetOf<String>()
    private val entered = hashSetOf<String>()
    private val methodSteps = hashMapOf<String, Long>()
    private val approximations = hashMapOf<String, Long>()
    private val solverStatuses = hashMapOf<String, Long>()
    private var steps = 0L
    private var auxiliarySteps = 0L
    private var stoppedPrefixes = 0L
    private val residualCalls = hashMapOf<String, Long>()
    private val stoppedPrefixExamples = mutableListOf<String>()
    private var approximationFailures = 0L
    private var normalTerminations = 0L
    private var exceptionalTerminations = 0L
    private var unreachableTerminations = 0L
    private var approximationAttempts = 0L
    private var approximationNanos = 0L
    private var solverChecks = 0L
    private var solverNanos = 0L
    private var artifactOwnedSteps = 0L
    private var lastCheckpointNanos = System.nanoTime()
    private var machineLoopStartedNanos: Long? = null
    private var machineLoopStoppedNanos: Long? = null
    private var stopReason: String? = null
    private var hasPendingStates: Boolean? = null

    override fun onMachineStarted() {
        machineLoopStartedNanos = System.nanoTime()
    }

    override fun onMachineStopped() {
        machineLoopStoppedNanos = System.nanoTime()
    }

    override fun onAnalysisStopped(reason: String, hasPendingStates: Boolean) {
        stopReason = reason
        this.hasPendingStates = hasPendingStates
    }

    override fun onEntryPoint(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcMethodEntrypointInst,
        stepScope: JcStepScope,
    ) {
        entered += stmt.method.methodId()
    }

    override fun onStatePeeked(state: JcState) {
        steps++
        val instruction = state.lastStmt
        if (instruction is JcTransparentInstruction) {
            auxiliarySteps++
            return
        }

        val owner = instruction.location.method.enclosingClass
        val method = instruction.location.method.methodId()
        methodSteps[method] = methodSteps.getOrDefault(method, 0) + 1
        if (owner.isUsvmInternalClass) artifactOwnedSteps++
        if (owner.name == className) {
            val point = instruction.pointId()
            if (point in universe) visited += point else outsideUniverse += point
        }
        if (steps % 256L == 0L) {
            val now = System.nanoTime()
            val checkpointDue = now - lastCheckpointNanos >= 5_000_000_000L
            if (checkpointDue) {
                checkpoint(json())
                lastCheckpointNanos = now
            }
        }
    }

    override fun onStateTerminated(state: JcState, stateReachable: Boolean) {
        if (!stateReachable) {
            unreachableTerminations++
            return
        }
        if (state.residualCallStop != null) {
            stoppedPrefixes++
            if (stoppedPrefixExamples.size < 16) {
                stoppedPrefixExamples += "${state.entrypoint.methodId()}: ${state.lastStmt.pointId()}"
            }
            return
        }

        when (state.methodResult) {
            is JcMethodResult.Success -> normalTerminations++
            is JcMethodResult.JcException -> exceptionalTerminations++
            JcMethodResult.NoCall -> return
        }
    }

    override fun onResidualCall(call: JcMethodCall, policy: JcResidualCallPolicy, scope: JcStepScope) {
        val key = "${policy.name}:${call.method.methodId()}"
        residualCalls[key] = residualCalls.getOrDefault(key, 0) + 1
    }

    override fun onMethodApproximation(call: JcMethodCall, applied: Boolean, elapsedNanos: Long, failure: String?) {
        approximationAttempts++
        if (failure != null) approximationFailures++
        approximationNanos += elapsedNanos
        if (applied) {
            val key = call.method.methodId()
            approximations[key] = approximations.getOrDefault(key, 0) + 1
        }
    }

    override fun onSolverQuery(event: USolverQueryEvent) {
        solverChecks++
        solverNanos += event.elapsedNanos
        val key = event.failure ?: event.status.toString()
        solverStatuses[key] = solverStatuses.getOrDefault(key, 0) + 1
    }

    fun json(): JsonObject = buildJsonObject {
        val machineLoopNanos = machineLoopStartedNanos?.let { started ->
            (machineLoopStoppedNanos ?: System.nanoTime()) - started
        }
        put("steps", steps)
        put("auxiliarySteps", auxiliarySteps)
        put("machineLoopNanos", machineLoopNanos?.let(::JsonPrimitive) ?: JsonNull)
        put("stopReason", stopReason?.let(::JsonPrimitive) ?: JsonNull)
        put("hasPendingStates", hasPendingStates?.let(::JsonPrimitive) ?: JsonNull)
        put("enteredMethods", strings(entered.sorted()))
        put("visitedClassPoints", strings(visited.sorted()))
        put("visitedClassPointsCount", visited.size)
        put("outsideClassUniverse", strings(outsideUniverse.sorted()))
        put("stoppedPrefixes", stoppedPrefixes)
        put("stoppedPrefixExamples", strings(stoppedPrefixExamples))
        put("residualCalls", counts(residualCalls))
        put("normalTerminations", normalTerminations)
        put("exceptionalTerminations", exceptionalTerminations)
        put("unreachableTerminations", unreachableTerminations)
        put("approximationAttempts", approximationAttempts)
        put("approximationFailures", approximationFailures)
        put("appliedApproximations", counts(approximations))
        put("approximationNanos", approximationNanos)
        put("approximationArtifactOwnedSteps", artifactOwnedSteps)
        put("solverChecks", solverChecks)
        put("solverBackendCheckNanos", solverNanos)
        put("solverStatuses", counts(solverStatuses))
        put("methodSteps", counts(methodSteps))
    }
}

private fun JcMethod.methodId(): String = "${enclosingClass.name}#$name$description"
private fun JcInst.pointId(): String = "${location.method.methodId()}:${location.index}"
private fun strings(values: Collection<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))
private fun counts(values: Map<String, Long>): JsonObject = buildJsonObject {
    values.toSortedMap().forEach { (key, value) -> put(key, value) }
}
private fun textMap(values: Map<String, String>): JsonObject = JsonObject(values.mapValues { JsonPrimitive(it.value) })
private fun failureJson(failure: Throwable): JsonObject = buildJsonObject {
    put("type", failure.javaClass.name)
    put("message", failure.message)
    put("stackTrace", failure.stackTraceToString())
}
private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString(separator = "") { "%02x".format(it) }
