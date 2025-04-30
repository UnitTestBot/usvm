plugins {
    id("usvm.kotlin-conventions")
    id("com.squareup.wire") version "5.3.1"
    application
}

application {
    mainClass.set("org.usvm.service.ServerKt")
}

buildscript {
    dependencies {
        classpath("com.squareup.wiregrpcserver:server-generator:1.0.0-alpha04")
    }
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
