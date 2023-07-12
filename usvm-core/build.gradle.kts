plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version "1.8.21"
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":usvm-util"))

    api("io.ksmt:ksmt-core:${Versions.ksmt}")
    api("io.ksmt:ksmt-z3:${Versions.ksmt}")

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
}
