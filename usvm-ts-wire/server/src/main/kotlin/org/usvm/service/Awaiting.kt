package org.usvm.service

import greeter.GreeterBlockingServer
import greeter.GreeterWireGrpc
import greeter.HelloReply
import greeter.HelloRequest
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

class AwaitingImpl : GreeterBlockingServer {
    override fun SayHello(request: HelloRequest): HelloReply {
        return HelloReply(message = "Hello, ${request.name}!")
    }
}

class AwaitingService : GreeterWireGrpc.GreeterImplBase() {
    private val impl = AwaitingImpl()

    val ready: Channel<Unit> = Channel(Channel.RENDEZVOUS)

    override fun SayHello(request: HelloRequest, response: StreamObserver<HelloReply>) {
        println("request = $request")
        if (request.name == "READY") {
            ready.trySend(Unit)
        }
        response.onNext(impl.SayHello(request))
        response.onCompleted()
    }
}

fun main() {
    val awaitingService = AwaitingService()
    val server = ServerBuilder
        .forPort(8080)
        .addService(awaitingService)
        .addService(@Suppress("DEPRECATION") ProtoReflectionService.newInstance())
        .build()
    server.start()
    println("Server listening on port ${server.port}")
    runBlocking {
        // Note: `npx ts-node start_and_notify.ts`
        println("Waiting for READY...")
        awaitingService.ready.receive()
        println("READY received")
    }
    server.awaitTermination()
}
