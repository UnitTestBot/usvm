package org.usvm.ts.pbt.external

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.usvm.ts.pbt.interpreter.VArray
import org.usvm.ts.pbt.interpreter.VBool
import org.usvm.ts.pbt.interpreter.VFunction
import org.usvm.ts.pbt.interpreter.VMap
import org.usvm.ts.pbt.interpreter.VNamespace
import org.usvm.ts.pbt.interpreter.VNull
import org.usvm.ts.pbt.interpreter.VNumber
import org.usvm.ts.pbt.interpreter.VObject
import org.usvm.ts.pbt.interpreter.VSet
import org.usvm.ts.pbt.interpreter.VString
import org.usvm.ts.pbt.interpreter.VUndefined
import org.usvm.ts.pbt.interpreter.VValue
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

const val EXTERNAL_TEST_CORPUS_SCHEMA_VERSION: Int = ARTIFACT_SCHEMA_VERSION
const val LEGACY_EXTERNAL_TEST_CORPUS_SCHEMA_VERSION: Int = 1

/**
 * Portable corpus shared by Node-side generators and the EtsIR replay engine.
 *
 * Values use an explicit tag instead of JSON's native value model so that
 * `undefined`, special numbers, and array holes survive transport unchanged.
 */
@Serializable
data class ExternalTestCorpus(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val schemaVersion: Int = EXTERNAL_TEST_CORPUS_SCHEMA_VERSION,
    val producer: String,
    val cases: List<ExternalTestCase>,
)

