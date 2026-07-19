package org.usvm.ts.pbt.capability

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

object CapabilityReportCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val compactJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(report: CapabilityScanReport): String {
        val validation = CapabilityReportValidator.validate(report)
        require(validation.valid) { validation.issues.joinToString { "${it.path}: ${it.code}" } }
        return json.encodeToString(report)
    }

    fun decode(text: String, sourceName: String = "<memory>"): CapabilityScanReport {
        val document = json.parseToJsonElement(text) as? JsonObject
            ?: error("capability report $sourceName must be a JSON object")
        val version = (document["schemaVersion"] as? JsonPrimitive)
            ?.takeUnless(JsonPrimitive::isString)
            ?.intOrNull
            ?: error("capability report $sourceName has no integer schemaVersion")
        require(version == CAPABILITY_SCHEMA_VERSION) {
            "unsupported capability report schemaVersion $version; expected $CAPABILITY_SCHEMA_VERSION"
        }
        return json.decodeFromJsonElement<CapabilityScanReport>(document).also { report ->
            val validation = CapabilityReportValidator.validate(report)
            require(validation.valid) {
                validation.issues.joinToString(prefix = "invalid capability report: ") {
                    "${it.path}: ${it.code}: ${it.message}"
                }
            }
        }
    }

    /** Records-only JSONL for diagnostics before final replay capability output. */
    fun encodeRecordsJsonl(report: CapabilityScanReport): String = buildString {
        report.records.forEach { appendLine(compactJson.encodeToString(it)) }
    }
}
