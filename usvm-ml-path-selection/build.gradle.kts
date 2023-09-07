object MLVersions {
    const val serialization = "1.5.1"
    const val onnxruntime = "1.15.1"
    const val dotlin = "1.0.2"
}

plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version "1.8.21"
}

dependencies {
    implementation(project(":usvm-jvm"))
    implementation(project(":usvm-core"))

    implementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")
    implementation("ch.qos.logback:logback-classic:${Versions.logback}")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${MLVersions.serialization}")
    implementation("io.github.rchowell:dotlin:${MLVersions.dotlin}")
    implementation("com.microsoft.onnxruntime:onnxruntime:${MLVersions.onnxruntime}")

    testImplementation(project(":usvm-jvm"))
    testImplementation(project(":usvm-core"))
}
