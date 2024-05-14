import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlinVersion
    application
}

application {
    mainClass.set("org.usvm.MainKt")
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}
tasks.withType<KotlinCompile> {
    kotlinOptions.allWarningsAsErrors = false
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation("io.ktor:ktor-server-core:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-websockets:${Versions.ktor_version}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor_version}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
    implementation("org.slf4j:slf4j-simple:${Versions.samplesSl4j}")

    testImplementation(kotlin("test"))
}