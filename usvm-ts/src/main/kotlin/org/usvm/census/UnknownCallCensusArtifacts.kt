package org.usvm.census

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal const val CENSUS_SCHEMA_VERSION = 2

internal val censusJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

@Serializable
internal data class UnknownCallCensusManifest(
    val schemaVersion: Int,
    val projects: List<UnknownCallCensusProject>,
    val profiles: List<UnknownCallCensusProfile> = listOf(UnknownCallCensusProfile.EMPTY_FRESH),
    val randomSeed: Long = 0,
    val limits: UnknownCallCensusLimits = UnknownCallCensusLimits(),
)

@Serializable
internal data class UnknownCallCensusProject(
    val id: String,
    val repository: String,
    val revision: String,
    val path: String,
    val license: String,
    val licenseFile: String,
    val include: List<String> = emptyList(),
    val includeSuffixes: List<String> = listOf(".ts"),
    val excludeSuffixes: List<String> = listOf(".test.ts", ".spec.ts", ".d.ts"),
)

@Serializable
internal enum class UnknownCallCensusProfile {
    EMPTY_FRESH,
    EMPTY_STOP,
}

@Serializable
internal data class UnknownCallCensusLimits(
    val projectTimeoutSeconds: Long = 300,
    val methodTimeoutSeconds: Long = 10,
    val maxClasses: Int = 100,
    val maxMethods: Int = 1_000,
    val minMethodsPerClass: Int = 1,
    val minStatementsPerMethod: Int = 1,
)

@Serializable
internal data class UnknownCallCensusSummary(
    val schemaVersion: Int = CENSUS_SCHEMA_VERSION,
    val profiles: Map<String, UnknownCallCensusProfileSummary>,
    val input: UnknownCallCensusInput,
)

@Serializable
internal data class UnknownCallCensusInput(
    val completeness: UnknownCallCensusCompleteness,
    val issues: List<UnknownCallCensusInputIssue>,
)

@Serializable
internal enum class UnknownCallCensusCompleteness { COMPLETE, PARTIAL }

@Serializable
internal enum class UnknownCallCensusInputIssue {
    MISSING_RUN_START,
    MISSING_RUN_RESULT,
    INVALID_RUN_START,
    INVALID_RUN_RESULT,
    DUPLICATE_RUN_START,
    DUPLICATE_RUN_RESULT,
    OUT_OF_ORDER_MARKERS,
    DATA_OUTSIDE_RUN,
    UNSUPPORTED_DATA_SCHEMA,
}

@Serializable
internal data class UnknownCallCensusProfileSummary(
    val projects: Int,
    val functionsAnalyzed: Int,
    val functionsCompleted: Int,
    val functionsPartial: Int,
    val functionsWithUnknownCalls: Int,
    val uniqueSites: Int,
    val rawEvents: Int,
    val eventsByFailureReason: Map<String, Int>,
    val eventsByDecision: Map<String, Int>,
    val eventsByCallee: Map<String, Int>,
    val uniqueSitesByCallee: Map<String, Int>,
    val partials: List<UnknownCallCensusIssue>,
    val timeouts: List<UnknownCallCensusIssue>,
    val errors: List<UnknownCallCensusIssue>,
)

@Serializable
internal data class UnknownCallCensusIssue(
    val projectId: String,
    val functionId: String? = null,
    val message: String? = null,
    val failureCount: Int = 0,
)

