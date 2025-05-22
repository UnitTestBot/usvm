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

package org.usvm.service

import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.protobuf.protobuf
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.accept
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mu.KotlinLogging
import org.jacodb.ets.dto.dtoModule

private val logger = KotlinLogging.logger {}

fun main() {
    embeddedServer(Netty, port = 9999) {
        setup()
    }.start(wait = true)
}

fun Application.setup() {
    install(ContentNegotiation) {
        json(Json {
            serializersModule = dtoModule
            encodeDefaults = true
        })
        protobuf(ProtoBuf {
            encodeDefaults = false
        })
    }

    routing {
        post("/hello") {
            val request = call.receive<HelloRequest>()
            logger.info { "Got ${call.request.contentType()}: $request" }
            val result = handleHello(request)
            logger.info { "Returning ${call.request.accept()}: $result" }
            call.respond(result)
        }
        post("/infer") {
            val request = call.receive<InferTypesRequestDto>()
            logger.info { "Got ${call.request.contentType()}" }
            val result = handleInfer(request)
            logger.info { "Returning ${call.request.accept()}" }
            call.respond(result)
        }
    }
}
