plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
}

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(":usvm-ts"))

    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_ets)

    implementation(Libs.kotlinx_serialization_json)

    implementation(Libs.ksmt_yices)

    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)
}

// Reuse the usvm-ts test samples (read-only) for differential and end-to-end tests.
sourceSets {
    test {
        resources {
            srcDir(rootDir.resolve("usvm-ts").resolve("src").resolve("test").resolve("resources"))
        }
    }
}

// CLI for batch experiments: ./gradlew :usvm-ts-pbt:runHybrid --args="file.ts --mode HYBRID"
val runHybrid by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Runs the hybrid PBT + symbolic analyzer CLI."
    mainClass.set("org.usvm.ts.pbt.report.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
    // Propagate the ArkAnalyzer location for .ts -> EtsIR conversion
    System.getenv("ARKANALYZER_DIR")?.let { environment("ARKANALYZER_DIR", it) }
}
