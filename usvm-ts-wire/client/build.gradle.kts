plugins {
    id("usvm.kotlin-conventions")
    id("com.squareup.wire") version "5.3.1"
}

dependencies {
    protoSource(project(":usvm-ts-wire:protos"))
    runtimeOnly("com.squareup.wire:wire-runtime:5.3.1")
    api("com.squareup.wire:wire-grpc-client:5.3.1")
}

wire {
    kotlin {
        rpcRole = "client"
    }
}
