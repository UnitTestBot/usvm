package org.usvm.census

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal const val CENSUS_SCHEMA_VERSION = 1

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
    val maxFiles: Int = 100,
    val maxMethods: Int = 1_000,
)

@Serializable
internal data class UnknownCallCensusSummary(
    val schemaVersion: Int = CENSUS_SCHEMA_VERSION,
    val profiles: Map<String, UnknownCallCensusProfileSummary>,
)

@Serializable
internal data class UnknownCallCensusProfileSummary(
    val projects: Int,
    val functionsAnalyzed: Int,
    val functionsCompleted: Int,
    val functionsWithUnknownCalls: Int,
    val uniqueSites: Int,
    val rawEvents: Int,
    val eventsByFailureReason: Map<String, Int>,
    val eventsByDecision: Map<String, Int>,
    val eventsByCallee: Map<String, Int>,
    val uniqueSitesByCallee: Map<String, Int>,
    val timeouts: List<UnknownCallCensusIssue>,
    val errors: List<UnknownCallCensusIssue>,
)

@Serializable
internal data class UnknownCallCensusIssue(
    val projectId: String,
    val functionId: String? = null,
    val message: String? = null,
)

internal object UnknownCallCensusAggregator {
    fun summarize(lines: Sequence<String>): UnknownCallCensusSummary {
        val profiles = linkedMapOf<String, ProfileAccumulator>()

        lines.filter(String::isNotBlank).forEachIndexed { index, line ->
            val record = censusJson.parseToJsonElement(line) as? JsonObject
                ?: error("Raw census record ${index + 1} is not a JSON object")
            val kind = record.requiredString("kind", index)
            if (kind == "run_start" || kind == "run_result") {
                return@forEachIndexed
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

        val summaries = profiles.toSortedMap().mapValues { (_, accumulator) -> accumulator.toSummary() }
        return UnknownCallCensusSummary(profiles = summaries)
    }
}

private class ProfileAccumulator {
    private val projects = hashSetOf<String>()
    private val functions = hashSetOf<String>()
    private val completedFunctions = hashSetOf<String>()
    private val functionsWithUnknownCalls = hashSetOf<String>()
    private val sites = hashSetOf<String>()
    private var rawEvents = 0
    private val eventsByFailureReason = hashMapOf<String, Int>()
    private val eventsByDecision = hashMapOf<String, Int>()
    private val eventsByCallee = hashMapOf<String, Int>()
    private val sitesByCallee = hashMapOf<String, MutableSet<String>>()
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
        val status = record.requiredString("status", index)
        val issue = UnknownCallCensusIssue(
            projectId = projectId,
            functionId = functionId,
            message = record.optionalString("error"),
        )

        projects += projectId
        functions += functionId
        when (status) {
            "completed" -> completedFunctions += functionId
            "timeout" -> timeouts += issue
            "tool_error" -> errors += issue
        }
    }

    fun addProjectResult(record: JsonObject, index: Int) {
        val projectId = record.requiredString("projectId", index)
        val status = record.requiredString("status", index)
        val issue = UnknownCallCensusIssue(
            projectId = projectId,
            message = record.optionalString("error"),
        )

        projects += projectId
        when (status) {
            "timeout" -> timeouts += issue
            "tool_error" -> errors += issue
        }
    }

    fun toSummary(): UnknownCallCensusProfileSummary = UnknownCallCensusProfileSummary(
        projects = projects.size,
        functionsAnalyzed = functions.size,
        functionsCompleted = completedFunctions.size,
        functionsWithUnknownCalls = functionsWithUnknownCalls.size,
        uniqueSites = sites.size,
        rawEvents = rawEvents,
        eventsByFailureReason = eventsByFailureReason.toSortedMap(),
        eventsByDecision = eventsByDecision.toSortedMap(),
        eventsByCallee = eventsByCallee.toSortedMap(),
        uniqueSitesByCallee = sitesByCallee.toSortedMap().mapValues { (_, sites) -> sites.size },
        timeouts = timeouts.sortedWith(issueComparator),
        errors = errors.sortedWith(issueComparator),
    )
}

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

private fun JsonObject.optionalString(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull
