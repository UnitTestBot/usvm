import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation("${Versions.jacodbPackage}:jacodb-api-common:${Versions.jacodb}")
    implementation("${Versions.jacodbPackage}:jacodb-taint-configuration:${Versions.jacodb}")

    api("io.github.detekt.sarif4k", "sarif4k", Versions.sarif4k)

    api("io.github.microutils:kotlin-logging:${Versions.klogging}")
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
