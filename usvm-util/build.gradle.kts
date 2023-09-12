import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(group = "io.github.microutils", name = "kotlin-logging", version = Versions.klogging)
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")
}

tasks {
   withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
        }
    }
}