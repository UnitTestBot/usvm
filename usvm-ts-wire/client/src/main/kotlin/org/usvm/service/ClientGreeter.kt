package org.usvm.service

import greeter2.GreeterClient
import greeter2.HelloRequest

fun main() {
    val greeter: GreeterClient = grpcClient(50051).create()
    val response = greeter.SayHello().executeBlocking(HelloRequest(name = "Kotlin"))
    println("response = \"${response.message}\"")
}
