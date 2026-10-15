package org.usvm.ts.calls

import io.ksmt.expr.KExpr
import kotlinx.serialization.Serializable
import java.io.BufferedWriter
import java.io.FilterOutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.IdentityHashMap
import java.util.zip.GZIPOutputStream

@Serializable
internal data class CallsClassRawJournalMetadata(
    val path: String,
    val records: Long,
    val compressedBytes: Long,
    val sha256: String,
    val complete: Boolean,
    val schemaVersion: Int = 2,
    val format: String = "GZIP_JSONL_CONCATENATED_MEMBERS",
)

@Serializable
internal data class CallsClassRawSymbolicExpression(
    val id: Long,
    val expressionKind: String,
    val sort: String,
    val diagnostic: String,
)

/**
 * Each expression diagnostic is rendered once, and every state keeps the complete ordered list of references.
 * Neither the state snapshots nor their diagnostic strings accumulate in the final result or in memory.
 */
internal class CallsClassRawSnapshotStore(output: Path) {
    private val expressions = IdentityHashMap<KExpr<*>, Long>()
    private val expressionJournal = CallsClassRawGzipJournal(path = output.resolve("expressions.jsonl.gz"))
    private val stateJournal = CallsClassRawGzipJournal(path = output.resolve("states.jsonl.gz"))
    private var failed = false

    fun append(snapshot: ((KExpr<*>) -> Long) -> CallsClassRawStateSnapshot) {
        check(!failed) { "A previous raw snapshot write failed; journals are incomplete" }
        runCatching {
            var state: CallsClassRawStateSnapshot? = null
            expressionJournal.appendBatch { writer ->
                var added = 0L
                state = snapshot { expression ->
                    val previous = expressions[expression]
                    if (previous != null) {
                        previous
                    } else {
                        val id = expressions.size.toLong()
                        val record = CallsClassRawSymbolicExpression(
                            id = id,
                            expressionKind = expression::class.java.name,
                            sort = expression.sort.toString(),
                            diagnostic = expression.toString(),
                        )
                        writer.appendLine(encodeCallsArtifact(record))
                        expressions[expression] = id
                        added++
                        id
                    }
                }
                added
            }
            val saved = checkNotNull(state)
            stateJournal.appendBatch { writer ->
                writer.appendLine(encodeCallsArtifact(saved))
                1L
            }
        }.onFailure { failed = true }.getOrThrow()
    }

    fun finish(): Pair<CallsClassRawJournalMetadata, CallsClassRawJournalMetadata> {
        // Closing both journals is necessary even when the first close fails.
        val states = runCatching { stateJournal.finish(complete = !failed) }
        val terms = runCatching { expressionJournal.finish(complete = !failed) }
        return states.getOrThrow() to terms.getOrThrow()
    }
}

/** A completed, fsynced gzip member per state also preserves all earlier members after process termination. */
internal class CallsClassRawGzipJournal(private val path: Path) {
    private val output = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    private var records = 0L
    private var failed = false
    private var closed = false

    init {
        appendBatch { 0L }
    }

    fun appendBatch(write: (BufferedWriter) -> Long) {
        check(!closed && !failed) { "Raw journal is closed or incomplete" }
        runCatching {
            val sink = object : FilterOutputStream(Channels.newOutputStream(output)) {
                override fun close() = flush()
                override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
            }
            val gzip = GZIPOutputStream(sink)
            val added = gzip.bufferedWriter(Charsets.UTF_8).use { writer -> write(writer) }
            output.force(true)
            records += added
        }.onFailure { failed = true }.getOrThrow()
    }

    fun finish(complete: Boolean): CallsClassRawJournalMetadata {
        if (!closed) {
            output.close()
            closed = true
        }
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return CallsClassRawJournalMetadata(
            path = path.fileName.toString(),
            records = records,
            compressedBytes = Files.size(path),
            sha256 = digest.digest().joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and UNSIGNED_BYTE_MASK)
            },
            complete = complete && !failed,
        )
    }
}

private const val UNSIGNED_BYTE_MASK = 0xff
