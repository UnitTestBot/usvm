package org.usvm.ts.pbt.external

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

@Serializable
data class ArtifactValidationIssue(
    val path: String,
    val code: String,
    val message: String,
)

@Serializable
data class ArtifactValidationReport(
    val artifact: String,
    val valid: Boolean,
    val issues: List<ArtifactValidationIssue>,
)

/** Canonical encoders used by producers so adapters do not need another artifact codec. */
object ArtifactContractCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
        ignoreUnknownKeys = true
    }
    private val compactJson = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    fun encodeSourceTargets(records: List<SourceTargetRecord>): String = buildString {
        records.forEach { record ->
            require(record.schemaVersion == SOURCE_TARGETS_SCHEMA_VERSION) {
                "source-target record schemaVersion ${record.schemaVersion}; expected $SOURCE_TARGETS_SCHEMA_VERSION"
            }
            appendLine(compactJson.encodeToString(record))
        }
    }

    fun decodeSourceTargets(text: String, sourceName: String = "<memory>"): List<SourceTargetRecord> {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(lines.isNotEmpty()) { "source-targets $sourceName has no edge records" }
        return lines.mapIndexed { index, line ->
            val document = compactJson.parseToJsonElement(line) as? JsonObject
                ?: error("source-targets $sourceName:${index + 1} must be a JSON object")
            val version = document.strictInt("schemaVersion")
                ?: error("source-targets $sourceName:${index + 1} has no integer schemaVersion")
            require(version == SOURCE_TARGETS_SCHEMA_VERSION) {
                "source-targets $sourceName:${index + 1} has schemaVersion $version; " +
                    "expected $SOURCE_TARGETS_SCHEMA_VERSION"
            }
            compactJson.decodeFromJsonElement(document)
        }
    }

    fun encodeRunConfig(config: ArtifactRunConfig): String = encodeVersioned(
        config.schemaVersion,
        RUN_CONFIG_SCHEMA_VERSION,
        "run config",
    ) { json.encodeToString(config) }

    fun decodeRunConfig(text: String, sourceName: String = "<memory>"): ArtifactRunConfig =
        decodeDocument(text, sourceName, "run config", RUN_CONFIG_SCHEMA_VERSION)

    fun encodeNativeCoverage(coverage: NativeCoverageArtifact): String = encodeVersioned(
        coverage.schemaVersion,
        NATIVE_COVERAGE_SCHEMA_VERSION,
        "native coverage",
    ) { json.encodeToString(coverage) }

    fun decodeNativeCoverage(text: String, sourceName: String = "<memory>"): NativeCoverageArtifact =
        decodeDocument(text, sourceName, "native coverage", NATIVE_COVERAGE_SCHEMA_VERSION)

    fun encodeRunMeta(meta: RawRunMeta): String = encodeVersioned(
        meta.schemaVersion,
        RUN_META_SCHEMA_VERSION,
        "run meta",
    ) { json.encodeToString(meta) }

    fun decodeRunMeta(text: String, sourceName: String = "<memory>"): RawRunMeta =
        decodeDocument(text, sourceName, "run meta", RUN_META_SCHEMA_VERSION)

    private inline fun encodeVersioned(
        actual: Int,
        expected: Int,
        artifact: String,
        encode: () -> String,
    ): String {
        require(actual == expected) { "$artifact schemaVersion $actual; expected $expected" }
        return encode()
    }

    private inline fun <reified T> decodeDocument(
        text: String,
        sourceName: String,
        artifact: String,
        expectedVersion: Int,
    ): T {
        val document = json.parseToJsonElement(text) as? JsonObject
            ?: error("$artifact $sourceName must be a JSON object")
        val version = document.strictInt("schemaVersion")
            ?: error("$artifact $sourceName has no integer schemaVersion")
        require(version == expectedVersion) {
            "unsupported $artifact schemaVersion $version; expected $expectedVersion"
        }
        return json.decodeFromJsonElement(document)
    }
}

