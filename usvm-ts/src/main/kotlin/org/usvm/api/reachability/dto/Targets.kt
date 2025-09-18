package org.usvm.api.reachability.dto

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// The `targets.json` can contain the traces in the following formats:
//  - { "targets": [...] }, a single linear trace with a list of targets
//  - { "target": {...}, "children": [...] }, a single tree-like trace with a tree structure of targets
//  - [ {...} ], a list of traces (can contain both linear and tree traces)
//
// Note:
//  - A trace is a sequence of targets (points).
// Note:
//  - A target is a point, i.e. a statement in the code.
//  - A target can be of type: initial, intermediate (default), final.

@Serializable(with = TargetsContainerSerializer::class)
sealed interface TargetsContainerDto {

    @Serializable(with = SingleTraceSerializer::class)
    sealed interface SingleTrace : TargetsContainerDto

    // Format: { "targets": [...] }
    @Serializable
    data class LinearTrace(
        val targets: List<TargetDto>,
    ) : SingleTrace

    // Format: { "root": {...} }
    @Serializable
    data class TreeTrace(
        val root: TargetTreeNodeDto,
    ) : SingleTrace

    // Format: [ {...} ]
    @Serializable
    data class TraceList(
        val traces: List<SingleTrace>,
    ) : TargetsContainerDto
}

object SingleTraceSerializer :
    JsonContentPolymorphicSerializer<TargetsContainerDto.SingleTrace>(TargetsContainerDto.SingleTrace::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<TargetsContainerDto.SingleTrace> =
        when {
            // Object with "targets" field: { "targets": [...] }
            element is JsonObject && element.containsKey("targets") -> TargetsContainerDto.LinearTrace.serializer()

            // Object with "root" field: { "root": {...} }
            element is JsonObject && element.containsKey("root") -> TargetsContainerDto.TreeTrace.serializer()

            else -> error("Unknown single trace format")
        }

    fun deserializeSingleTrace(element: JsonElement): DeserializationStrategy<TargetsContainerDto> =
        selectDeserializer(element) as DeserializationStrategy<TargetsContainerDto>
}

object TargetsContainerSerializer :
    JsonContentPolymorphicSerializer<TargetsContainerDto>(TargetsContainerDto::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<TargetsContainerDto> = when {
        // Array at top level: [ {...} ]
        element is JsonArray -> TargetsContainerDto.TraceList.serializer()

        // Any object: delegate to SingleTraceSerializer
        else -> SingleTraceSerializer.deserializeSingleTrace(element)
    }
}

@Serializable
data class TargetTreeNodeDto(
    val target: TargetDto,
    val children: List<TargetTreeNodeDto> = emptyList(),
)

@Serializable
enum class TargetTypeDto {
    @SerialName("initial")
    INITIAL,

    @SerialName("intermediate")
    INTERMEDIATE,

    @SerialName("final")
    FINAL,
}

@Serializable
data class TargetDto(
    val type: TargetTypeDto = TargetTypeDto.INTERMEDIATE,
    val location: LocationDto, // TODO: consider inlining
)

@Serializable
data class LocationDto(
    val fileName: String,
    val className: String,
    val methodName: String,
    val stmtType: String? = null,
    val block: Int? = null,
    val index: Int? = null,
)
