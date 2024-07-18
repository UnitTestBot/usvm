import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    api(project(":usvm-dataflow"))

    api(Libs.jacodb_api_common)
    api(Libs.jacodb_ets)
    implementation(Libs.jacodb_taint_configuration)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
        allWarningsAsErrors = false
    }
}
