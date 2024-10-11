package org.usvm.dataflow.ts.infer.dto

import kotlinx.serialization.Serializable
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.MethodSignatureDto
import org.jacodb.ets.dto.TypeDto
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceResult

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

fun TypeInferenceResult.toDto(): InferredTypesDto {
    val classTypeInferenceResult = inferredCombinedThisType.map { (signature, fact) ->
        val properties = (fact as? EtsTypeFact.ObjectEtsTypeFact)?.properties ?: emptyMap()
        val methods = properties.filter { it.value is EtsTypeFact.FunctionEtsTypeFact }.keys.toList()
        val fields = properties
            .filterNot { it.value is EtsTypeFact.FunctionEtsTypeFact }
            .mapNotNull { (name, fact) ->
                fact.toType()?.let {
                    FieldTypeResultDto(name, it.toDto())
                }
            }

        ClassTypeResultDto(signature.toDto(), fields, methods)
    }

    val methodTypeInferenceResult = inferredTypes.map { (method, facts) ->
        val args = facts.mapNotNull { (base, fact) ->
            if (base is AccessPathBase.Arg) {
                val type = fact.toType()
                if (type != null) {
                    return@mapNotNull ArgumentTypeResultDto(base.index, type.toDto())
                }
            }
            null
        }
        val locals = facts.mapNotNull { (base, fact) ->
            if (base is AccessPathBase.Local) {
                val type = fact.toType()
                if (type != null) {
                    return@mapNotNull LocalTypeResultDto(base.name, type.toDto())
                }
            }
            null
        }
        val returnType = inferredReturnType[method]?.toType()?.toDto()

        MethodTypeResultDto(method.signature.toDto(), args, returnType, locals)
    }

    return InferredTypesDto(classTypeInferenceResult, methodTypeInferenceResult)
}
