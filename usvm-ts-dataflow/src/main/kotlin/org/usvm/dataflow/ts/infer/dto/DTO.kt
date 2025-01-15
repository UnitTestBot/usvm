package org.usvm.dataflow.ts.infer.dto

import kotlinx.serialization.Serializable
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.MethodSignatureDto
import org.jacodb.ets.dto.TypeDto

@Serializable
data class InferredTypesDto(
    val classes: List<ClassTypeResultDto>,
    val methods: List<MethodTypeResultDto>,
)

@Serializable
data class ClassTypeResultDto(
    val signature: ClassSignatureDto,
    val fields: List<FieldTypeResultDto>,
    val methods: List<String>,
)

@Serializable
data class FieldTypeResultDto(
    val name: String,
    val type: TypeDto,
)

@Serializable
data class MethodTypeResultDto(
    val signature: MethodSignatureDto,
    val args: List<ArgumentTypeResultDto>,
    val returnType: TypeDto? = null,
    val locals: List<LocalTypeResultDto>,
)

@Serializable
data class ArgumentTypeResultDto(
    val index: Int,
    val type: TypeDto,
)

@Serializable
data class LocalTypeResultDto(
    val name: String,
    val type: TypeDto,
)