/**
 * Shared structural and semantic validator for all adapter/replay boundary artifacts.
 *
 * Unknown JSON object members are intentionally ignored everywhere in schema v2.
 * Unknown versions, discriminator values, enum values, or malformed known fields
 * are rejected. See artifact-contract/v2/README.md for the frozen compatibility policy.
 */
object ArtifactValidator {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val mappingStatuses = setOf("exact", "oneToMany", "ambiguous", "unmapped", "synthetic")
    private val callableKinds = setOf("free", "static", "instance", "constructor", "arrow", "synthetic")
    private val referenceKinds = setOf("function", "class", "staticMethod", "instanceMethod", "arrow")
    private val unrepresentableKinds =
        setOf("function", "cycle", "symbol", "accessor", "classInstance", "namespace", "other")
    private val exitStatuses =
        setOf("success", "unsupported_configuration", "tool_failure", "timeout_partial_corpus")

    fun validateTargetManifest(path: Path): ArtifactValidationReport {
        val issues = Issues(path.name)
        val manifest = runCatching { TargetManifest.decode(path.readText(), path.toString()) }
            .getOrElse { cause ->
                issues.error("$", classifyDecodeError(cause), shortMessage(cause))
                null
            }
        if (manifest != null) validateTargetManifest(manifest, issues)
        return issues.report("target-manifest")
    }

    fun validateSourceTargets(path: Path): ArtifactValidationReport {
        val issues = Issues(path.name)
        decodeSourceTargets(path, issues)
        return issues.report("source-targets")
    }

    fun validateMethodIds(path: Path): ArtifactValidationReport {
        val issues = Issues(path.name)
        val text = readText(path, issues)
        if (text != null) {
            val lines = text.split('\n').map { it.removeSuffix("\r") }.let { raw ->
                if (raw.lastOrNull().isNullOrEmpty()) raw.dropLast(1) else raw
            }
            if (lines.isEmpty()) issues.error("$", "empty", "method-ids.txt has no method IDs")
            val seen = mutableSetOf<String>()
            lines.forEachIndexed { index, methodId ->
                val linePath = "$[${index + 1}]"
                if (methodId.isBlank()) issues.error(linePath, "blank", "method ID must not be blank")
                if (methodId != methodId.trim()) issues.error(linePath, "whitespace", "method ID has surrounding whitespace")
                if (!seen.add(methodId)) issues.error(linePath, "duplicate", "duplicate method ID")
            }
        }
        return issues.report("method-ids")
    }

    fun validateRunConfig(path: Path): ArtifactValidationReport {
        val issues = Issues(path.name)
        decodeDocument<ArtifactRunConfig>(path, "run config", RUN_CONFIG_SCHEMA_VERSION, issues)
            ?.let { validateRunConfig(it, issues) }
        return issues.report("run-config")
    }

    fun validateExternalTestCorpus(path: Path): ArtifactValidationReport {
        val issues = Issues(path.name)
        decodeExternalTestCorpus(path, issues)
        return issues.report("external-test-corpus")
    }

    fun validateNativeCoverage(path: Path): ArtifactValidationReport {
        val issues = Issues(path.name)
        decodeNativeCoverage(path, issues)
        return issues.report("native-coverage")
    }

    fun validateRunMeta(path: Path): ArtifactValidationReport {
        val issues = Issues(path.name)
        decodeRunMeta(path, issues)
        return issues.report("run-meta")
    }

