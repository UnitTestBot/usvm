package org.usvm.dataflow.ts.infer.dto

import kotlinx.serialization.Serializable
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.MethodSignatureDto
import org.jacodb.ets.dto.TypeDto
import org.usvm.dataflow.ts.infer.*

@Serializable
data class TypeInferenceResultDto(
    val classes: List<ClassTypeResultDto>,
    val methods: List<MethodTypeResultDto>
)

@Serializable
data class ClassTypeResultDto(
    val signature: ClassSignatureDto,
    val fields: List<FieldTypeResultDto>,
    val methods: List<String>
)

@Serializable
data class FieldTypeResultDto(
    val name: String,
    val type: TypeDto
)

@Serializable
data class MethodTypeResultDto(
    val signature: MethodSignatureDto,
    val parameters: List<ParameterTypeResultDto>,
    val locals: List<LocalTypeResultDto>,
    val returnType: TypeDto? = null
)

@Serializable
data class ParameterTypeResultDto(
    val index: Int,
    val type: TypeDto
)

@Serializable
data class LocalTypeResultDto(
    val name: String,
    val type: TypeDto
)

fun TypeInferenceResult.toDto(): TypeInferenceResultDto {
    val classTypeInferenceResult = inferredCombinedThisType.map { (signature, fact) ->
        val properties = (fact as? EtsTypeFact.ObjectEtsTypeFact)?.properties ?: emptyMap()

        val methods = properties
            .filter { it.value is EtsTypeFact.FunctionEtsTypeFact }
            .keys
            .toList()

        val fields = properties
            .filterNot { it.value is EtsTypeFact.FunctionEtsTypeFact }
            .mapNotNull { (name, fact) -> fact.getType()?.let {
                FieldTypeResultDto(name, it.toDto())
            } }

        ClassTypeResultDto(signature.toDto(), fields, methods)
    }

    val methodTypeInferenceResult = inferredTypes.map { (method, facts) ->
        val parameters = facts
            .mapNotNull { (base, fact) ->
                val type = fact.getType()
                if (base is AccessPathBase.Arg && type != null)
                    ParameterTypeResultDto(base.index, type.toDto())
                else null
            }

        val locals = facts
            .mapNotNull { (base, fact) ->
                val type = fact.getType()
                if (base is AccessPathBase.Local && type != null)
                    LocalTypeResultDto(base.name, type.toDto())
                else null
            }

        val returnType = inferredReturnType[method]?.getType()?.toDto()

        MethodTypeResultDto(method.signature.toDto(), parameters, locals, returnType)
    }

    return TypeInferenceResultDto(classTypeInferenceResult, methodTypeInferenceResult)
}
