package org.usvm.ts.pbt.external

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Frozen artifact version shared by target discovery, adapters and replay.
 *
 * The version is repeated on every standalone JSON document and on every
 * source-target JSONL record. ETC JSONL instead carries it in its mandatory
 * header record.
 */
const val ARTIFACT_SCHEMA_VERSION: Int = 2

const val SOURCE_TARGETS_SCHEMA_VERSION: Int = ARTIFACT_SCHEMA_VERSION
const val RUN_CONFIG_SCHEMA_VERSION: Int = ARTIFACT_SCHEMA_VERSION
const val NATIVE_COVERAGE_SCHEMA_VERSION: Int = ARTIFACT_SCHEMA_VERSION
const val RUN_META_SCHEMA_VERSION: Int = ARTIFACT_SCHEMA_VERSION

@Serializable
data class ArtifactProducer(
    val name: String,
    val version: String,
    val commit: String? = null,
)

/** One record in source-targets.jsonl. There is deliberately no header record. */
@Serializable
data class SourceTargetRecord(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val schemaVersion: Int = SOURCE_TARGETS_SCHEMA_VERSION,
    val methodId: String,
    val branchId: String,
    val stmtIndex: Int,
    val successorStmtIndex: Int,
    val successorOrdinal: Int,
    val tsSourceRange: ArtifactSourceRange,
    val emittedJsRange: ArtifactSourceRange? = null,
    val sourceOrigin: SourceCallableOrigin,
    /** exact | oneToMany | ambiguous | unmapped | synthetic */
    val mappingStatus: String,
)

@Serializable
data class ArtifactSourceRange(
    val fileName: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
)

@Serializable
data class SourceCallableOrigin(
    val modulePath: String,
    val callableName: String,
    /** free | static | instance | constructor | arrow | synthetic */
    val callableKind: String,
)

/** Reproducible adapter invocation written before a run starts. */
@Serializable
data class ArtifactRunConfig(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val schemaVersion: Int = RUN_CONFIG_SCHEMA_VERSION,
    val runId: String,
    val adapter: ArtifactProducer,
    /** Unsigned 32-bit seed represented losslessly as a JSON integer. */
    val seed: Long,
    val budgetMs: Long,
    val exportReplayGraceMs: Long,
    val explorationDeadlineMs: Long,
    val hardResultDeadlineMs: Long,
    /** cold | warm */
    val cacheMode: String,
    val versions: Map<String, String>,
    val commits: Map<String, String>,
    /** Additive adapter-specific switches. Values stay typed JSON. */
    val flags: Map<String, JsonElement> = emptyMap(),
)

/** Native tool claims. These records are diagnostics, never final EtsIR coverage. */
@Serializable
data class NativeCoverageArtifact(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val schemaVersion: Int = NATIVE_COVERAGE_SCHEMA_VERSION,
    val producer: ArtifactProducer,
    val claims: List<NativeCoverageClaim>,
    val diagnostics: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class NativeCoverageClaim(
    val methodId: String,
    val nativeTargetId: String,
    val claimedCovered: Boolean,
    val discoveredAtMs: Long? = null,
)

/** Bounded metadata emitted next to a raw adapter corpus. */
@Serializable
data class RawRunMeta(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val schemaVersion: Int = RUN_META_SCHEMA_VERSION,
    val runId: String,
    val producer: ArtifactProducer,
    val startupMs: Long,
    val generationMs: Long,
    val exportMs: Long,
    val totalMs: Long,
    val commits: Map<String, String>,
    /** success | unsupported_configuration | tool_failure | timeout_partial_corpus */
    val exitStatus: String,
    val timedOut: Boolean,
    val logCapBytes: Long = 16L * 1024L * 1024L,
    val logTruncated: Boolean,
    val overBudgetMs: Long = 0,
)
