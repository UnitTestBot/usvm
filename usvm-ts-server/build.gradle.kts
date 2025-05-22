plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
    id("io.ktor.plugin") version "3.1.3"
}

dependencies {
    implementation(project(":usvm-ts"))
    implementation(project(":usvm-ts-dataflow"))

    implementation(Libs.jacodb_ets)

    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-serialization-kotlinx-protobuf")

    // TODO: remove logback dep
    implementation(Libs.logback)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)
}

application {
    mainClass.set("org.usvm.service.ServerokKt")
}