internal object UnknownCallCensusAggregator {
    fun summarize(lines: Sequence<String>): UnknownCallCensusSummary {
        val profiles = linkedMapOf<String, ProfileAccumulator>()
        val inputIssues = linkedSetOf<UnknownCallCensusInputIssue>()
        var runStartSeen = false
        var runResultSeen = false
        var dataSeen = false
        var startedAt: String? = null

        lines.forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed

            val record = censusJson.parseToJsonElement(line) as? JsonObject
                ?: error("Raw census record ${index + 1} is not a JSON object")
            val kind = record.requiredString("kind", index)
            when (kind) {
                "run_start" -> {
                    if (runStartSeen) inputIssues += UnknownCallCensusInputIssue.DUPLICATE_RUN_START
                    if (dataSeen || runResultSeen) inputIssues += UnknownCallCensusInputIssue.OUT_OF_ORDER_MARKERS
                    val markerStartedAt = record.optionalString("startedAt")
                    val validSchema = record.optionalInt("schemaVersion") == CENSUS_SCHEMA_VERSION
                    if (!validSchema || markerStartedAt.isNullOrBlank()) {
                        inputIssues += UnknownCallCensusInputIssue.INVALID_RUN_START
                    }
                    startedAt = markerStartedAt
                    runStartSeen = true
                    return@forEachIndexed
                }
                "run_result" -> {
                    if (runResultSeen) inputIssues += UnknownCallCensusInputIssue.DUPLICATE_RUN_RESULT
                    if (!runStartSeen) inputIssues += UnknownCallCensusInputIssue.OUT_OF_ORDER_MARKERS
                    val validSchema = record.optionalInt("schemaVersion") == CENSUS_SCHEMA_VERSION
                    val completed = record.optionalString("status") == "completed"
                    val matchingStart = record.optionalString("startedAt") == startedAt && !startedAt.isNullOrBlank()
                    val hasFinish = !record.optionalString("finishedAt").isNullOrBlank()
                    val validRunResult = validSchema && completed && matchingStart && hasFinish
                    if (!validRunResult) {
                        inputIssues += UnknownCallCensusInputIssue.INVALID_RUN_RESULT
                    }
                    runResultSeen = true
                    return@forEachIndexed
                }
            }

            dataSeen = true
            if (!runStartSeen || runResultSeen) inputIssues += UnknownCallCensusInputIssue.DATA_OUTSIDE_RUN
            if (record.optionalInt("schemaVersion") != CENSUS_SCHEMA_VERSION) {
                inputIssues += UnknownCallCensusInputIssue.UNSUPPORTED_DATA_SCHEMA
            }

            val profile = record.requiredString("profile", index)
            val accumulator = profiles.getOrPut(profile, ::ProfileAccumulator)

            when (kind) {
                "unknown_call" -> accumulator.addUnknownCall(record, index)
                "method_result" -> accumulator.addMethodResult(record, index)
                "project_result" -> accumulator.addProjectResult(record, index)
                else -> error("Raw census record ${index + 1} has unknown kind '$kind'")
            }
        }

        if (!runStartSeen) inputIssues += UnknownCallCensusInputIssue.MISSING_RUN_START
        if (!runResultSeen) inputIssues += UnknownCallCensusInputIssue.MISSING_RUN_RESULT

        val completeness = if (inputIssues.isEmpty()) {
            UnknownCallCensusCompleteness.COMPLETE
        } else {
            UnknownCallCensusCompleteness.PARTIAL
        }
        val summaries = profiles.toSortedMap().mapValues { (_, accumulator) -> accumulator.toSummary() }
        val input = UnknownCallCensusInput(completeness = completeness, issues = inputIssues.toList())

        return UnknownCallCensusSummary(
            profiles = summaries,
            input = input,
        )
    }
}

private class ProfileAccumulator {
    private val projects = hashSetOf<String>()
    private val functions = hashSetOf<String>()
    private val completedFunctions = hashSetOf<String>()
    private val partialFunctions = hashSetOf<String>()
    private val functionsWithUnknownCalls = hashSetOf<String>()
    private val sites = hashSetOf<String>()
    private var rawEvents = 0
    private val eventsByFailureReason = hashMapOf<String, Int>()
    private val eventsByDecision = hashMapOf<String, Int>()
    private val eventsByCallee = hashMapOf<String, Int>()
    private val sitesByCallee = hashMapOf<String, MutableSet<String>>()
    private val partials = mutableListOf<UnknownCallCensusIssue>()
    private val timeouts = mutableListOf<UnknownCallCensusIssue>()
    private val errors = mutableListOf<UnknownCallCensusIssue>()

