package org.usvm.service

import greeter.HelloReply
import greeter.HelloRequest
import greeter.helloReply
import io.grpc.ManagedChannelBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import mu.KotlinLogging
import org.jacodb.ets.grpc.GrpcServer
import org.jacodb.ets.grpc.ManagerClient
import org.jacodb.ets.grpc.ProtoToEtsConverter
import org.usvm.dataflow.ts.infer.EntryPointsProcessor
import org.usvm.dataflow.ts.infer.TypeGuesser
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.util.EtsTraits
import usvm.UsvmServiceGrpcKt
import usvm.infer.InferTypesRequest
import usvm.infer.InferredTypes
import usvm.infer.sceneOrNull
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger {}

class UsvmService : UsvmServiceGrpcKt.UsvmServiceCoroutineImplBase() {
    override suspend fun sayHello(request: HelloRequest): HelloReply {
        logger.info { "Received $request" }
        val response = helloReply {
            this.message = "Hello, ${request.name}"
        }
        logger.info { "Sending $response" }
        return response
    }

    override suspend fun inferTypes(
        request: InferTypesRequest,
    ): InferredTypes {
        logger.info { "Received $request" }
        val startTime = System.currentTimeMillis()

        val sceneProto = request.sceneOrNull ?: run {
            logger.info { "Scene is null, requesting scene from ArkAnalyzer" }
            val path = checkNotNull(request.path)
            val port = 50051
            val channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .maxInboundMessageSize(64 * 1024 * 1024) // 64 MiB
                .usePlaintext()
                .build()
            val manager = ManagerClient(channel)
            logger.info { "call GetScene(path = \"$path\")" }
            manager.getScene(path)
        }
        val scene = ProtoToEtsConverter().convert(sceneProto)
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

fun usvmServer(port: Int): GrpcServer {
    return GrpcServer(port) {
        addService(UsvmService())
        addService(@Suppress("DEPRECATION") ProtoReflectionService.newInstance())
    }
}

fun main() {
    val port = 7777
    val server = usvmServer(port)
    server.start()
    server.blockUntilShutdown()
}
