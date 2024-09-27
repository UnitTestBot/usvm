import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-util"))
    implementation(Libs.jacodb_api_common)
    implementation(Libs.jacodb_taint_configuration)
    implementation(Libs.sarif4k)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
