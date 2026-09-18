package org.usvm.machine.call

import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.usvm.machine.state.TsState
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

private const val BYTE_MASK = 0xff

/** An immutable deterministic set of semantic models used by one machine run. */
class TsUnknownCallModelCatalog(
    models: Collection<TsUnknownCallModel>,
    selection: TsUnknownCallModelSelection = TsUnknownCallModelSelection.All,
) {
    private val index: Map<String, Map<TsUnknownCallFailureReason, Map<String?, TsUnknownCallModel>>>

    val modelIds: List<String>
    val fingerprint: String
    val additionalSceneFiles: List<EtsFile>

    init {
        val modelsById = hashMapOf<String, TsUnknownCallModel>()
        models.forEach { model ->
            require(model.id.isNotBlank()) { "Semantic model ID must not be blank" }
            require(modelsById.put(model.id, model) == null) { "Duplicate semantic model ID: ${model.id}" }
        }

        val selectedModels = when (selection) {
            TsUnknownCallModelSelection.All -> modelsById.values
            is TsUnknownCallModelSelection.Only -> {
                val unknownIds = selection.ids.subtract(modelsById.keys)
                require(unknownIds.isEmpty()) { "Unknown semantic model IDs: ${unknownIds.sorted().joinToString()}" }
                selection.ids.map(modelsById::getValue)
            }
        }.sortedBy(TsUnknownCallModel::id)

        modelIds = Collections.unmodifiableList(selectedModels.map(TsUnknownCallModel::id))
        index = indexModels(selectedModels)
        fingerprint = computeFingerprint(modelIds)
        additionalSceneFiles = selectedModels
            .flatMap(TsUnknownCallModel::additionalSceneFiles)
            .deduplicateEtsFilesBySignature()
            .let(Collections::unmodifiableList)
    }

    internal fun select(call: TsUnknownCall): TsUnknownCallModel? {
        val candidates = index[call.callee.name]?.get(call.failureReason) ?: return null
        return candidates[call.callee.enclosingClass.name] ?: candidates[null]
    }

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

private fun indexModels(
    models: List<TsUnknownCallModel>,
): Map<String, Map<TsUnknownCallFailureReason, Map<String?, TsUnknownCallModel>>> {
    val index = hashMapOf<String, MutableMap<TsUnknownCallFailureReason, MutableMap<String?, TsUnknownCallModel>>>()
    models.forEach { model ->
        val target = model.target
        val methods = index.getOrPut(target.methodName) { hashMapOf() }
        val reasons = target.failureReason?.let(::listOf) ?: TsUnknownCallFailureReason.entries
        reasons.forEach { reason ->
            val classes = methods.getOrPut(reason) { hashMapOf() }
            val conflict = if (target.enclosingClassName == null) {
                classes.values.firstOrNull()
            } else {
                classes[target.enclosingClassName] ?: classes[null]
            }
            if (conflict != null) {
                error("Ambiguous semantic model targets: ${listOf(model.id, conflict.id).sorted().joinToString()}")
            }

            classes[target.enclosingClassName] = model
        }
    }
    return index
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

private fun computeFingerprint(modelIds: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    modelIds.forEach { digest.updateLengthPrefixed(it) }

    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and BYTE_MASK)
    }
}

/** Length prefixes distinguish ID sequences such as ["ab", "c"] and ["a", "bc"]. */
private fun MessageDigest.updateLengthPrefixed(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
    update(bytes)
}
