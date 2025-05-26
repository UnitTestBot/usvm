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

package org.usvm.ts.grpc

import io.grpc.protobuf.services.ProtoReflectionService
import mu.KotlinLogging
import org.jacodb.ets.grpc.GreeterService
import org.jacodb.ets.grpc.GrpcServer
import org.jacodb.ets.grpc.ManagerClient
import org.jacodb.ets.grpc.grpcChannel
import org.jacodb.ets.grpc.toEts
import org.usvm.dataflow.ts.infer.EntryPointsProcessor
import org.usvm.dataflow.ts.infer.TypeGuesser
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.util.EtsTraits
import usvm.InferTypesRequest
import usvm.InferredTypes
import usvm.UsvmGrpcKt
import usvm.sceneOrNull
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger {}

class UsvmService : UsvmGrpcKt.UsvmCoroutineImplBase() {
    override suspend fun inferTypes(
        request: InferTypesRequest,
    ): InferredTypes {
        if (request.hasPath()) {
            logger.info { "Received type inference request for '${request.path}'" }
        } else if (request.hasScene()) {
            logger.info { "Received type inference request for scene" }
        } else {
            logger.info { "Received type inference request WITHOUT path or scene" }
        }
        val startTime = System.currentTimeMillis()

        val sceneProto = request.sceneOrNull ?: run {
            logger.info { "Scene is null, requesting scene from ArkAnalyzer" }
            val path = checkNotNull(request.path)
            val port = 9999 // local AA
            val channel = grpcChannel(port = port) {
                maxInboundMessageSize(64 * 1024 * 1024) // 64 MiB
            }
            val manager = ManagerClient(channel)
            logger.info { "call GetScene(path = \"$path\")" }
            manager.getScene(path)
        }
        val scene = sceneProto.toEts()
        val graph = createApplicationGraph(scene)
        val guesser = TypeGuesser(scene)

        val (dummyMains, allMethods) = EntryPointsProcessor(scene).extractEntryPoints()
        val publicMethods = allMethods.filter { m -> m.isPublic }

        val manager = TypeInferenceManager(EtsTraits(), graph)

        val useKnownTypes = true
        val enableAliasAnalysis = true

        val (resultBasic, timeAnalyze) = measureTimedValue {
            manager.analyze(
                entrypoints = dummyMains,
                allMethods = publicMethods,
                doAddKnownTypes = useKnownTypes,
                doAliasAnalysis = enableAliasAnalysis,
            )
        }
        logger.info { "Inferred types for ${resultBasic.inferredTypes.size} methods in $timeAnalyze" }

        val (result, timeGuess) = measureTimedValue {
            resultBasic.withGuessedTypes(guesser)
        }
        logger.info { "Guessed types for ${result.inferredTypes.size} methods in $timeGuess" }
        logger.info { "Done type inference in %.1f s".format((System.currentTimeMillis() - startTime) / 1000.0) }

        logger.info { "Converting to proto..." }
        val resultProto = result.toProto()
        logger.info { "All done in %.1f s".format((System.currentTimeMillis() - startTime) / 1000.0) }
        return resultProto
    }
}

const val DEFAULT_PORT = 7777

fun usvmServer(port: Int = DEFAULT_PORT): GrpcServer {
    return GrpcServer(port) {
        maxInboundMessageSize(64 * 1024 * 1024)
        addService(GreeterService())
        addService(UsvmService())
        addService(@Suppress("DEPRECATION") ProtoReflectionService.newInstance())
    }
}

fun main() {
    val server = usvmServer()
    server.start()
    server.blockUntilShutdown()
}
