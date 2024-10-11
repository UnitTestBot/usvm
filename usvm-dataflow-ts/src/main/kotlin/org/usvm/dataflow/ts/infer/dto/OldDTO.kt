/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalSerializationApi::class)

package org.usvm.dataflow.ts.infer.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.ClassTypeDto
import org.jacodb.ets.dto.TypeDto

@Serializable
data class TypeInferenceResultDto(
    val inferredTypes: Map<MethodId, Map<AccessPathBaseDto, TypeFactDto>>,
    val inferredReturnType: Map<MethodId, TypeFactDto>,
    val inferredCombinedThisType: Map<ClassId, TypeFactDto>,
)

@Serializable
data class MethodId(
    // TODO: classId?
    val className: String,
    val methodName: String,
)

@Serializable
data class ClassId(
    val className: String,
    val fileName: String,
    // TODO: namespace?
)

@Serializable
@JsonClassDiscriminator("_")
sealed interface AccessPathBaseDto {
    @Serializable
    @SerialName("This")
    object This : AccessPathBaseDto {
        override fun toString(): String = "<this>"
    }

    @Serializable
    @SerialName("Static")
    data object Static : AccessPathBaseDto {
        override fun toString(): String = "<static>"
    }

    @Serializable
    @SerialName("Arg")
    data class Arg(val index: Int) : AccessPathBaseDto {
        override fun toString(): String = "arg($index)"
    }

    @Serializable
    @SerialName("Local")
    data class Local(val name: String) : AccessPathBaseDto {
        override fun toString(): String = "local($name)"
    }

    @Serializable
    @SerialName("Const")
    data class Const(val value: String) : AccessPathBaseDto {
        override fun toString(): String = "const($value)"
    }
}

@Serializable
@JsonClassDiscriminator("_")
sealed interface TypeFactDto {
    sealed interface Basic : TypeFactDto

    @Serializable
    @SerialName("AnyType")
    object AnyTypeFact : Basic {
        override fun toString(): String = "any"
    }

    @Serializable
    @SerialName("UnknownType")
    object UnknownTypeFact : Basic {
        override fun toString(): String = "unknown"
    }

    @Serializable
    @SerialName("NullType")
    object NullTypeFact : Basic {
        override fun toString(): String = "null"
    }

    @Serializable
    @SerialName("UndefinedType")
    object UndefinedTypeFact : Basic {
        override fun toString(): String = "undefined"
    }

    @Serializable
    @SerialName("BooleanType")
    object BooleanTypeFact : Basic {
        override fun toString(): String = "boolean"
    }

    @Serializable
    @SerialName("NumberType")
    object NumberTypeFact : Basic {
        override fun toString(): String = "number"
    }

    @Serializable
    @SerialName("StringType")
    object StringTypeFact : Basic {
        override fun toString(): String = "string"
    }

    @Serializable
    @SerialName("SymbolType")
    object SymbolTypeFact : Basic {
        override fun toString(): String = "symbol"
    }

    @Serializable
    @SerialName("FunctionType")
    object FunctionTypeFact : Basic {
        override fun toString(): String = "function"
    }

    @Serializable
    @SerialName("ArrayType")
    data class ArrayTypeFact(val elementType: TypeFactDto) : Basic {
        override fun toString(): String = "Array<$elementType>"
    }

    // TODO: tuple?

    @Serializable
    @SerialName("ObjectType")
    data class ObjectTypeFact(
        val cls: TypeDto? = null,
        val properties: Map<String, TypeFactDto> = emptyMap(),
    ) : Basic {
        override fun toString(): String = buildString {
            // "{}"                         when cls==null, properties.isEmpty()
            // "{ x: number, y: string }"   when cls!=null, properties.isNotEmpty()
            // "T {}"                       when cls!=null, properties.isEmpty()
            // "T { x: number, y: string }" when cls!=null, properties.isNotEmpty()
            if (cls != null) {
                append(cls)
                append(" ")
            }
            val inner = properties.entries.joinToString(", ") { (k, v) -> "$k: $v" }
            if (inner.isNotEmpty()) {
                append("{ ")
                append(inner)
                append(" }")
            } else {
                append("{}")
            }
        }
    }

    sealed interface Complex : TypeFactDto

    @Serializable
    @SerialName("UnionType")
    data class UnionTypeFact(val types: List<TypeFactDto>) : Complex {
        override fun toString(): String = types.joinToString(" | ")
    }

    @Serializable
    @SerialName("IntersectionType")
    data class IntersectionTypeFact(val types: List<TypeFactDto>) : Complex {
        override fun toString(): String = types.joinToString(" & ")
    }
}

fun main() {
    val json = Json {
        prettyPrint = true
    }

    val o1: TypeFactDto = TypeFactDto.ObjectTypeFact(
        ClassTypeDto(ClassSignatureDto("T")), mapOf(
            "x" to TypeFactDto.NumberTypeFact,
            "y" to TypeFactDto.StringTypeFact,
        )
    )
    val s = json.encodeToString(o1)
    println(s)
    val o2: TypeFactDto = Json.decodeFromString(s)
    println(o2)
    check(o1 == o2)
}
