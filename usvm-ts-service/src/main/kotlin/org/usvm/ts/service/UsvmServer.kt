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

package org.usvm.ts.service

import io.grpc.Server
import io.grpc.stub.StreamObserver
import manager.GetSceneRequest
import manager.ManagerClient
import mu.KotlinLogging
import org.jacodb.ets.proto.toEts
import org.jacodb.ets.service.GreeterService
import org.jacodb.ets.service.createGrpcClient
import org.jacodb.ets.service.grpcServer
import org.usvm.dataflow.ts.infer.EntryPointsProcessor
import org.usvm.dataflow.ts.infer.TypeGuesser
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.util.EtsTraits
import usvm.InferTypesRequest
import usvm.InferredTypes
import usvm.UsvmBlockingServer
import usvm.UsvmWireGrpc
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger {}

class UsvmImpl : UsvmBlockingServer {
    override fun InferTypes(
        request: InferTypesRequest,
    ): InferredTypes {
        if (request.path != null) {
            logger.info { "Received type inference request for '${request.path}'" }
        } else if (request.scene != null) {
            logger.info { "Received type inference request for Scene" }
        } else {
            logger.info { "Received type inference request WITHOUT path or scene" }
        }
        val startTime = System.currentTimeMillis()

        val sceneProto = request.scene ?: run {
            logger.info { "Scene is null, requesting scene from ArkAnalyzer" }
            val path = checkNotNull(request.path)
            logger.info { "call GetScene(path = \"$path\")" }
            // TODO: make AA port configurable
            val port = 9999 // local AA
            val manager = createGrpcClient<ManagerClient>(port)
            val request = GetSceneRequest(path = path)
            manager.GetScene().executeBlocking(request)
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

class UsvmService : UsvmWireGrpc.UsvmImplBase() {
    private val impl = UsvmImpl()

    override fun InferTypes(
        request: InferTypesRequest,
        response: StreamObserver<InferredTypes>,
    ) {
        response.onNext(impl.InferTypes(request))
        response.onCompleted()
    }
}

const val DEFAULT_PORT = 7777

fun usvmServer(port: Int = DEFAULT_PORT): Server {
    return grpcServer(port) {
        maxInboundMessageSize(64 * 1024 * 1024)
        addService(GreeterService())
        addService(UsvmService())
    }
}

/**
 * Example usage:
 *
 * 1. Start ArkAnalyzer server on port 9999:
 * ```
 * cd arkanalyzer
 * ARKANALYZER_PORT=9999 npm run server
 * ```
 *
 * 2. Run this USVM server via IDEA.
 *
 * 3. Send a request to the USVM server via `grpc_cli`:
 * ```
 * grpc_cli call --max_recv_msg_size -1 localhost:7777 InferTypes "path: \"$(realpath ~/dev/jacodb/jacodb-ets/src/test/resources/repos/applications_app_samples/code/SuperFeature/DistributedAppDev/ArkTSDistributedCalc)\""
 * ```
 */
fun main() {
    val server = usvmServer()
    server.start()
    logger.info { "USVM server listening on port ${server.port}" }
    server.awaitTermination()
    logger.info { "USVM server stopped" }
}
