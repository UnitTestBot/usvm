package org.usvm.ts.calls

import io.ksmt.KContext
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CallsClassRawSnapshotStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `shared constraints preserve every state and symbolic value without repeated diagnostics`() {
        KContext().use { context ->
            val store = CallsClassRawSnapshotStore(output = directory)
            val terms = List(2_674) { index ->
                val name = "input-$index-${"nested-field-".repeat(n = 64)}"
                context.mkConst(name = name, sort = context.boolSort)
            }
            val diagnosticBytes = terms.sumOf { it.toString().toByteArray().size }

            repeat(times = 547) { state ->
                store.append { reference ->
                    snapshot(
                        stateId = state.toString(),
                        constraintIds = terms.map(reference),
                        symbolicResultId = reference(terms.first()),
                    )
                }
            }
            val (states, expressions) = store.finish()

            assertEquals(547L, states.records)
            assertEquals(2_674L, expressions.records)
            assertTrue(states.complete)
            assertTrue(expressions.complete)
            assertEquals(callsArtifactSha256(Files.readAllBytes(directory.resolve(states.path))), states.sha256)
            assertEquals(callsArtifactSha256(Files.readAllBytes(directory.resolve(expressions.path))), expressions.sha256)
            val restoredTerms = readJournal<CallsClassRawSymbolicExpression>(expressions.path)
            assertEquals(terms.map { it.toString() }, restoredTerms.map { it.diagnostic })
            val restoredStates = readJournal<CallsClassRawStateSnapshot>(states.path)
            assertEquals(547, restoredStates.size)
            restoredStates.forEachIndexed { index, state ->
                assertEquals(index.toString(), state.stateId)
                assertEquals((0L until 2_674L).toList(), state.constraintIds)
                assertEquals(0L, state.symbolicResultId)
            }
            assertTrue(547L * diagnosticBytes > 1_000_000_000L)
            assertTrue(expressions.compressedBytes < diagnosticBytes)
            assertTrue(states.compressedBytes + expressions.compressedBytes < 547L * diagnosticBytes)
        }
    }

    @Test
    fun `finished gzip members preserve earlier snapshots before final result exists`() {
        val journal = CallsClassRawGzipJournal(path = directory.resolve("states.jsonl.gz"))
        journal.appendBatch { writer ->
            writer.appendLine(encodeCallsArtifact(mapOf("value" to "isolated-\uD800")))
            1L
        }

        val rows = readJournal<Map<String, String>>("states.jsonl.gz")

        assertEquals(listOf(mapOf("value" to "isolated-\uD800")), rows)
        assertFalse(Files.exists(directory.resolve("result.json")))
        assertEquals(1L, journal.finish(complete = true).records)
    }

    @Test
    fun `partial snapshot failure marks both journals incomplete and rejects later references`() {
        KContext().use { context ->
            val store = CallsClassRawSnapshotStore(output = directory)
            val term = context.mkConst(name = "surviving-expression", sort = context.boolSort)
            store.append { reference -> snapshot(stateId = "first", constraintIds = listOf(reference(term))) }

            assertFailsWith<IllegalArgumentException> {
                store.append { reference ->
                    reference(term)
                    throw IllegalArgumentException("snapshot failed")
                }
            }
            assertFailsWith<IllegalStateException> {
                store.append { snapshot(stateId = "never-committed", constraintIds = emptyList()) }
            }
            val (states, expressions) = store.finish()

            assertFalse(states.complete)
            assertFalse(expressions.complete)
            assertEquals(listOf("first"), readJournal<CallsClassRawStateSnapshot>(states.path).map { it.stateId })
            assertEquals(1L, expressions.records)
        }
    }

    private inline fun <reified T> readJournal(path: String): List<T> =
        GZIPInputStream(Files.newInputStream(directory.resolve(path))).bufferedReader().use { reader ->
            reader.lineSequence().map { CallsExperimentJson.json.decodeFromString<T>(it) }.toList()
        }

    private fun snapshot(
        stateId: String,
        constraintIds: List<Long>,
        symbolicResultId: Long? = null,
    ): CallsClassRawStateSnapshot = CallsClassRawStateSnapshot(
        stateId = stateId,
        outcome = "SUSPENDED",
        entryMethod = "fixture.entry",
        currentMethod = "fixture.current",
        statementIndex = 0,
        statement = "fixture statement",
        callStack = listOf("fixture.entry", "fixture.current"),
        constraintIds = constraintIds,
        modelCount = 1,
        resultKind = "Success",
        symbolicResultId = symbolicResultId,
    )
}
