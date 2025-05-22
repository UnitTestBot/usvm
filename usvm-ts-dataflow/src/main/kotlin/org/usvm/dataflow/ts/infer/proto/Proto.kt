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

package org.usvm.dataflow.ts.infer.proto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.jacodb.ets.proto.ProtoClassSignature
import org.jacodb.ets.proto.ProtoMethodSignature
import org.jacodb.ets.proto.ProtoType

@Serializable
@SerialName("InferredTypes")
data class ProtoInferredTypes(
    @ProtoNumber(1) val classes: List<ProtoClassTypeResult>,
    @ProtoNumber(2) val methods: List<ProtoMethodTypeResult>,
)

@Serializable
@SerialName("ClassTypeResult")
data class ProtoClassTypeResult(
    @ProtoNumber(1) val signature: ProtoClassSignature,
    @ProtoNumber(2) val fields: List<ProtoFieldTypeResult>,
    @ProtoNumber(3) val methods: List<String>,
)

@Serializable
@SerialName("FieldTypeResult")
data class ProtoFieldTypeResult(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val type: ProtoType,
)

@Serializable
@SerialName("MethodTypeResult")
data class ProtoMethodTypeResult(
    @ProtoNumber(1) val signature: ProtoMethodSignature,
    @ProtoNumber(2) val args: List<ProtoArgumentTypeResult>,
    @ProtoNumber(3) val returnType: ProtoType? = null,
    @ProtoNumber(4) val locals: List<ProtoLocalTypeResult>,
)

@Serializable
@SerialName("ArgumentTypeResult")
data class ProtoArgumentTypeResult(
    @ProtoNumber(1) val index: Int,
    @ProtoNumber(2) val type: ProtoType,
)

@Serializable
@SerialName("LocalTypeResult")
data class ProtoLocalTypeResult(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val type: ProtoType,
)
