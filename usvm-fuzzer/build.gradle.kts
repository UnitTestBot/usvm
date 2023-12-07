import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.sourceSets
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
    id("com.jetbrains.rdgen") version Versions.rd
    application
    java
}

dependencies {
    implementation(project(":usvm-jvm-instrumentation"))
    implementation("org.jacodb:jacodb-core:${Versions.jcdb}")
    implementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            allWarningsAsErrors = false
        }
    }
}