    /** Validate the exact four-file raw adapter output contract and cross-file producer identity. */
    fun validateRawRunDirectory(path: Path): ArtifactValidationReport {
        val issues = Issues(path.name)
        if (!path.exists() || !path.isDirectory()) {
            issues.error("$", "not_directory", "$path is not a directory")
            return issues.report("raw-run-directory")
        }

        val expected = setOf("corpus.etc.jsonl", "native-coverage.json", "run-meta.json", "stderr.log")
        val entries = path.listDirectoryEntries().associateBy(Path::name)
        expected.sorted().forEach { name ->
            val entry = entries[name]
            when {
                entry == null -> issues.error("$/$name", "missing_artifact", "required artifact is absent")
                !entry.isRegularFile() -> issues.error("$/$name", "wrong_type", "required artifact is not a regular file")
            }
        }
        (entries.keys - expected).sorted().forEach { name ->
            issues.error("$/$name", "unexpected_artifact", "raw run contains an extra entry")
        }

        val corpusPath = path.resolve("corpus.etc.jsonl")
        val nativePath = path.resolve("native-coverage.json")
        val metaPath = path.resolve("run-meta.json")
        val stderrPath = path.resolve("stderr.log")
        val corpus = if (corpusPath.isRegularFile()) decodeExternalTestCorpus(corpusPath, issues, "$/corpus.etc.jsonl") else null
        val native = if (nativePath.isRegularFile()) decodeNativeCoverage(nativePath, issues, "$/native-coverage.json") else null
        val meta = if (metaPath.isRegularFile()) decodeRunMeta(metaPath, issues, "$/run-meta.json") else null

        if (native != null && meta != null && native.producer != meta.producer) {
            issues.error("$/native-coverage.json/producer", "producer_mismatch", "producer differs from run-meta.json")
        }
        if (corpus != null && meta != null) {
            val expectedProducer = "${meta.producer.name}@${meta.producer.version}"
            if (corpus.producer != expectedProducer) {
                issues.error(
                    "$/corpus.etc.jsonl/producer",
                    "producer_mismatch",
                    "producer '${corpus.producer}' differs from run-meta producer '$expectedProducer'",
                )
            }
            corpus.cases.forEachIndexed { index, case ->
                if (case.generatedAtMs > meta.totalMs) {
                    issues.error(
                        "$/corpus.etc.jsonl/cases[$index]/generatedAtMs",
                        "time_out_of_range",
                        "generatedAtMs ${case.generatedAtMs} exceeds run totalMs ${meta.totalMs}",
                    )
                }
            }
        }
        if (stderrPath.isRegularFile() && meta != null && stderrPath.fileSize() > meta.logCapBytes) {
            issues.error(
                "$/stderr.log",
                "log_cap_exceeded",
                "stderr.log is ${stderrPath.fileSize()} bytes; cap is ${meta.logCapBytes}",
            )
        }
        return issues.report("raw-run-directory")
    }

    private fun validateTargetManifest(manifest: TargetManifest, issues: Issues) {
        if (manifest.generator.isBlank()) issues.error("$.generator", "blank", "generator must not be blank")
        val methodIds = mutableSetOf<String>()
        val branchIds = mutableSetOf<String>()
        manifest.methods.forEachIndexed { methodIndex, method ->
            val path = "$.methods[$methodIndex]"
            requireNonBlank(method.methodId, "$path.methodId", issues)
            if (!methodIds.add(method.methodId)) issues.error("$path.methodId", "duplicate", "duplicate methodId")
            requireNonBlank(method.signature, "$path.signature", issues)
            requireNonBlank(method.projectName, "$path.projectName", issues)
            requireNonBlank(method.fileName, "$path.fileName", issues)
            requireNonBlank(method.className, "$path.className", issues)
            requireNonBlank(method.methodName, "$path.methodName", issues)
            if (method.arity < 0) issues.error("$path.arity", "out_of_range", "arity must be non-negative")
            if (method.arity != method.parameters.size || method.arity != method.parameterTypes.size) {
                issues.error(path, "arity_mismatch", "arity, parameters, and parameterTypes sizes must match")
            }
            if (method.entryKind !in setOf("free", "static", "instance")) {
                issues.error("$path.entryKind", "unknown_enum", "unknown entryKind '${method.entryKind}'")
            }
            method.parameters.forEachIndexed { parameterIndex, parameter ->
                val parameterPath = "$path.parameters[$parameterIndex]"
                if (parameter.index != parameterIndex) {
                    issues.error("$parameterPath.index", "index_mismatch", "expected parameter index $parameterIndex")
                }
                requireNonBlank(parameter.name, "$parameterPath.name", issues)
                requireNonBlank(parameter.type, "$parameterPath.type", issues)
                if (parameter.type != method.parameterTypes.getOrNull(parameterIndex)) {
                    issues.error("$parameterPath.type", "type_mismatch", "type differs from parameterTypes")
                }
                if (parameter.rest && parameterIndex != method.parameters.lastIndex) {
                    issues.error("$parameterPath.rest", "invalid_rest", "rest parameter must be last")
                }
            }
            method.branches.forEachIndexed { branchIndex, branch ->
                val branchPath = "$path.branches[$branchIndex]"
                requireNonBlank(branch.branchId, "$branchPath.branchId", issues)
                if (!branchIds.add(branch.branchId)) issues.error("$branchPath.branchId", "duplicate", "duplicate branchId")
                if (!branch.branchId.startsWith("${method.methodId}#")) {
                    issues.error("$branchPath.branchId", "method_mismatch", "branchId is not scoped by methodId")
                }
                if (branch.ifStmtIndex < 0) issues.error("$branchPath.ifStmtIndex", "out_of_range", "must be non-negative")
                if (branch.successorStmtIndex < 0) {
                    issues.error("$branchPath.successorStmtIndex", "out_of_range", "must be non-negative")
                }
                if (branch.successorOrdinal !in 0..1) {
                    issues.error("$branchPath.successorOrdinal", "out_of_range", "if successorOrdinal must be 0 or 1")
                }
                branch.conditionOrigin?.let { validateManifestOrigin(it, "$branchPath.conditionOrigin", issues) }
                branch.successorOrigin?.let { validateManifestOrigin(it, "$branchPath.successorOrigin", issues) }
            }
        }
    }

