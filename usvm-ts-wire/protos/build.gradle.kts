plugins {
    id("usvm.kotlin-conventions")
    id("com.squareup.wire") version "5.3.1"
}

dependencies {
    runtimeOnly("com.squareup.wire:wire-runtime:5.3.1")
}

wire {
    protoLibrary = true
    kotlin {
        rpcRole = "none"
    }
}
