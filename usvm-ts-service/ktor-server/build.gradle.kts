plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
    id(Plugins.Ktor)
}

dependencies {
    implementation(project(":usvm-ts"))
    implementation(project(":usvm-ts-dataflow"))

    implementation(Libs.jacodb_ets)
    implementation(Libs.ktor_server_netty)
    implementation(Libs.ktor_server_content_negotiation)
    implementation(Libs.ktor_serialization_kotlinx_json)
    implementation(Libs.ktor_serialization_kotlinx_protobuf)

    // TODO: remove logback dep
    implementation(Libs.logback)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)
}

application {
    mainClass.set("org.usvm.ts.service.ServerokKt")
}

kotlin {
    jvmToolchain(11)
}
