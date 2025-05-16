package org.usvm.service

import io.grpc.protobuf.services.ProtoReflectionService
import org.jacodb.ets.grpc.GrpcServer
import usvm.UsvmServiceGrpcKt
import usvm.infer.TypeInferenceRequest
import usvm.infer.TypeInferenceResult
import usvm.infer.typeInferenceResult

class UsvmService : UsvmServiceGrpcKt.UsvmServiceCoroutineImplBase() {
    override suspend fun inferTypes(
        request: TypeInferenceRequest,
    ): TypeInferenceResult {
        println("Received $request")
        return typeInferenceResult {
            this.payload = "done!"
        }
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
