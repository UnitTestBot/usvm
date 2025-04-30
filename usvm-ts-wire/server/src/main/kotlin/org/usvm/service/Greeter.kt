package org.usvm.service

import greeter.GreeterBlockingServer
import greeter.GreeterWireGrpc
import greeter.HelloReply
import greeter.HelloRequest
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.stub.StreamObserver

class GreeterImpl : GreeterBlockingServer {
    override fun SayHello(request: HelloRequest): HelloReply {
        return HelloReply(message = "Hello, ${request.name}!")
    }
}

class GreeterService : GreeterWireGrpc.GreeterImplBase() {
    private val impl = GreeterImpl()

    override fun SayHello(request: HelloRequest, response: StreamObserver<HelloReply>) {
        println("request = $request")
        response.onNext(impl.SayHello(request))
        response.onCompleted()
    }
}

fun main() {
    val server = ServerBuilder
        .forPort(8080)
        .addService(AwaitingService())
        .addService(@Suppress("DEPRECATION") ProtoReflectionService.newInstance())
        .build()
    server.start()
    println("Server listening on port ${server.port}")
    server.awaitTermination()
}
