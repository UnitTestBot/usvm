package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.usvm.UExpr
import org.usvm.api.TsTestValue
import org.usvm.machine.state.TsState
import org.usvm.machine.state.lastStmt
import org.usvm.util.TsTestResolver
import org.usvm.util.TsUnsupportedEnvironmentWitnessException
import org.usvm.util.mkRegisterStackLValue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.IdentityHashMap
import kotlin.time.TimeSource

@Serializable
internal enum class CallsClassInvocationKind { CONSTRUCTOR, STATIC_METHOD, INSTANCE_METHOD }

/** Concrete original inputs, without a predicted output for suspended paths or a symbolic restart image. */
@Serializable
internal data class CallsClassInputCandidate(
    val stateId: String,
    val entryMethod: String,
    val currentMethod: String,
    val statementIndex: Int,
    val className: String,
    val methodName: String,
    val invocationKind: CallsClassInvocationKind,
    val outcome: String,
    val profile: CallsExperimentProfile,
    val origin: CallsCoverageCandidateOrigin,
    val completion: CallsCoverageCompletion?,
    val suspensionReason: String?,
    val suspendedAtMachineStop: Boolean,
    val emittedAtMillis: Long,
    val inputs: CallsConcreteHeap,
    val environmentComplete: Boolean,
    val environmentLimitations: List<String>,
    val nativeReplayValidated: Boolean = false,
)

@Serializable
internal data class CallsClassInputExtractionFailure(
    val stateId: String,
    val entryMethod: String,
    val outcome: String,
    val diagnostic: String,
    val environmentOnly: Boolean = false,
)

@Serializable
internal data class CallsClassInputJournalMetadata(
    val path: String,
    val records: Long,
    val bytes: Long,
    val sha256: String,
    val complete: Boolean,
    val schemaVersion: Int = 1,
    val format: String = "JSONL",
)

/** Reuses the normal before-test resolver; materialization failures remain explicit journal records. */
internal class CallsClassInputCandidates(output: Path, private val profile: CallsExperimentProfile) {
    private val candidates = InputJournal(path = output.resolve("input-candidates.jsonl"))
    private val failures = InputJournal(path = output.resolve("input-extraction-failures.jsonl"))
    private val bindings = IdentityHashMap<EtsMethod, Result<EntryBindings>>()
    private var ioFailed = false

    var extractionElapsedNanos: Long = 0
        private set

    var writeElapsedNanos: Long = 0
        private set

    fun registerInitialState(method: EtsMethod, state: TsState) {
        bindings[method] = runCatching {
            val receiver = state.memory.read(mkRegisterStackLValue(state.ctx.addressSort, idx = 0))
            val parameters = method.parameters.mapIndexed { index, _ ->
                val slot = index + 1
                val storageSort = checkNotNull(state.getSortForLocal(slot)) { "Entry input has no initialized sort" }
                state.memory.read(mkRegisterStackLValue(storageSort, idx = slot))
            }
            EntryBindings(receiver = receiver, parameters = parameters)
        }
    }

