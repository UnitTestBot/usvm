package org.usvm.machine.call

import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.usvm.machine.state.TsState
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val BYTE_MASK = 0xff

/** Opaque semantic-model implementation selected by its [kind]. */
interface TsUnknownCallModelImplementation {
    val kind: TsUnknownCallModelImplementationKind

    /** Stable implementation-specific inputs included in the frozen catalog fingerprint. */
    val fingerprintComponents: List<String>
        get() = emptyList()

    /** EtsIR files that must be visible to the normal interpreter when this implementation is enabled. */
    val additionalSceneFiles: List<EtsFile>
        get() = emptyList()
}

/** Executes opaque model implementations of one [kind]. */
interface TsUnknownCallModelBackend {
    val kind: TsUnknownCallModelImplementationKind

    fun execute(
        implementation: TsUnknownCallModelImplementation,
        precision: TsUnknownCallModelPrecision,
        state: TsState,
        call: TsUnknownCall,
    ): TsUnknownCallModelBackendResult
}

/** Result of attempting to execute one selected backend implementation. */
sealed interface TsUnknownCallModelBackendResult {
    data class Executed(
        val execution: TsUnknownCallModelExecution,
    ) : TsUnknownCallModelBackendResult

    data object NotApplicable : TsUnknownCallModelBackendResult
}

/** Binds backend-neutral model metadata to an opaque backend implementation. */
data class TsUnknownCallModelRegistration(
    val descriptor: TsUnknownCallModelDescriptor,
    val implementation: TsUnknownCallModelImplementation,
) {
    init {
        require(descriptor.implementationKind == implementation.kind) {
            "Semantic model ${descriptor.id} declares ${descriptor.implementationKind} " +
                "but provides ${implementation.kind}"
        }
    }
}

/** Validates semantic-model registrations and freezes deterministic per-run subsets. */
class TsUnknownCallModelRegistry(
    registrations: Collection<TsUnknownCallModelRegistration>,
    backends: Collection<TsUnknownCallModelBackend> = emptyList(),
) {
    private val registrations = registrations.sortedBy { it.descriptor.id }
    private val backends = backends.associateBackendKinds()

    init {
        val duplicateIds = this.registrations
            .groupingBy { it.descriptor.id }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()

        require(duplicateIds.isEmpty()) {
            "Duplicate semantic model IDs: ${duplicateIds.joinToString()}"
        }
    }

    /** Freezes an immutable enabled subset and validates its backends; `null` enables the complete catalog. */
    fun freeze(enabledModelIds: Set<String>? = null): TsFrozenUnknownCallModelRegistry {
        val enabledIds = enabledModelIds?.toSet()
        val knownIds = registrations.mapTo(mutableSetOf()) { it.descriptor.id }
        val unknownIds = enabledIds.orEmpty().subtract(knownIds).sorted()

        require(unknownIds.isEmpty()) {
            "Unknown semantic model IDs: ${unknownIds.joinToString()}"
        }

        val enabledRegistrations = when (enabledIds) {
            null -> registrations
            else -> registrations.filter { it.descriptor.id in enabledIds }
        }
        val missingBackendKinds = enabledRegistrations
            .map { it.descriptor.implementationKind }
            .distinct()
            .filterNot(backends::containsKey)
            .sortedBy { it.name }

        require(missingBackendKinds.isEmpty()) {
            "Missing semantic model backends: ${missingBackendKinds.joinToString()}"
        }

        return TsFrozenUnknownCallModelRegistry(enabledRegistrations, backends)
    }

    private fun Collection<TsUnknownCallModelBackend>.associateBackendKinds():
        Map<TsUnknownCallModelImplementationKind, TsUnknownCallModelBackend> {
        val duplicateKinds = groupingBy { it.kind }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sortedBy { it.name }

        require(duplicateKinds.isEmpty()) {
            "Duplicate semantic model backends: ${duplicateKinds.joinToString()}"
        }

        return associateBy { it.kind }
    }
}

/** Selects all registered models or a defensively copied explicit subset for one machine run. */
class TsUnknownCallModelSelection(
    enabledModelIds: Set<String>? = null,
) {
    val enabledModelIds: Set<String>? = enabledModelIds?.toSet()
}

/** An immutable deterministic semantic-model catalog used by one machine run. */
class TsFrozenUnknownCallModelRegistry internal constructor(
    private val registrations: List<TsUnknownCallModelRegistration>,
    private val backends: Map<TsUnknownCallModelImplementationKind, TsUnknownCallModelBackend>,
) : TsUnknownCallModelProvider {
    val descriptors: List<TsUnknownCallModelDescriptor> = registrations.map { it.descriptor }
    val fingerprint: String = computeFingerprint(registrations)
    override val additionalSceneFiles: List<EtsFile> = registrations
        .flatMap { registration -> registration.implementation.additionalSceneFiles }
        .deduplicateEtsFilesBySignature()

    internal fun select(call: TsUnknownCall): TsUnknownCallModelRegistration? {
        val matches = registrations.filter { it.descriptor.matcher.matches(call) }

        check(matches.size <= 1) {
            val modelIds = matches.map { it.descriptor.id }.sorted()
            "Ambiguous semantic models matched: ${modelIds.joinToString()}"
        }

        return matches.singleOrNull()
    }

    override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelApplication {
        val registration = select(call) ?: return TsUnknownCallModelApplication.NotApplicable
        if (state.isUnknownCallModelActive(registration.descriptor.id)) {
            return TsUnknownCallModelApplication.NotApplicable
        }

        val implementationKind = registration.descriptor.implementationKind
        val backend = checkNotNull(backends[implementationKind]) {
            "No semantic model backend configured for $implementationKind"
        }
        val backendResult = backend.execute(
            implementation = registration.implementation,
            precision = registration.descriptor.precision,
            state = state,
            call = call,
        )

        return when (backendResult) {
            is TsUnknownCallModelBackendResult.Executed -> TsUnknownCallModelApplication.Applied(
                modelId = registration.descriptor.id,
                precision = registration.descriptor.precision,
                execution = backendResult.execution,
            )

            TsUnknownCallModelBackendResult.NotApplicable -> TsUnknownCallModelApplication.NotApplicable
        }
    }
}

internal fun Iterable<EtsFile>.deduplicateEtsFilesBySignature(): List<EtsFile> {
    val filesBySignature = linkedMapOf<EtsFileSignature, EtsFile>()

    for (file in this) {
        val existingFile = filesBySignature[file.signature]
        require(existingFile == null || existingFile === file) {
            "Conflicting EtsIR files share signature ${file.signature}"
        }

        filesBySignature.putIfAbsent(file.signature, file)
    }

    return filesBySignature.values.toList()
}

private fun computeFingerprint(registrations: List<TsUnknownCallModelRegistration>): String {
    val digest = MessageDigest.getInstance("SHA-256")

    registrations.forEach { registration ->
        digest.updateLengthPrefixed(registration.descriptor.id)
        digest.updateLengthPrefixed(registration.descriptor.implementationKind.name)
        registration.implementation.fingerprintComponents.forEach(digest::updateLengthPrefixed)
    }

    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and BYTE_MASK)
    }
}

private fun MessageDigest.updateLengthPrefixed(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
    update(bytes)
}
