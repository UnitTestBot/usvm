package org.usvm.machine.call

import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.usvm.machine.state.TsState
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val BYTE_MASK = 0xff

/** An immutable deterministic set of semantic models used by one machine run. */
class TsUnknownCallModelCatalog(
    models: Collection<TsUnknownCallModel>,
    enabledModelIds: Set<String>? = null,
) {
    private val models: List<TsUnknownCallModel>

    val modelIds: List<String>
        get() = models.map(TsUnknownCallModel::id)

    val fingerprint: String
    val additionalSceneFiles: List<EtsFile>

    init {
        val allModels = models.sortedBy(TsUnknownCallModel::id)
        val duplicateIds = allModels
            .groupingBy(TsUnknownCallModel::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()

        require(allModels.none { model -> model.id.isBlank() }) { "Semantic model ID must not be blank" }
        require(duplicateIds.isEmpty()) { "Duplicate semantic model IDs: ${duplicateIds.joinToString()}" }

        val selectedIds = enabledModelIds?.toSet()
        val knownIds = allModels.mapTo(mutableSetOf(), TsUnknownCallModel::id)
        val unknownIds = selectedIds.orEmpty().subtract(knownIds).sorted()

        require(unknownIds.isEmpty()) { "Unknown semantic model IDs: ${unknownIds.joinToString()}" }

        this.models = when (selectedIds) {
            null -> allModels
            else -> allModels.filter { model -> model.id in selectedIds }
        }

        validateUnambiguousTargets(this.models)
        fingerprint = computeFingerprint(this.models)
        additionalSceneFiles = this.models
            .flatMap(TsUnknownCallModel::additionalSceneFiles)
            .deduplicateEtsFilesBySignature()
    }

    internal fun select(call: TsUnknownCall): TsUnknownCallModel? =
        models.singleOrNull { model -> model.target.matches(call) }

    fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelApplication {
        val model = select(call) ?: return TsUnknownCallModelApplication.NotApplicable
        if (state.isUnknownCallModelActive(model.id)) {
            return TsUnknownCallModelApplication.NotApplicable
        }

        val execution = model.apply(state, call) ?: return TsUnknownCallModelApplication.NotApplicable

        return TsUnknownCallModelApplication.Applied(
            modelId = model.id,
            execution = execution,
        )
    }
}

private fun validateUnambiguousTargets(models: List<TsUnknownCallModel>) {
    models.forEachIndexed { index, model ->
        val conflictingModel = models.drop(index + 1).firstOrNull { other ->
            model.target.overlaps(other.target)
        } ?: return@forEachIndexed

        error(
            "Ambiguous semantic model targets: " +
                listOf(model.id, conflictingModel.id).sorted().joinToString()
        )
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

private fun computeFingerprint(models: List<TsUnknownCallModel>): String {
    val digest = MessageDigest.getInstance("SHA-256")

    models.forEach { model ->
        digest.updateLengthPrefixed(model.id)
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