    private fun decodeSourceTargets(path: Path, issues: Issues): List<SourceTargetRecord> {
        val records = mutableListOf<SourceTargetRecord>()
        val text = readText(path, issues) ?: return records
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.isEmpty()) {
            issues.error("$", "empty", "source-targets JSONL has no edge records")
            return records
        }
        val identities = mutableSetOf<Pair<String, String>>()
        lines.forEachIndexed { index, line ->
            val linePath = "$[${index + 1}]"
            val element = runCatching { json.parseToJsonElement(line) }.getOrElse { cause ->
                issues.error(linePath, "invalid_json", shortMessage(cause))
                null
            } ?: return@forEachIndexed
            val obj = element as? JsonObject
            if (obj == null) {
                issues.error(linePath, "wrong_shape", "edge record must be a JSON object")
                return@forEachIndexed
            }
            if (!validateSchemaVersion(obj, SOURCE_TARGETS_SCHEMA_VERSION, linePath, "source-target", issues)) {
                return@forEachIndexed
            }
            val record = runCatching { json.decodeFromJsonElement<SourceTargetRecord>(obj) }.getOrElse { cause ->
                issues.error(linePath, "decode_error", shortMessage(cause))
                null
            } ?: return@forEachIndexed
            validateSourceTarget(record, linePath, issues)
            if (!identities.add(record.methodId to record.branchId)) {
                issues.error(linePath, "duplicate", "duplicate (methodId, branchId) edge record")
            }
            records += record
        }
        return records
    }

    private fun validateSourceTarget(record: SourceTargetRecord, path: String, issues: Issues) {
        requireNonBlank(record.methodId, "$path.methodId", issues)
        requireNonBlank(record.branchId, "$path.branchId", issues)
        if (record.stmtIndex < 0) issues.error("$path.stmtIndex", "out_of_range", "must be non-negative")
        if (record.successorStmtIndex < 0) {
            issues.error("$path.successorStmtIndex", "out_of_range", "must be non-negative")
        }
        if (record.successorOrdinal < 0) {
            issues.error("$path.successorOrdinal", "out_of_range", "must be non-negative")
        }
        validateRange(record.tsSourceRange, "$path.tsSourceRange", issues)
        record.emittedJsRange?.let { validateRange(it, "$path.emittedJsRange", issues) }
        requireNonBlank(record.sourceOrigin.modulePath, "$path.sourceOrigin.modulePath", issues)
        requireNonBlank(record.sourceOrigin.callableName, "$path.sourceOrigin.callableName", issues)
        if (record.sourceOrigin.callableKind !in callableKinds) {
            issues.error(
                "$path.sourceOrigin.callableKind",
                "unknown_enum",
                "unknown callableKind '${record.sourceOrigin.callableKind}'",
            )
        }
        if (record.mappingStatus !in mappingStatuses) {
            issues.error("$path.mappingStatus", "unknown_enum", "unknown mappingStatus '${record.mappingStatus}'")
        }
    }

    private fun validateRunConfig(config: ArtifactRunConfig, issues: Issues) {
        requireNonBlank(config.runId, "$.runId", issues)
        validateProducer(config.adapter, "$.adapter", issues)
        if (config.seed !in 0..0xffff_ffffL) issues.error("$.seed", "out_of_range", "seed must be unsigned 32-bit")
        if (config.budgetMs <= 0) issues.error("$.budgetMs", "out_of_range", "budgetMs must be positive")
        val expectedGrace = minOf(5_000L, maxOf(1_000L, config.budgetMs / 10L))
        if (config.exportReplayGraceMs != expectedGrace) {
            issues.error("$.exportReplayGraceMs", "deadline_mismatch", "expected deadline grace $expectedGrace")
        }
        if (config.budgetMs <= config.exportReplayGraceMs) {
            issues.error("$.budgetMs", "deadline_mismatch", "budgetMs must exceed exportReplayGraceMs")
        }
        if (config.explorationDeadlineMs != config.budgetMs - config.exportReplayGraceMs) {
            issues.error("$.explorationDeadlineMs", "deadline_mismatch", "must equal budgetMs - exportReplayGraceMs")
        }
        if (config.hardResultDeadlineMs != config.budgetMs) {
            issues.error("$.hardResultDeadlineMs", "deadline_mismatch", "must equal budgetMs")
        }
        if (config.cacheMode !in setOf("cold", "warm")) {
            issues.error("$.cacheMode", "unknown_enum", "cacheMode must be cold or warm")
        }
        validateStringMap(config.versions, "$.versions", "versions", issues)
        validateStringMap(config.commits, "$.commits", "commits", issues)
    }

    private fun decodeExternalTestCorpus(
        path: Path,
        issues: Issues,
        root: String = "$",
    ): ExternalCorpusReadResult? {
        val decoded = runCatching { ExternalTestCorpusCodec.read(path) }.getOrElse { cause ->
            issues.error(root, classifyDecodeError(cause), shortMessage(cause))
            null
        } ?: return null
        if (decoded.schemaVersion != EXTERNAL_TEST_CORPUS_SCHEMA_VERSION) {
            issues.error(
                "$root.schemaVersion",
                "schema_version",
                "raw ETC must use schemaVersion $EXTERNAL_TEST_CORPUS_SCHEMA_VERSION, got ${decoded.schemaVersion}",
            )
        }
        decoded.rejections.forEach { rejection ->
            issues.error(root, "invalid_case", rejection.reason)
        }
        if (!isVersionedProducer(decoded.producer)) {
            issues.error("$root.producer", "invalid_producer", "producer must have canonical name@version form")
        }
        val caseIds = mutableSetOf<String>()
        decoded.cases.forEachIndexed { index, case ->
            val casePath = "$root.cases[$index]"
            requireNonBlank(case.id, "$casePath.id", issues)
            if (!caseIds.add(case.id)) issues.error("$casePath.id", "duplicate", "duplicate case id")
            requireNonBlank(case.methodId, "$casePath.methodId", issues)
            if (case.generatedAtMs < 0) {
                issues.error("$casePath.generatedAtMs", "out_of_range", "generatedAtMs must be non-negative")
            }
            if (case.seed.isNullOrBlank() && case.path.isNullOrBlank()) {
                issues.error(casePath, "missing_generation_origin", "at least one of seed or path is required")
            }
            val aliases = AliasState()
            validateExternalValue(case.receiver, "$casePath.receiver", false, aliases, issues, 0)
            case.arguments.forEachIndexed { argumentIndex, argument ->
                validateExternalValue(argument, "$casePath.arguments[$argumentIndex]", false, aliases, issues, 0)
            }
            (aliases.references - aliases.definitions).sorted().forEach { missing ->
                issues.error(casePath, "unknown_alias", "alias '$missing' is referenced but not defined in the case")
            }
        }
        return decoded
    }

    private fun validateExternalValue(
        value: ExternalValue,
        path: String,
        holeAllowed: Boolean,
        aliases: AliasState,
        issues: Issues,
        depth: Int,
    ) {
        if (depth > 256) {
            issues.error(path, "nesting_limit", "value nesting exceeds 256")
            return
        }
        value.aliasId?.let { alias ->
            if (alias.isBlank()) issues.error("$path.aliasId", "blank", "aliasId must not be blank")
            else if (!aliases.definitions.add(alias)) issues.error("$path.aliasId", "duplicate", "duplicate aliasId '$alias'")
        }
        value.constructorPlan?.let { plan ->
            if (value.kind != "object") {
                issues.error("$path.constructorPlan", "invalid_field", "constructorPlan is only valid for object values")
            }
            validateCallableReference(plan.callable, "$path.constructorPlan.callable", issues)
            plan.arguments.forEachIndexed { index, argument ->
                validateExternalValue(argument, "$path.constructorPlan.arguments[$index]", false, aliases, issues, depth + 1)
            }
        }
        when (value.kind) {
            "undefined", "null" -> requireNoScalarValue(value, path, issues)
            "number" -> {
                val raw = value.value
                if (raw == null || raw !in setOf("NaN", "Infinity", "-Infinity", "-0") && raw.toDoubleOrNull() == null) {
                    issues.error("$path.value", "invalid_number", "number must use a decimal or special-number token")
                }
            }
            "boolean" -> if (value.value !in setOf("true", "false")) {
                issues.error("$path.value", "invalid_boolean", "boolean value must be 'true' or 'false'")
            }
            "string" -> if (value.value == null) issues.error("$path.value", "missing", "string value is required")
            "hole" -> {
                if (!holeAllowed) issues.error(path, "invalid_hole", "hole is only valid as a direct array element")
                requireNoScalarValue(value, path, issues)
            }
            "array" -> value.elements.forEachIndexed { index, element ->
                validateExternalValue(element, "$path.elements[$index]", true, aliases, issues, depth + 1)
            }
            "object" -> {
                val keys = mutableSetOf<String>()
                value.properties.forEachIndexed { index, property ->
                    if (!keys.add(property.key)) {
                        issues.error("$path.properties[$index].key", "duplicate", "duplicate object property '${property.key}'")
                    }
                    validateExternalValue(
                        property.value,
                        "$path.properties[$index].value",
                        false,
                        aliases,
                        issues,
                        depth + 1,
                    )
                }
            }
            "map" -> value.entries.forEachIndexed { index, entry ->
                validateExternalValue(entry.key, "$path.entries[$index].key", false, aliases, issues, depth + 1)
                validateExternalValue(entry.value, "$path.entries[$index].value", false, aliases, issues, depth + 1)
            }
            "set" -> value.elements.forEachIndexed { index, element ->
                validateExternalValue(element, "$path.elements[$index]", false, aliases, issues, depth + 1)
            }
            "callable" -> {
                val reference = value.callableReference
                if (reference == null) issues.error("$path.callableReference", "missing", "callable reference is required")
                else validateCallableReference(reference, "$path.callableReference", issues)
            }
            "alias" -> {
                val reference = value.aliasReference
                if (reference.isNullOrBlank()) issues.error("$path.aliasReference", "missing", "alias reference is required")
                else aliases.references += reference
                if (value.aliasId != null) issues.error("$path.aliasId", "invalid_field", "alias references cannot define aliasId")
            }
            "unrepresentable" -> {
                if (value.reason.isNullOrBlank()) issues.error("$path.reason", "missing", "rejection reason is required")
                if (value.unrepresentableKind !in unrepresentableKinds) {
                    issues.error(
                        "$path.unrepresentableKind",
                        "unknown_enum",
                        "unrepresentableKind must explicitly classify the value",
                    )
                }
            }
            else -> issues.error("$path.kind", "unknown_discriminator", "unknown ETC value kind '${value.kind}'")
        }
    }

    private fun decodeNativeCoverage(
        path: Path,
        issues: Issues,
        root: String = "$",
    ): NativeCoverageArtifact? {
        val artifact = decodeDocument<NativeCoverageArtifact>(
            path,
            "native coverage",
            NATIVE_COVERAGE_SCHEMA_VERSION,
            issues,
            root,
        ) ?: return null
        validateProducer(artifact.producer, "$root.producer", issues)
        val identities = mutableSetOf<Pair<String, String>>()
        artifact.claims.forEachIndexed { index, claim ->
            val claimPath = "$root.claims[$index]"
            requireNonBlank(claim.methodId, "$claimPath.methodId", issues)
            requireNonBlank(claim.nativeTargetId, "$claimPath.nativeTargetId", issues)
            if (!identities.add(claim.methodId to claim.nativeTargetId)) {
                issues.error(claimPath, "duplicate", "duplicate native coverage claim")
            }
            if (claim.discoveredAtMs != null && claim.discoveredAtMs < 0) {
                issues.error("$claimPath.discoveredAtMs", "out_of_range", "must be non-negative")
            }
        }
        return artifact
    }

    private fun decodeRunMeta(path: Path, issues: Issues, root: String = "$"): RawRunMeta? {
        val meta = decodeDocument<RawRunMeta>(path, "run meta", RUN_META_SCHEMA_VERSION, issues, root) ?: return null
        requireNonBlank(meta.runId, "$root.runId", issues)
        validateProducer(meta.producer, "$root.producer", issues)
        listOf(
            "startupMs" to meta.startupMs,
            "generationMs" to meta.generationMs,
            "exportMs" to meta.exportMs,
            "totalMs" to meta.totalMs,
            "overBudgetMs" to meta.overBudgetMs,
        ).forEach { (name, value) ->
            if (value < 0) issues.error("$root.$name", "out_of_range", "$name must be non-negative")
        }
        if (meta.startupMs + meta.generationMs + meta.exportMs > meta.totalMs) {
            issues.error(root, "time_mismatch", "startupMs + generationMs + exportMs exceeds totalMs")
        }
        if (meta.exitStatus !in exitStatuses) {
            issues.error("$root.exitStatus", "unknown_enum", "unknown exitStatus '${meta.exitStatus}'")
        }
        if (meta.timedOut != (meta.exitStatus == "timeout_partial_corpus")) {
            issues.error("$root.timedOut", "exit_mismatch", "timedOut must match timeout_partial_corpus exitStatus")
        }
        if (meta.logCapBytes <= 0) issues.error("$root.logCapBytes", "out_of_range", "logCapBytes must be positive")
        validateStringMap(meta.commits, "$root.commits", "commits", issues)
        return meta
    }

    private inline fun <reified T> decodeDocument(
        path: Path,
        artifact: String,
        expectedVersion: Int,
        issues: Issues,
        root: String = "$",
    ): T? {
        val text = readText(path, issues, root) ?: return null
        val element = runCatching { json.parseToJsonElement(text) }.getOrElse { cause ->
            issues.error(root, "invalid_json", shortMessage(cause))
            null
        } ?: return null
        val obj = element as? JsonObject
        if (obj == null) {
            issues.error(root, "wrong_shape", "$artifact must be a JSON object")
            return null
        }
        if (!validateSchemaVersion(obj, expectedVersion, root, artifact, issues)) return null
        return runCatching { json.decodeFromJsonElement<T>(obj) }.getOrElse { cause ->
            issues.error(root, "decode_error", shortMessage(cause))
            null
        }
    }

    private fun validateSchemaVersion(
        obj: JsonObject,
        expected: Int,
        path: String,
        artifact: String,
        issues: Issues,
    ): Boolean {
        val version = obj.strictInt("schemaVersion")
        if (version == null) {
            issues.error("$path.schemaVersion", "schema_version", "$artifact has no integer schemaVersion")
            return false
        }
        if (version != expected) {
            issues.error("$path.schemaVersion", "schema_version", "unsupported $artifact schemaVersion $version; expected $expected")
            return false
        }
        return true
    }

    private fun validateRange(range: ArtifactSourceRange, path: String, issues: Issues) {
        requireNonBlank(range.fileName, "$path.fileName", issues)
        if (range.startOffset < 0 || range.endOffset < range.startOffset) {
            issues.error(path, "invalid_range", "offset range must be non-negative and ordered")
        }
        if (range.startLine < 0 || range.startColumn < 0 || range.endLine < 0 || range.endColumn < 0) {
            issues.error(path, "invalid_range", "line and column values must be non-negative")
        }
        if (range.endLine < range.startLine || range.endLine == range.startLine && range.endColumn < range.startColumn) {
            issues.error(path, "invalid_range", "source positions must be ordered")
        }
    }

    private fun validateManifestOrigin(origin: ManifestSourceOrigin, path: String, issues: Issues) {
        validateRange(
            ArtifactSourceRange(
                origin.fileName,
                origin.startOffset,
                origin.endOffset,
                origin.startLine,
                origin.startColumn,
                origin.endLine,
                origin.endColumn,
            ),
            path,
            issues,
        )
        requireNonBlank(origin.nodeKind, "$path.nodeKind", issues)
    }

    private fun validateCallableReference(reference: ExternalCallableReference, path: String, issues: Issues) {
        requireNonBlank(reference.modulePath, "$path.modulePath", issues)
        requireNonBlank(reference.exportName, "$path.exportName", issues)
        if (reference.callableKind !in referenceKinds) {
            issues.error("$path.callableKind", "unknown_enum", "unknown callable reference kind '${reference.callableKind}'")
        }
    }

    private fun requireNoScalarValue(value: ExternalValue, path: String, issues: Issues) {
        if (value.value != null) issues.error("$path.value", "invalid_field", "${value.kind} must not have value")
    }

    private fun validateProducer(producer: ArtifactProducer, path: String, issues: Issues) {
        requireNonBlank(producer.name, "$path.name", issues)
        requireNonBlank(producer.version, "$path.version", issues)
        if (producer.commit != null && producer.commit.isBlank()) {
            issues.error("$path.commit", "blank", "commit must be omitted or non-blank")
        }
    }

    private fun validateStringMap(values: Map<String, String>, path: String, label: String, issues: Issues) {
        if (values.isEmpty()) issues.error(path, "empty", "$label must not be empty")
        values.forEach { (key, value) ->
            if (key.isBlank() || value.isBlank()) issues.error(path, "blank", "$label keys and values must not be blank")
        }
    }

    private fun requireNonBlank(value: String, path: String, issues: Issues) {
        if (value.isBlank()) issues.error(path, "blank", "value must not be blank")
    }

    private fun isVersionedProducer(producer: String): Boolean {
        val separator = producer.lastIndexOf('@')
        return separator > 0 && separator < producer.lastIndex
    }

    private fun readText(path: Path, issues: Issues, root: String = "$"): String? = runCatching { path.readText() }
        .getOrElse { cause ->
            issues.error(root, "io_error", shortMessage(cause))
            null
        }

    private fun classifyDecodeError(cause: Throwable): String =
        if (shortMessage(cause).contains("schemaVersion")) "schema_version" else "decode_error"

    private fun shortMessage(cause: Throwable): String =
        cause.message?.lineSequence()?.firstOrNull() ?: cause::class.simpleName ?: "unknown error"

    private data class AliasState(
        val definitions: MutableSet<String> = mutableSetOf(),
        val references: MutableSet<String> = mutableSetOf(),
    )

    private class Issues(private val source: String) {
        private val values = mutableListOf<ArtifactValidationIssue>()

        fun error(path: String, code: String, message: String) {
            values += ArtifactValidationIssue(path = path, code = code, message = message)
        }

        fun report(artifact: String): ArtifactValidationReport = ArtifactValidationReport(
            artifact = "$artifact:$source",
            valid = values.isEmpty(),
            issues = values.toList(),
        )
    }
}

private fun JsonObject.strictInt(key: String): Int? = (this[key] as? JsonPrimitive)
    ?.takeUnless(JsonPrimitive::isString)
    ?.intOrNull
