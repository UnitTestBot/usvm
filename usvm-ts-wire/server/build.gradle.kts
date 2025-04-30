plugins {
    id("usvm.kotlin-conventions")
    id("com.squareup.wire") version "5.3.1"
}

buildscript {
    dependencies {
        classpath("com.squareup.wiregrpcserver:server-generator:1.0.0-alpha04")
    }
}

dependencies {
    protoSource(project(":usvm-ts-wire:protos"))
    runtimeOnly("com.squareup.wire:wire-runtime:5.3.1")
    api("com.squareup.wiregrpcserver:server:1.0.0-alpha04")
    api("io.grpc:grpc-services:1.72.0")
    implementation("io.grpc:grpc-protobuf:1.72.0")
    implementation("io.grpc:grpc-netty:1.72.0")
}

wire {
    custom {
        schemaHandlerFactory = com.squareup.wire.kotlin.grpcserver.GrpcServerSchemaHandler.Factory()
        options = mapOf(
            "rpcCallStyle" to "blocking",
            "singleMethodServices" to "false",
        )
        exclusive = false
    }
    kotlin {
        rpcRole = "server"
        rpcCallStyle = "blocking"
        singleMethodServices = false
    }
}