    fun addUnknownCall(record: JsonObject, index: Int) {
        val projectId = record.requiredString("projectId", index)
        val functionId = record.requiredString("functionId", index)
        val siteId = record.requiredString("siteId", index)
        val calleeId = record.requiredString("calleeId", index)
        val failureReason = record.requiredString("failureReason", index)
        val decision = record.requiredString("decision", index)

        projects += projectId
        functionsWithUnknownCalls += functionId
        sites += siteId
        rawEvents++
        eventsByFailureReason.increment(failureReason)
        eventsByDecision.increment(decision)
        eventsByCallee.increment(calleeId)
        sitesByCallee.getOrPut(calleeId, ::hashSetOf).add(siteId)
    }

    fun addMethodResult(record: JsonObject, index: Int) {
        val projectId = record.requiredString("projectId", index)
        val functionId = record.requiredString("functionId", index)
        val rawStatus = record.requiredString("status", index)
        val matchingStatus = MethodStatus.entries.firstOrNull { it.serializedName == rawStatus }
        val status = requireNotNull(matchingStatus) {
            "Raw census line ${index + 1} has unsupported method_result status '$rawStatus'"
        }
        val issue = UnknownCallCensusIssue(
            projectId = projectId,
            functionId = functionId,
            message = record.optionalString("error"),
            failureCount = record.optionalInt("failureCount") ?: 0,
        )

        projects += projectId
        functions += functionId
        when (status) {
            MethodStatus.COMPLETED -> completedFunctions += functionId
            MethodStatus.PARTIAL -> {
                partialFunctions += functionId
                partials += issue
            }
            MethodStatus.TIMEOUT -> timeouts += issue
            MethodStatus.TOOL_ERROR -> errors += issue
        }
    }

    fun addProjectResult(record: JsonObject, index: Int) {
        val projectId = record.requiredString("projectId", index)
        val status = record.requiredString("status", index)
        require(status in projectResultStatuses) {
            "Raw census line ${index + 1} has unsupported project_result status '$status'"
        }
        val issue = UnknownCallCensusIssue(
            projectId = projectId,
            message = record.optionalString("error"),
        )

        projects += projectId
        when (status) {
            "completed" -> Unit
            "timeout" -> timeouts += issue
            "tool_error" -> errors += issue
        }
    }

    fun toSummary(): UnknownCallCensusProfileSummary = UnknownCallCensusProfileSummary(
        projects = projects.size,
        functionsAnalyzed = functions.size,
        functionsCompleted = completedFunctions.size,
        functionsPartial = partialFunctions.size,
        functionsWithUnknownCalls = functionsWithUnknownCalls.size,
        uniqueSites = sites.size,
        rawEvents = rawEvents,
        eventsByFailureReason = eventsByFailureReason.toSortedMap(),
        eventsByDecision = eventsByDecision.toSortedMap(),
        eventsByCallee = eventsByCallee.toSortedMap(),
        uniqueSitesByCallee = sitesByCallee.toSortedMap().mapValues { (_, sites) -> sites.size },
        partials = partials.sortedWith(issueComparator),
        timeouts = timeouts.sortedWith(issueComparator),
        errors = errors.sortedWith(issueComparator),
    )
}

private val projectResultStatuses = setOf("completed", "timeout", "tool_error")

private val issueComparator = compareBy<UnknownCallCensusIssue>(
    UnknownCallCensusIssue::projectId,
    { it.functionId.orEmpty() },
    { it.message.orEmpty() },
)

private fun MutableMap<String, Int>.increment(key: String) {
    this[key] = getOrDefault(key, defaultValue = 0) + 1
}

private fun JsonObject.requiredString(name: String, recordIndex: Int): String =
    optionalString(name) ?: error("Raw census record ${recordIndex + 1} has no string '$name'")

private fun JsonObject.optionalString(name: String): String? =
    (get(name) as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull

private fun JsonObject.optionalInt(name: String): Int? =
    (get(name) as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.intOrNull