    fun append(state: TsState, outcome: String, emittedAtMillis: Long, stopped: Boolean) {
        val started = TimeSource.Monotonic.markNow()
        val extracted = runCatching {
            val method = state.entrypoint
            val binding = checkNotNull(bindings[method]) { "Missing original entry bindings" }
            val initial = binding.getOrThrow()
            val prototypes = IdentityHashMap<TsTestValue.TsClass, CallsConcretePrototype>()
            val resolver = TsTestResolver(onClassResolved = { value, owner ->
                val builtinObject = owner.name == "Object" && owner.signature.file == EtsFileSignature.UNKNOWN
                prototypes[value] = CallsConcretePrototype(
                    signature = owner.signature,
                    ordinaryObject = owner.category == EtsClassCategory.OBJECT || builtinObject,
                )
            })
            var environmentFailure: String? = null
            val inputs = try {
                resolver.resolveInputs(
                    method = method,
                    state = state,
                    inputValues = initial.parameters,
                    receiverValue = initial.receiver,
                )
            } catch (failure: TsUnsupportedEnvironmentWitnessException) {
                environmentFailure = "${failure::class.java.name}: ${failure.message}"
                resolver.resolveInputs(
                    method = method,
                    state = state,
                    inputValues = initial.parameters,
                    receiverValue = initial.receiver,
                    resolveEnvironment = false,
                )
            }
            val heap = concreteCallsInputHeap(inputs = inputs, prototypes = prototypes)
            val suspended = outcome != "RETURNED" && outcome != "THREW"
            val reason = if (suspended) state.engineFailureReason ?: state.unsupportedReason ?: outcome else null
            val currentMethod = state.lastStmt.location.method.signature
            val candidate = CallsClassInputCandidate(
                stateId = state.id.toString(),
                entryMethod = method.signature.toString(),
                currentMethod = currentMethod.toString(),
                statementIndex = state.lastStmt.location.index,
                className = method.signature.enclosingClass.name,
                methodName = method.name,
                invocationKind = when {
                    method.name == "constructor" -> CallsClassInvocationKind.CONSTRUCTOR
                    method.isStatic -> CallsClassInvocationKind.STATIC_METHOD
                    else -> CallsClassInvocationKind.INSTANCE_METHOD
                },
                outcome = outcome,
                profile = profile,
                origin = if (suspended) {
                    CallsCoverageCandidateOrigin.SUSPENDED
                } else {
                    CallsCoverageCandidateOrigin.TERMINATED
                },
                completion = when (outcome) {
                    "RETURNED" -> CallsCoverageCompletion.RETURNED
                    "THREW" -> CallsCoverageCompletion.THREW
                    else -> null
                },
                suspensionReason = reason,
                suspendedAtMachineStop = suspended && stopped,
                emittedAtMillis = emittedAtMillis,
                inputs = heap,
                environmentComplete = environmentFailure == null,
                environmentLimitations = listOfNotNull(environmentFailure),
            )
            ExtractedCandidate(candidate = candidate, environmentFailure = environmentFailure)
        }
        extractionElapsedNanos += started.elapsedNow().inWholeNanoseconds

        val writeStarted = TimeSource.Monotonic.markNow()
        try {
            extracted.fold(
                onSuccess = { result ->
                    candidates.append(encodeCallsArtifact(result.candidate))
                    val environmentFailure = result.environmentFailure
                    if (environmentFailure != null) {
                        val diagnostic = CallsClassInputExtractionFailure(
                            stateId = state.id.toString(),
                            entryMethod = state.entrypoint.signature.toString(),
                            outcome = outcome,
                            diagnostic = environmentFailure,
                            environmentOnly = true,
                        )
                        failures.append(encodeCallsArtifact(diagnostic))
                    }
                },
                onFailure = { failure ->
                    val diagnostic = CallsClassInputExtractionFailure(
                        stateId = state.id.toString(),
                        entryMethod = state.entrypoint.signature.toString(),
                        outcome = outcome,
                        diagnostic = "${failure::class.java.name}: ${failure.message}",
                    )
                    failures.append(encodeCallsArtifact(diagnostic))
                },
            )
        } catch (failure: IOException) {
            ioFailed = true
            throw failure
        } finally {
            writeElapsedNanos += writeStarted.elapsedNow().inWholeNanoseconds
        }
    }

    fun checkpoint() {
        candidates.flush()
        failures.flush()
    }

    fun finish(): Pair<CallsClassInputJournalMetadata, CallsClassInputJournalMetadata> {
        val saved = runCatching { candidates.finish(complete = !ioFailed) }
        val rejected = runCatching { failures.finish(complete = !ioFailed) }
        return saved.getOrThrow() to rejected.getOrThrow()
    }

    private data class EntryBindings(val receiver: UExpr<*>, val parameters: List<UExpr<*>>)

    private data class ExtractedCandidate(
        val candidate: CallsClassInputCandidate,
        val environmentFailure: String?,
    )
}

private class InputJournal(private val path: Path) {
    private val writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    private var records = 0L

    fun append(record: String) {
        writer.appendLine(record)
        records++
    }

    fun flush() = writer.flush()

    fun finish(complete: Boolean): CallsClassInputJournalMetadata {
        writer.close()
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val sha256 = digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and UNSIGNED_BYTE_MASK)
        }
        return CallsClassInputJournalMetadata(
            path = path.fileName.toString(),
            records = records,
            bytes = Files.size(path),
            sha256 = sha256,
            complete = complete,
        )
    }
}

private const val UNSIGNED_BYTE_MASK = 0xff
