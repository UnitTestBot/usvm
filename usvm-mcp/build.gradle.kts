import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
    application
    id(Plugins.Shadow)
}

dependencies {
    implementation(project(":usvm-ts"))
    implementation(project(":usvm-core"))
    implementation(project(":usvm-util"))

    implementation(Libs.jacodb_ets)
    implementation(Libs.mcp_kotlin_sdk)
    implementation(Libs.kotlinx_serialization_json)

    runtimeOnly(Libs.logback)
}

// The MCP Kotlin SDK (and its Ktor/kotlinx-io dependencies) requires JVM 11,
// while the shared conventions target JVM 1.8. This is a leaf application
// module, so it is safe to raise the target here.
tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
}
tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

application {
    mainClass = "org.usvm.mcp.MainKt"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8")
}

tasks.startScripts {
    applicationName = "usvm-mcp"
}

// Forward stdin to the process so that `./gradlew :usvm-mcp:run` can be used
// as an MCP stdio server directly (e.g., for local debugging).
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
