import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.gradle.application")
    id("usvm.kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            allWarningsAsErrors = false
        }
    }
}

dependencies {
    implementation(project(":usvm-core"))

    implementation(Libs.jacodb_go)
    implementation(Libs.kotlinx_serialization_core)
    implementation(Libs.kotlinx_serialization_json)
    implementation(Libs.kotlinx_collections)
    implementation(Libs.ksmt_yices)
    implementation(Libs.slf4j_simple)

    testImplementation(Libs.logback)
}