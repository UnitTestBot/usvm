@file:OptIn(ExperimentalSerializationApi::class)

package org.usvm.service

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf

@Serializable
sealed interface Message

@Serializable
@SerialName("A")
data class A(
    val x: Int,
) : Message

@Serializable
@SerialName("B")
data class B(
    val x: Double,
) : Message

fun main() {
    run {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        val s = """{ "x": 42, "y": 100 }"""
        val x: A = json.decodeFromString(s)
        println("x = $x")
    }

    run {
        val protobuf = ProtoBuf { encodeDefaults = false }
        val json = Json { prettyPrint = true }

        val x: Message = B(42.1)
        println("x = $x")
        val xs = protobuf.encodeToHexString(x)
        println("x.proto = $xs")
        val x2: Message = protobuf.decodeFromHexString(xs)
        println("x2 = $x2")
        println("x.json = ${json.encodeToString(x)}")

        val y = A(100)
        println("y = $y")
        val ys = protobuf.encodeToHexString(y)
        println("y.proto = $ys")
        val y2: A = protobuf.decodeFromHexString(ys)
        println("y2 = $y2")
        println("y.json = ${json.encodeToString(y)}")
    }
}
