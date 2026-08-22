plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
}

dependencies {
    implementation(project(":usvm-ts"))
    implementation(Libs.jacodb_ets)
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(Libs.logback)
}
