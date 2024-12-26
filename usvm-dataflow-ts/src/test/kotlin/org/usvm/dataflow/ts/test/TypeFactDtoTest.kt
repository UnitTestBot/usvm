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

@file:Suppress("TestFunctionName", "RemoveRedundantBackticks")

package org.usvm.dataflow.ts.test

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.ClassTypeDto
import org.jacodb.ets.dto.FileSignatureDto
import org.usvm.dataflow.ts.infer.dto.TypeFactDto
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeFactDtoTest {
    @Test
    fun `NumberTypeFact`() {
        val fact = TypeFactDto.NumberTypeFact
        println(fact)

        val serialized = Json.encodeToString(fact)
        println(serialized)
        assertEquals("{}", serialized)

        val deserialized = Json.decodeFromString<TypeFactDto.NumberTypeFact>(serialized)
        println(deserialized)
        assertEquals(fact, deserialized)
    }

    @Test
    fun `polymorphic NumberTypeFact`() {
        val fact: TypeFactDto = TypeFactDto.NumberTypeFact
        println(fact)

        val serialized = Json.encodeToString(fact)
        println(serialized)
        assertEquals("""{"_":"NumberType"}""", serialized)

        val deserialized: TypeFactDto = Json.decodeFromString(serialized)
        println(deserialized)
        assertEquals(fact, deserialized)
    }

    @Test
    fun `polymorphic ObjectTypeFact with null class`() {
        val fact: TypeFactDto = TypeFactDto.ObjectTypeFact(
            cls = null,
        )
        println(fact)

        val serialized = Json.encodeToString(fact)
        println(serialized)
        assertEquals("""{"_":"ObjectType"}""", serialized)

        val deserialized: TypeFactDto = Json.decodeFromString(serialized)
        println(deserialized)
        assertEquals(fact, deserialized)
    }

    @Test
    fun `polymorphic ObjectTypeFact with null class and some properties`() {
        val fact: TypeFactDto = TypeFactDto.ObjectTypeFact(
            cls = null,
            properties = mapOf(
                "x" to TypeFactDto.NumberTypeFact,
                "bar" to TypeFactDto.ObjectTypeFact(
                    cls = mkClassType("org.example.Bar"),
                ),
            ),
        )
        println(fact)

        val serialized = Json.encodeToString(fact)
        println(serialized)
        assertEquals(
            """{"_":"ObjectType","properties":{"x":{"_":"NumberType"},"bar":{"_":"ObjectType","cls":{"_":"ClassType","signature":{"name":"org.example.Bar"}}}}}""",
            serialized
        )

        val deserialized: TypeFactDto = Json.decodeFromString(serialized)
        println(deserialized)
        assertEquals(fact, deserialized)
    }

    @Test
    fun `polymorphic ObjectTypeFact with class`() {
        val fact: TypeFactDto = TypeFactDto.ObjectTypeFact(
            cls = mkClassType("org.example.Foo"),
        )
        println(fact)

        val serialized = Json.encodeToString(fact)
        println(serialized)
        assertEquals(
            """{"_":"ObjectType","cls":{"_":"ClassType","signature":{"name":"org.example.Foo"}}}""",
            serialized
        )

        val deserialized: TypeFactDto = Json.decodeFromString(serialized)
        println(deserialized)
        assertEquals(fact, deserialized)
    }

    @Test
    fun `polymorphic ObjectTypeFact with class and some properties`() {
        val fact: TypeFactDto = TypeFactDto.ObjectTypeFact(
            cls = mkClassType("org.example.Foo"),
            properties = mapOf(
                "x" to TypeFactDto.NumberTypeFact,
                "bar" to TypeFactDto.ObjectTypeFact(
                    cls = mkClassType("org.example.Bar"),
                ),
            ),
        )
        println(fact)

        val serialized = Json.encodeToString(fact)
        println(serialized)
        assertEquals(
            """{"_":"ObjectType","cls":{"_":"ClassType","signature":{"name":"org.example.Foo"}},"properties":{"x":{"_":"NumberType"},"bar":{"_":"ObjectType","cls":{"_":"ClassType","signature":{"name":"org.example.Bar"}}}}}""",
            serialized
        )

        val deserialized: TypeFactDto = Json.decodeFromString(serialized)
        println(deserialized)
        assertEquals(fact, deserialized)
    }
}

// TODO: empty file signature
private fun mkClassType(className: String) = ClassTypeDto(ClassSignatureDto(className, FileSignatureDto("", "")))