@Serializable
data class ExternalTestCase(
    val id: String,
    val methodId: String,
    /** Monotonic milliseconds from the common run start. */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val generatedAtMs: Long = 0,
    /** Producer seed and/or exploration path. At least one is required in raw v2 artifacts. */
    val seed: String? = null,
    val path: String? = null,
    val receiver: ExternalValue = ExternalValue(kind = "undefined"),
    val arguments: List<ExternalValue>,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Version-two tagged value. Only fields relevant to [kind] are populated.
 * Keeping the tag as data (rather than a closed polymorphic hierarchy) lets a
 * newer producer be rejected case-by-case instead of making the whole corpus
 * undecodable when it introduces a new value kind.
 */
@Serializable
data class ExternalValue(
    val kind: String,
    val value: String? = null,
    val elements: List<ExternalValue> = emptyList(),
    val properties: List<ExternalProperty> = emptyList(),
    val entries: List<ExternalMapEntry> = emptyList(),
    val className: String? = null,
    val reason: String? = null,
    /** function | cycle | symbol | accessor | classInstance | namespace | other */
    val unrepresentableKind: String? = null,
    /** Defines or references identity shared by structured values in one case. */
    val aliasId: String? = null,
    val aliasReference: String? = null,
    /** A materializable source-level callable, distinct from an arbitrary function value. */
    val callableReference: ExternalCallableReference? = null,
    /** Optional construction recipe for a structured receiver/object. */
    val constructorPlan: ExternalConstructorPlan? = null,
)

@Serializable
data class ExternalProperty(val key: String, val value: ExternalValue)

@Serializable
data class ExternalMapEntry(val key: ExternalValue, val value: ExternalValue)

@Serializable
data class ExternalCallableReference(
    val modulePath: String,
    val exportName: String,
    /** function | class | staticMethod | instanceMethod | arrow */
    val callableKind: String,
)

@Serializable
data class ExternalConstructorPlan(
    val callable: ExternalCallableReference,
    val arguments: List<ExternalValue> = emptyList(),
)

data class ExternalCorpusRejection(
    val id: String?,
    val methodId: String?,
    val reason: String,
)

data class ExternalCorpusReadResult(
    val schemaVersion: Int,
    val producer: String,
    val cases: List<ExternalTestCase>,
    val rejections: List<ExternalCorpusRejection>,
)

/** JSON and JSONL reader/writer with per-case decode failures. */
object ExternalTestCorpusCodec {
    private val compactJson = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    /** ETC v2 has one canonical wire format: a header plus JSONL case records. */
    fun encode(corpus: ExternalTestCorpus): String = encodeJsonLines(corpus)

    fun decode(text: String, sourceName: String = "<memory>"): ExternalCorpusReadResult =
        readText(text, sourceName)

    fun read(path: Path): ExternalCorpusReadResult = readText(path.readText(), path.name)

    fun write(path: Path, corpus: ExternalTestCorpus) {
        path.writeText(encode(corpus))
    }

    /**
     * JSONL consists of one header followed by one case per line. A malformed
     * case becomes a rejection without hiding the remaining valid cases.
     */
    fun encodeJsonLines(corpus: ExternalTestCorpus): String {
        requireWritableVersion(corpus.schemaVersion)
        val header = CorpusHeader(corpus.schemaVersion, corpus.producer)
        return buildString {
            appendLine(compactJson.encodeToString(header))
            corpus.cases.forEach { appendLine(compactJson.encodeToString(it)) }
        }
    }

    internal fun inputFingerprint(receiver: ExternalValue, arguments: List<ExternalValue>): String =
        compactJson.encodeToString(ExternalInputPayload(receiver, arguments))

    private fun readText(text: String, sourceName: String): ExternalCorpusReadResult {
        require(text.isNotBlank()) { "external corpus $sourceName is empty" }

        val wholeDocument = runCatching { compactJson.parseToJsonElement(text) }.getOrNull()
        return when (wholeDocument) {
            is JsonObject -> when {
                "cases" in wholeDocument -> decodeDocument(wholeDocument, sourceName)
                "methodId" in wholeDocument -> decodeElements(
                    schemaVersion = LEGACY_EXTERNAL_TEST_CORPUS_SCHEMA_VERSION,
                    producer = sourceName,
                    elements = listOf(wholeDocument),
                )
                "schemaVersion" in wholeDocument -> decodeJsonLines(text, sourceName)
                else -> error("external corpus $sourceName has a header but no cases")
            }

            is JsonArray -> decodeElements(
                schemaVersion = LEGACY_EXTERNAL_TEST_CORPUS_SCHEMA_VERSION,
                producer = sourceName,
                elements = wholeDocument,
            )

            null -> decodeJsonLines(text, sourceName)
            else -> error("external corpus $sourceName must be a JSON object, array, or JSONL stream")
        }
    }

    private fun decodeDocument(document: JsonObject, sourceName: String): ExternalCorpusReadResult {
        val version = document.strictInt("schemaVersion")
            ?: error("external corpus $sourceName has no integer schemaVersion")
        require(version == LEGACY_EXTERNAL_TEST_CORPUS_SCHEMA_VERSION) {
            "external corpus $sourceName uses JSON document encoding for schemaVersion $version; " +
                "schemaVersion $EXTERNAL_TEST_CORPUS_SCHEMA_VERSION requires JSONL"
        }
        val producer = document.string("producer")
            ?: error("external corpus $sourceName has no producer")
        val cases = document["cases"] as? JsonArray
            ?: error("external corpus $sourceName has a non-array cases field")
        return decodeElements(version, producer, cases)
    }

    private fun decodeJsonLines(text: String, sourceName: String): ExternalCorpusReadResult {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(lines.isNotEmpty()) { "external corpus $sourceName is empty" }
        val parseRejections = mutableListOf<ExternalCorpusRejection>()
        val elements = lines.mapIndexedNotNull { index, line ->
            runCatching { compactJson.parseToJsonElement(line) }.getOrElse { cause ->
                parseRejections += ExternalCorpusRejection(
                    id = null,
                    methodId = null,
                    reason = "$sourceName:${index + 1} is not valid JSON: ${cause.message}",
                )
                null
            }
        }
        val header = elements.firstOrNull() as? JsonObject
        val hasHeader = header != null && "schemaVersion" in header && "methodId" !in header
        val version = if (hasHeader) {
            header!!.strictInt("schemaVersion")
                ?: error("external corpus $sourceName has no integer schemaVersion")
        } else {
            LEGACY_EXTERNAL_TEST_CORPUS_SCHEMA_VERSION
        }
        requireReadableVersion(version)
        val producer = if (hasHeader) {
            header!!.string("producer")
                ?: if (version == LEGACY_EXTERNAL_TEST_CORPUS_SCHEMA_VERSION) sourceName
                else error("external corpus $sourceName has no producer")
        } else {
            sourceName
        }
        val decoded = decodeElements(version, producer, if (hasHeader) elements.drop(1) else elements)
        return decoded.copy(rejections = parseRejections + decoded.rejections)
    }

    private fun decodeElements(
        schemaVersion: Int,
        producer: String,
        elements: List<JsonElement>,
    ): ExternalCorpusReadResult {
        val cases = mutableListOf<ExternalTestCase>()
        val rejections = mutableListOf<ExternalCorpusRejection>()
        for (element in elements) {
            val obj = element as? JsonObject
            val id = obj?.string("id")
            val methodId = obj?.string("methodId")
            runCatching { compactJson.decodeFromJsonElement<ExternalTestCase>(element) }
                .onSuccess(cases::add)
                .onFailure { cause ->
                    rejections += ExternalCorpusRejection(
                        id = id,
                        methodId = methodId,
                        reason = "invalid case: ${cause.message?.lineSequence()?.firstOrNull() ?: cause::class.simpleName}",
                    )
                }
        }
        return ExternalCorpusReadResult(schemaVersion, producer, cases, rejections)
    }

    private fun requireReadableVersion(version: Int) {
        require(version == LEGACY_EXTERNAL_TEST_CORPUS_SCHEMA_VERSION || version == EXTERNAL_TEST_CORPUS_SCHEMA_VERSION) {
            "unsupported External Test Corpus schemaVersion $version; expected " +
                "$LEGACY_EXTERNAL_TEST_CORPUS_SCHEMA_VERSION or $EXTERNAL_TEST_CORPUS_SCHEMA_VERSION"
        }
    }

    private fun requireWritableVersion(version: Int) {
        require(version == EXTERNAL_TEST_CORPUS_SCHEMA_VERSION) {
            "cannot encode External Test Corpus schemaVersion $version; canonical output is " +
                "schemaVersion $EXTERNAL_TEST_CORPUS_SCHEMA_VERSION JSONL"
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.strictInt(key: String): Int? = (this[key] as? JsonPrimitive)
        ?.takeUnless(JsonPrimitive::isString)
        ?.intOrNull

    @Serializable
    private data class CorpusHeader(val schemaVersion: Int, val producer: String)

    @Serializable
    private data class ExternalInputPayload(
        val receiver: ExternalValue,
        val arguments: List<ExternalValue>,
    )
}

/** Explicit, deterministic migration from every legacy v1 encoding to canonical v2 JSONL. */
object ExternalTestCorpusV1Converter {
    fun convert(text: String, sourceName: String = "<memory>"): ExternalTestCorpus {
        val legacy = ExternalTestCorpusCodec.decode(text, sourceName)
        require(legacy.schemaVersion == LEGACY_EXTERNAL_TEST_CORPUS_SCHEMA_VERSION) {
            "external corpus $sourceName is schemaVersion ${legacy.schemaVersion}; expected legacy schemaVersion " +
                LEGACY_EXTERNAL_TEST_CORPUS_SCHEMA_VERSION
        }
        require(legacy.rejections.isEmpty()) {
            "external corpus $sourceName has ${legacy.rejections.size} malformed case(s): " +
                legacy.rejections.joinToString { it.reason }
        }
        val producerIsVersioned = hasVersionedProducerLabel(legacy.producer)
        return ExternalTestCorpus(
            producer = if (producerIsVersioned) legacy.producer else "legacy-v1@unknown",
            cases = legacy.cases.map { case ->
                val convertedAt = case.generatedAtMs.takeIf { it > 0 }
                    ?: case.metadata["generatedAtMs"]?.toLongOrNull()?.takeIf { it >= 0 }
                    ?: 0
                val convertedSeed = case.seed ?: case.metadata["seed"]
                val convertedPath = case.path ?: case.metadata["path"] ?: "legacy-v1:${case.id}"
                val conversionMetadata = buildMap {
                    putAll(case.metadata)
                    put("convertedFromSchemaVersion", "1")
                    if (!producerIsVersioned) put("legacyProducer", legacy.producer)
                }
                case.copy(
                    generatedAtMs = convertedAt,
                    seed = convertedSeed,
                    path = convertedPath,
                    receiver = upgradeValue(case.receiver),
                    arguments = case.arguments.map(::upgradeValue),
                    metadata = conversionMetadata,
                )
            },
        )
    }

    fun convertFile(input: Path, output: Path) {
        val converted = convert(input.readText(), input.name)
        output.writeText(ExternalTestCorpusCodec.encode(converted))
    }

    private fun upgradeValue(value: ExternalValue): ExternalValue = value.copy(
        elements = value.elements.map(::upgradeValue),
        properties = value.properties.map { it.copy(value = upgradeValue(it.value)) },
        entries = value.entries.map { it.copy(key = upgradeValue(it.key), value = upgradeValue(it.value)) },
        unrepresentableKind = value.unrepresentableKind ?: if (value.kind == "unrepresentable") {
            inferUnrepresentableKind(value.reason)
        } else {
            null
        },
    )

    private fun inferUnrepresentableKind(reason: String?): String = when {
        reason == null -> "other"
        "cycle" in reason || "shared alias" in reason -> "cycle"
        "function" in reason -> "function"
        "symbol" in reason -> "symbol"
        "accessor" in reason -> "accessor"
        "instance" in reason -> "classInstance"
        "namespace" in reason -> "namespace"
        else -> "other"
    }

    private fun hasVersionedProducerLabel(producer: String): Boolean {
        val separator = producer.lastIndexOf('@')
        return separator > 0 && separator < producer.lastIndex
    }
}

class ExternalValueConversionException(message: String) : IllegalArgumentException(message)

/** Loss-aware conversion between the portable tagged format and interpreter values. */
object ExternalValueCodec {
    fun toVValue(value: ExternalValue): VValue = decode(value, path = "$")

    fun fromVValue(value: VValue): ExternalValue = encode(value, IdentityHashMap(), path = "$")

    private fun decode(value: ExternalValue, path: String): VValue = when (value.kind) {
        "number" -> VNumber(decodeNumber(value.value, path))
        "boolean" -> when (value.value) {
            "true" -> VBool(true)
            "false" -> VBool(false)
            else -> reject(path, "boolean value must be 'true' or 'false'")
        }

        "string" -> VString(value.value ?: reject(path, "string has no value"))
        "null" -> VNull
        "undefined" -> VUndefined
        "array" -> VArray(value.elements.mapIndexed { index, element ->
            if (element.kind == "hole") reject("$path[$index]", "array holes are not representable by VArray yet")
            decode(element, "$path[$index]")
        }.toMutableList())

        "object" -> {
            if (value.constructorPlan != null) {
                reject(path, "constructor plan needs a scene-aware decoder")
            }
            if (value.className != null) {
                reject(path, "class-backed object '${value.className}' needs a scene-aware decoder")
            }
            val fields = linkedMapOf<String, VValue>()
            value.properties.forEach { property ->
                if (fields.containsKey(property.key)) reject(path, "duplicate object property '${property.key}'")
                fields[property.key] = decode(property.value, "$path.${property.key}")
            }
            VObject(cls = null, fields = fields)
        }

        "map" -> {
            val entries = LinkedHashMap<VValue, VValue>()
            value.entries.forEachIndexed { index, entry ->
                entries[decode(entry.key, "$path.map[$index].key")] =
                    decode(entry.value, "$path.map[$index].value")
            }
            VMap(entries)
        }

        "set" -> VSet(LinkedHashSet(value.elements.mapIndexed { index, element ->
            decode(element, "$path.set[$index]")
        }))

        "callable" -> reject(path, "callable reference needs a scene-aware decoder")
        "alias" -> reject(path, "alias reference needs an identity-aware decoder")
        "hole" -> reject(path, "array hole is only valid inside an array and cannot be replayed yet")
        "unrepresentable" -> reject(path, value.reason ?: "producer marked the value unrepresentable")
        else -> reject(path, "unknown value kind '${value.kind}'")
    }

    private fun decodeNumber(raw: String?, path: String): Double = when (raw) {
        "NaN" -> Double.NaN
        "Infinity" -> Double.POSITIVE_INFINITY
        "-Infinity" -> Double.NEGATIVE_INFINITY
        "-0" -> -0.0
        null -> reject(path, "number has no value")
        else -> raw.toDoubleOrNull() ?: reject(path, "invalid number '$raw'")
    }

    private fun encode(value: VValue, seen: IdentityHashMap<VValue, Unit>, path: String): ExternalValue = when (value) {
        is VNumber -> ExternalValue(kind = "number", value = encodeNumber(value.value))
        is VBool -> ExternalValue(kind = "boolean", value = value.value.toString())
        is VString -> ExternalValue(kind = "string", value = value.value)
        VNull -> ExternalValue(kind = "null")
        VUndefined -> ExternalValue(kind = "undefined")
        is VArray -> encodeReference(value, seen, path) {
            ExternalValue(
                kind = "array",
                elements = value.elements.mapIndexed { index, element -> encode(element, seen, "$path[$index]") },
            )
        }

        is VObject -> encodeReference(value, seen, path) {
            ExternalValue(
                kind = "object",
                className = value.cls?.name,
                properties = value.fields.map { (key, fieldValue) ->
                    ExternalProperty(key, encode(fieldValue, seen, "$path.$key"))
                },
            )
        }

        is VMap -> encodeReference(value, seen, path) {
            ExternalValue(
                kind = "map",
                entries = value.entries.entries.mapIndexed { index, (key, entryValue) ->
                    ExternalMapEntry(
                        encode(key, seen, "$path.map[$index].key"),
                        encode(entryValue, seen, "$path.map[$index].value"),
                    )
                },
            )
        }

        is VSet -> encodeReference(value, seen, path) {
            ExternalValue(
                kind = "set",
                elements = value.elements.mapIndexed { index, element -> encode(element, seen, "$path.set[$index]") },
            )
        }

        is VNamespace -> ExternalValue(
            kind = "unrepresentable",
            reason = "$path is namespace ${value.name}",
            unrepresentableKind = "namespace",
        )
        is VFunction -> ExternalValue(
            kind = "unrepresentable",
            reason = "$path is function ${value.method.name}",
            unrepresentableKind = "function",
        )
    }

    private inline fun encodeReference(
        value: VValue,
        seen: IdentityHashMap<VValue, Unit>,
        path: String,
        body: () -> ExternalValue,
    ): ExternalValue {
        if (seen.put(value, Unit) != null) {
            return ExternalValue(
                kind = "unrepresentable",
                reason = "$path contains a cycle or shared alias",
                unrepresentableKind = "cycle",
            )
        }
        return body()
    }

    private fun encodeNumber(value: Double): String = when {
        value.isNaN() -> "NaN"
        value == Double.POSITIVE_INFINITY -> "Infinity"
        value == Double.NEGATIVE_INFINITY -> "-Infinity"
        value.toRawBits() == (-0.0).toRawBits() -> "-0"
        else -> value.toString()
    }

    private fun reject(path: String, reason: String): Nothing =
        throw ExternalValueConversionException("$path: $reason")
}
